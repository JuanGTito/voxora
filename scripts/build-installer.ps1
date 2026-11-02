param([switch]$Offline, [string]$CompilerPath)
# Equipo: LFTY JGTM SCGC YARS DMFY
. (Join-Path $PSScriptRoot 'toolchain.ps1')
$compilerVersion = '7.1.1'
$compilerHash = '8A5A6711C3766A6E5FB74F280BA0E3551E719CE94538D901FDBD1F8F9920B3F0'
if (-not $CompilerPath) {
    $compilerHome = Join-Path $VoxoraRoot ".build-cache\inno-$compilerVersion"
    $CompilerPath = Join-Path $compilerHome 'ISCC.exe'
    if (-not (Test-Path -LiteralPath $CompilerPath)) {
        if ($Offline) { throw 'Compilador no preparado. Ejecuta una primera vez sin -Offline, o indica -CompilerPath.' }
        $download = Join-Path $VoxoraRoot '.build-cache\inno-download\innosetup.exe'
        New-Item -ItemType Directory -Force (Split-Path $download) | Out-Null
        Invoke-WebRequest "https://github.com/jrsoftware/issrc/releases/download/is-7_1_1/innosetup-$compilerVersion-x64.exe" -OutFile $download
        if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne $compilerHash) { throw 'SHA-256 incorrecto del compilador.' }
        if ((Get-AuthenticodeSignature -LiteralPath $download).Status -ne 'Valid') { throw 'Firma del compilador no valida.' }
        $process = Start-Process -FilePath $download -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/CURRENTUSER', "/DIR=`"$compilerHome`"") -WindowStyle Hidden -Wait -PassThru
        if ($process.ExitCode -ne 0) { throw "No se pudo preparar Inno Setup: $($process.ExitCode)" }
    }
}
if (-not (Test-Path -LiteralPath $CompilerPath)) { throw 'No se encontro ISCC.exe.' }
$buildGoals = @('verify')
if ($Offline) { $buildGoals = @('-o') + $buildGoals }
Invoke-VoxoraMaven -Goals $buildGoals
& (Join-Path $PSScriptRoot 'prepare-ai.ps1') -Offline:$Offline
$packageResult = & (Join-Path $PSScriptRoot 'package.ps1') -SkipBuild -IncludeAi -PassThru
$package = $packageResult | Where-Object { $_ -is [pscustomobject] -and $_.PSObject.Properties['ImagePath'] } | Select-Object -Last 1
if (-not $package) { throw 'No se obtuvo la carpeta de distribucion.' }
$output = Join-Path $package.OutputPath 'installer'
& $CompilerPath "/DAppImage=$($package.ImagePath)" "/DOutputPath=$output" (Join-Path $VoxoraRoot 'installer\windows\voxora.iss')
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el instalador.' }
$installer = Join-Path $output 'Voxora-Setup-0.2.0-windows-x64.exe'
$hash = (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $([IO.Path]::GetFileName($installer))" | Set-Content -LiteralPath "$installer.sha256" -Encoding ascii
Write-Output "Instalador creado: $installer"
Write-Output "SHA-256: $hash"
