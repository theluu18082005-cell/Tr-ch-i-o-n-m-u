@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
set "GAME_HOST=127.0.0.1"
set "GAME_PORT=5000"
if not "%~1"=="" set "GAME_HOST=%~1"
if not "%~2"=="" set "GAME_PORT=%~2"
if not exist "dist\ColorDuel-30s.jar" call build.bat --no-pause
if not exist "dist\ColorDuel-30s.jar" goto end
java -Dfile.encoding=UTF-8 -jar "dist\ColorDuel-30s.jar" client "%GAME_HOST%" "%GAME_PORT%"
:end
pause
