. (Join-Path $PSScriptRoot 'toolchain.ps1')
$javaFiles = @(Get-ChildItem -LiteralPath @((Join-Path $VoxoraRoot 'src'), (Join-Path $VoxoraRoot 'scripts')) -Filter '*.java' -Recurse | ForEach-Object FullName)
$formatter = Join-Path $VoxoraRoot '.build-cache\google-java-format.jar'
if (-not (Test-Path -LiteralPath $formatter)) { throw 'Prepara google-java-format 1.28.0 en la cache local antes de ejecutar este script.' }
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -jar $formatter --replace @javaFiles
if ($LASTEXITCODE -ne 0) { throw 'No se pudo formatear Java.' }
& (Join-Path $env:JAVA_HOME 'bin\java.exe') (Join-Path $PSScriptRoot 'AnnotateTeam.java') (Join-Path $VoxoraRoot 'src')
if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar la anotacion del equipo.' }
& (Join-Path $env:JAVA_HOME 'bin\java.exe') (Join-Path $PSScriptRoot 'AnnotateTeam.java') $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar la anotacion de las herramientas.' }
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -jar $formatter --replace @javaFiles
if ($LASTEXITCODE -ne 0) { throw 'No se pudo formatear el codigo anotado.' }
