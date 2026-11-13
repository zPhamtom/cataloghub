@echo off
REM Compila o CatalogHub e gera dist\CatalogHub.jar (compativel com Java 8+).
REM Ajuste JDK_HOME se o seu JDK estiver em outro local.
setlocal
if "%JDK_HOME%"=="" set "JDK_HOME=%USERPROFILE%\android-build-tools\jdk\jdk-17.0.20.1+1"
set "JAVAC=%JDK_HOME%\bin\javac.exe"
set "JAR=%JDK_HOME%\bin\jar.exe"
if not exist "%JAVAC%" set "JAVAC=javac"
if not exist "%JAR%" set "JAR=jar"

cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes
if not exist dist mkdir dist

dir /s /b core\src\*.java src\*.java > build\fontes.txt
"%JAVAC%" --release 8 -encoding UTF-8 -d build\classes @build\fontes.txt || goto erro
echo Main-Class: br.com.cataloghub.Main> build\manifest.txt
"%JAR%" cfm dist\CatalogHub.jar build\manifest.txt -C build\classes . || goto erro
echo.
echo OK: dist\CatalogHub.jar gerado. Execute com:  java -jar dist\CatalogHub.jar
exit /b 0
:erro
echo FALHA NA COMPILACAO
exit /b 1
