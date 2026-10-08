@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
chcp 65001 >nul
set "GAME_EXIT=0"
where java >nul 2>nul
if errorlevel 1 goto nojava
if exist "build\classes" rmdir /s /q "build\classes"
if not exist "build\classes" mkdir "build\classes"
if not exist "dist" mkdir "dist"
if not exist "dist\lib" mkdir "dist\lib"
if not exist "dist\lib\mysql-connector-j-8.3.0.jar" if exist "%USERPROFILE%\.m2\repository\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar" copy /Y "%USERPROFILE%\.m2\repository\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar" "dist\lib\" >nul
if not exist "dist\lib\mysql-connector-j-8.3.0.jar" goto nodriver
(for /r "src\main\java" %%f in (*.java) do (
set "GAME_SOURCE=%%f"
echo "!GAME_SOURCE:\=/!"
)) > "build\sources.txt"
java -Dfile.encoding=UTF-8 -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 --release 17 -d "build\classes" @build\sources.txt
if errorlevel 1 goto failed
xcopy /E /I /Y "src\main\resources\*" "build\classes\" >nul
if errorlevel 1 goto failed
> "build\manifest.mf" echo Manifest-Version: 1.0
>> "build\manifest.mf" echo Main-Class: vn.ptit.colorgame.Main
>> "build\manifest.mf" echo Class-Path: lib/mysql-connector-j-8.3.0.jar
>> "build\manifest.mf" echo.
java -m jdk.jartool/sun.tools.jar.Main --create --file "dist\ColorDuel-30s.jar" --manifest "build\manifest.mf" -C "build\classes" .
if errorlevel 1 goto failed
echo Da bien dich thanh cong: dist\ColorDuel-30s.jar
goto end
:nojava
echo Chua tim thay Java. Hay cai JDK 17 tro len va them Java vao PATH.
goto failed
:nodriver
echo Thieu dist\lib\mysql-connector-j-8.3.0.jar. Xem docs\MYSQL.md.
goto failed
:failed
echo Build that bai. Kiem tra java -version va dung JDK 17 tro len.
set "GAME_EXIT=1"
:end
if /I not "%~1"=="--no-pause" pause
exit /b %GAME_EXIT%
