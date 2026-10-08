@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
chcp 65001 >nul
call build.bat --no-pause
if errorlevel 1 goto end
if not exist "build\test-classes" mkdir "build\test-classes"
(for /r "src\test\java" %%f in (*.java) do (
set "GAME_SOURCE=%%f"
echo "!GAME_SOURCE:\=/!"
)) > "build\test-sources.txt"
java -Dfile.encoding=UTF-8 -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 --release 17 -cp "build\classes" -d "build\test-classes" @build\test-sources.txt
if errorlevel 1 goto end
java -Dfile.encoding=UTF-8 -cp "build\classes;build\test-classes" vn.ptit.colorgame.IntegrationTest
if errorlevel 1 goto end
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\classes;build\test-classes" vn.ptit.colorgame.AudioManagerTest
if errorlevel 1 goto end
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\classes;build\test-classes" vn.ptit.colorgame.ClientAudioTest
if errorlevel 1 goto end
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\classes;build\test-classes" vn.ptit.colorgame.SettingsTest
if errorlevel 1 goto end
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\classes;build\test-classes" vn.ptit.colorgame.UiSmokeTest
if errorlevel 1 goto end
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "build\classes;build\test-classes" vn.ptit.colorgame.GuiNetworkTest
:end
pause
