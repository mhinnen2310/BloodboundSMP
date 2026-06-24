@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\mitchsmp-src\build.ps1" -Deploy
if errorlevel 1 (
  echo.
  echo Deployment was not performed. Make sure the Minecraft server is fully stopped.
  pause
  exit /b 1
)
echo.
echo Deployment complete. You can start BloodboundSMP now.
pause
