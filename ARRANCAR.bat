@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar.ps1" %*
set CODIGO=%ERRORLEVEL%
if not "%CODIGO%"=="0" (
  echo.
  echo El arranque termino con errores. Revisa los mensajes de arriba.
)
echo.
pause
exit /b %CODIGO%
