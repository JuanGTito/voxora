$ErrorActionPreference = 'Stop'
$VoxoraRoot = Split-Path -Parent $PSScriptRoot
function Get-VoxoraJdk {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { return $env:JAVA_HOME }
    $candidates = @()
    foreach ($folder in @((Join-Path $env:USERPROFILE '.jdks'), (Join-Path $env:ProgramFiles 'Java'), (Join-Path $env:ProgramFiles 'Microsoft'))) {
        if (Test-Path -LiteralPath $folder) { $candidates += Get-ChildItem -LiteralPath $folder -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } }
    }
    foreach ($candidate in ($candidates | Sort-Object Name -Descending)) {
        $release = Join-Path $candidate.FullName 'release'
        if ((Test-Path -LiteralPath $release) -and ((Get-Content -LiteralPath $release -Raw) -match 'JAVA_VERSION="21[.\"]')) { return $candidate.FullName }
    }
    throw 'Instala un JDK 21 y define JAVA_HOME con su carpeta.'
}
$env:JAVA_HOME = Get-VoxoraJdk
$env:MAVEN_USER_HOME = Join-Path $VoxoraRoot '.build-cache\maven-user-home'
$VoxoraMavenRepository = Join-Path $VoxoraRoot '.build-cache\m2'
function Invoke-VoxoraMaven {
    param([string[]]$Goals)
    Push-Location $VoxoraRoot
    try {
        & (Join-Path $VoxoraRoot 'mvnw.cmd') "-Dmaven.repo.local=$VoxoraMavenRepository" -B @Goals
        if ($LASTEXITCODE -ne 0) { throw "Maven terminó con código $LASTEXITCODE." }
    } finally { Pop-Location }
}
