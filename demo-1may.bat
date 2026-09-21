@echo off
setlocal
cd /d "%~dp0"
if not exist "dist\ColorDuel.jar" call build.bat --no-pause
if not exist "dist\ColorDuel.jar" goto end
echo Mo 1 server va 3 client tren cung may. Moi client dung mot tai khoan rieng.
start "Color Duel Server" "%ComSpec%" /c call "%~dp0run-server.bat"
start "Color Duel Client 1" "%ComSpec%" /c call "%~dp0run-client.bat"
start "Color Duel Client 2" "%ComSpec%" /c call "%~dp0run-client.bat"
start "Color Duel Client 3" "%ComSpec%" /c call "%~dp0run-client.bat"
:end
