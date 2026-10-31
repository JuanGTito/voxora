param([string]$AiHome, [string]$AudioFixture)
. (Join-Path $PSScriptRoot 'toolchain.ps1')
if (-not $AiHome) { $AiHome = Join-Path $VoxoraRoot '.local-runtime' }
if (-not $AudioFixture) {
    $AudioFixture = Join-Path $VoxoraRoot '.local-data\fixtures\prueba-es.wav'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'create-test-audio.ps1') -Destination $AudioFixture
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar el audio sintetico.' }
}
Invoke-VoxoraMaven -Goals @('-o', '-Dvoxora.realAi=true', "-Dvoxora.ai.home=$([IO.Path]::GetFullPath($AiHome))", "-Dvoxora.audioFixture=$([IO.Path]::GetFullPath($AudioFixture))", 'verify')
