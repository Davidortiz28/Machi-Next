@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar-machi-next.ps1"
if errorlevel 1 (
  echo.
  echo Machi Next no pudo iniciarse. Revisa el mensaje anterior.
  pause
)
endlocal
