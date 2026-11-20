@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\detener.ps1" %*
set CODIGO=%ERRORLEVEL%
echo.
pause
exit /b %CODIGO%
