@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
set "GAME_EXIT=0"
where java >nul 2>nul
if errorlevel 1 goto nojava
if exist "build\classes" rmdir /s /q "build\classes"
if not exist "build\classes" mkdir "build\classes"
if not exist "dist" mkdir "dist"
(for /r "src\main\java" %%f in (*.java) do @echo "%%f") > "build\sources.txt"
java -Dfile.encoding=UTF-8 -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 --release 17 -d "build\classes" @build\sources.txt
if errorlevel 1 goto failed
xcopy /E /I /Y "src\main\resources\*" "build\classes\" >nul
if errorlevel 1 goto failed
java -m jdk.jartool/sun.tools.jar.Main --create --file "dist\ColorDuel.jar" --main-class vn.ptit.colorgame.Main -C "build\classes" .
if errorlevel 1 goto failed
echo Da bien dich thanh cong: dist\ColorDuel.jar
goto end
:nojava
echo Chua tim thay Java. Hay cai JDK 17 tro len va them Java vao PATH.
goto failed
:failed
echo Build that bai. Kiem tra java -version va dung JDK 17 tro len.
set "GAME_EXIT=1"
:end
if /I not "%~1"=="--no-pause" pause
exit /b %GAME_EXIT%
