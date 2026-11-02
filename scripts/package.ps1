param([ValidateSet('app-image','msi')][string]$Type = 'app-image', [switch]$Offline, [switch]$SkipBuild, [switch]$IncludeAi, [switch]$PassThru)
. (Join-Path $PSScriptRoot 'toolchain.ps1')
if ($Type -eq 'msi') {
    $portableWix = Join-Path $VoxoraRoot '.build-cache\wix3141\tools'
    if (Test-Path -LiteralPath (Join-Path $portableWix 'candle.exe')) { $env:PATH = $portableWix + [IO.Path]::PathSeparator + $env:PATH }
}
$goals = @('verify')
if ($Offline) { $goals = @('-o') + $goals }
if (-not $SkipBuild) { Invoke-VoxoraMaven -Goals $goals }
elseif (-not (Test-Path -LiteralPath (Join-Path $VoxoraRoot 'target\voxora.jar'))) { throw 'Primero ejecuta scripts/build.ps1.' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$inputDirectory = Join-Path $VoxoraRoot "target\package-input\$stamp"
$destination = Join-Path $VoxoraRoot "release\$stamp"
New-Item -ItemType Directory -Path $inputDirectory -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $VoxoraRoot 'target\voxora.jar') -Destination $inputDirectory
Copy-Item -LiteralPath (Join-Path $VoxoraRoot 'target\lib') -Destination $inputDirectory -Recurse
if ($IncludeAi) {
    if ($Type -ne 'app-image') { throw 'Distribuye los modelos aparte del MSI; Qwen3 4B supera el limite de archivo del formato CAB.' }
    $runtime = Join-Path $VoxoraRoot '.local-runtime'
    if (-not (Test-Path -LiteralPath (Join-Path $runtime 'components.json'))) { throw 'Ejecuta scripts/prepare-ai.ps1 antes de incluir los modelos.' }
    Copy-Item -LiteralPath $runtime -Destination (Join-Path $inputDirectory 'ai') -Recurse
}
$arguments = @('--type', $Type, '--input', $inputDirectory, '--main-jar', 'voxora.jar', '--main-class', 'dev.voxora.Main', '--name', 'Voxora', '--app-version', '0.2.0', '--vendor', 'Voxora', '--dest', $destination, '--java-options', '-Xmx512m')
$arguments += @('--icon', (Join-Path $VoxoraRoot 'src\main\resources\icons\voxora.ico'))
if ($Type -eq 'msi') { $arguments += @('--win-per-user-install', '--win-menu', '--win-shortcut', '--win-upgrade-uuid', '0da4c42f-5579-4f5b-9751-2f377d0ef9d5') }
& (Join-Path $env:JAVA_HOME 'bin\jpackage.exe') @arguments
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el paquete. MSI requiere WiX compatible con jpackage del JDK 21.' }
Copy-Item -LiteralPath (Join-Path $VoxoraRoot 'docs\THIRD_PARTY.md') -Destination $destination
Get-ChildItem -LiteralPath $destination -File -Recurse | Get-FileHash -Algorithm SHA256 | ForEach-Object {
    '{0}  {1}' -f $_.Hash.ToLowerInvariant(), $_.Path.Substring($destination.Length + 1)
} | Set-Content -LiteralPath (Join-Path $destination 'SHA256SUMS.txt') -Encoding utf8
Write-Output "Distribución creada en: $destination"
if ($PassThru) { Write-Output ([pscustomobject]@{ ImagePath = (Join-Path $destination 'Voxora'); OutputPath = $destination }) }
