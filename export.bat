@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
java -Dfile.encoding=UTF-8 -jar "dist\ColorDuel.jar" export "data" "reports"
pause
