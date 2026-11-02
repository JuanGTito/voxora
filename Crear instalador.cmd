@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-installer.ps1"
if errorlevel 1 (
  echo No se pudo crear el instalador. Revisa el mensaje anterior.
  pause
  exit /b 1
)
echo Instalador generado en la carpeta release.
pause
