@echo off
REM Gera dist\CatalogHub.apk (Android 7.0+). Usa o JDK 17, Gradle 8.7 e Android SDK de android-build-tools.
setlocal
set "TOOLS=%USERPROFILE%\android-build-tools"
set "JAVA_HOME=%TOOLS%\jdk\jdk-17.0.20.1+1"
set "ANDROID_HOME=%TOOLS%\sdk"
cd /d "%~dp0android"
call "%TOOLS%\gradle-8.7\bin\gradle.bat" assembleRelease --console=plain || goto erro
cd /d "%~dp0"
if not exist dist mkdir dist
copy /y android\app\build\outputs\apk\release\app-release.apk dist\CatalogHub.apk >nul
echo.
echo OK: dist\CatalogHub.apk gerado.
echo Para instalar com o celular no cabo USB (depuracao USB ativa):
echo   "%ANDROID_HOME%\platform-tools\adb.exe" install -r dist\CatalogHub.apk
exit /b 0
:erro
echo FALHA NO BUILD
exit /b 1
