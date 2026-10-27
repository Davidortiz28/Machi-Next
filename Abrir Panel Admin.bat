@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar-admin-machi-next.ps1"
if errorlevel 1 (
  echo.
  echo No se pudo abrir el panel administrativo. Revisa el mensaje anterior.
  pause
)
endlocal
