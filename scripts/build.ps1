param([switch]$Offline)
. (Join-Path $PSScriptRoot 'toolchain.ps1')
$goals = @('verify')
if ($Offline) { $goals = @('-o') + $goals }
Invoke-VoxoraMaven -Goals $goals
