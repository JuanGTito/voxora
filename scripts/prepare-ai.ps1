param([string]$Destination = (Join-Path (Split-Path -Parent $PSScriptRoot) '.local-runtime'), [switch]$Offline)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $root 'src\main\resources\ai\components.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding utf8 | ConvertFrom-Json
$Destination = [IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
foreach ($component in $manifest.components) {
    $file = Join-Path $Destination $component.file
    New-Item -ItemType Directory -Path (Split-Path -Parent $file) -Force | Out-Null
    $valid = (Test-Path -LiteralPath $file) -and ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -eq $component.sha256)
    if (-not $valid) {
        if ($Offline) { throw "Componente ausente o SHA-256 incorrecto: $($component.id). Ejecuta sin -Offline para descargarlo." }
        Write-Output "Descargando $($component.id): $($component.size) bytes"
        $partial = "$file.part"
        & curl.exe --location --fail --silent --show-error --retry 3 --connect-timeout 30 --max-time 3600 --speed-limit 1024 --speed-time 60 --continue-at - --output $partial $component.url
        if ($LASTEXITCODE -ne 0) { throw "No se pudo descargar $($component.id). Puedes repetir este comando para continuar." }
        if ((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLowerInvariant() -ne $component.sha256) { throw "SHA-256 incorrecto: $($component.id). No se ejecutara este archivo." }
        Move-Item -LiteralPath $partial -Destination $file -Force
    }
    if ($component.extract) { Expand-Archive -LiteralPath $file -DestinationPath (Join-Path $Destination $component.extract) -Force }
    Write-Output "Verificado $($component.id) $($component.version)"
}
$licenses = Join-Path $Destination 'licenses'
New-Item -ItemType Directory -Path $licenses -Force | Out-Null
$sources = @{
    'whisper.cpp-LICENSE.txt'='https://raw.githubusercontent.com/ggml-org/whisper.cpp/b5454/LICENSE';
    'llama.cpp-LICENSE.txt'='https://raw.githubusercontent.com/ggml-org/llama.cpp/b11838/LICENSE';
    'Qwen3-LICENSE.txt'='https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/bc640142c66e1fdd12af0bd68f40445458f3869b/LICENSE';
    'Whisper-LICENSE.txt'='https://raw.githubusercontent.com/openai/whisper/v20250625/LICENSE'
}
foreach ($name in $sources.Keys) {
    $licensePath = Join-Path $licenses $name
    if ($Offline) {
        if (-not (Test-Path -LiteralPath $licensePath) -or (Get-Item -LiteralPath $licensePath).Length -eq 0) { throw "Falta la licencia $name. Ejecuta sin -Offline." }
    } else { Invoke-WebRequest -UseBasicParsing -Uri $sources[$name] -OutFile $licensePath }
}
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $Destination 'components.json') -Force
Write-Output "Motores y modelos preparados en $Destination"
