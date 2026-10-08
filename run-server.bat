@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
set "GAME_PORT=5000"
if not "%~1"=="" set "GAME_PORT=%~1"
if not exist "dist\ColorDuel-30s.jar" call build.bat --no-pause
if not exist "dist\ColorDuel-30s.jar" goto end
echo May nay la SERVER. Giu cua so nay mo trong khi choi.
echo Cac client nhap dia chi IPv4 cua may nay va cong %GAME_PORT%.
java -Dfile.encoding=UTF-8 -jar "dist\ColorDuel-30s.jar" server "%GAME_PORT%" "data"
:end
pause
