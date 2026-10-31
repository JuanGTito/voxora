param([string]$DataDirectory, [string]$AiHome)
. (Join-Path $PSScriptRoot 'toolchain.ps1')
$jar = Join-Path $VoxoraRoot 'target\voxora.jar'
if (-not (Test-Path -LiteralPath $jar)) { Invoke-VoxoraMaven -Goals @('verify') }
$arguments = @('-Xmx512m', '-jar', $jar)
if ($DataDirectory) { $arguments += "--data=$([System.IO.Path]::GetFullPath($DataDirectory))" }
if (-not $AiHome -and (Test-Path -LiteralPath (Join-Path $VoxoraRoot '.local-runtime\components.json'))) { $AiHome = Join-Path $VoxoraRoot '.local-runtime' }
if ($AiHome) { $arguments += "--ai-home=$([System.IO.Path]::GetFullPath($AiHome))" }
& (Join-Path $env:JAVA_HOME 'bin\java.exe') @arguments
if ($LASTEXITCODE -ne 0) { throw "Voxora terminó con código $LASTEXITCODE." }
