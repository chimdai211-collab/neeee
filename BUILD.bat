@echo off
echo ====================================
echo   MicEQ Pro - Auto Build APK
echo ====================================
echo.
echo Requires: Android Studio installed
echo.
set STUDIO_PATH=
for %%p in (
  "C:\Program Files\Android\Android Studio\bin\studio64.exe"
  "C:\Program Files\Android\Android Studio\bin\studio.exe"
) do (
  if exist %%p set STUDIO_PATH=%%p
)

echo Looking for gradlew...
cd /d "%~dp0"
call gradlew.bat assembleDebug
if %ERRORLEVEL% == 0 (
  echo.
  echo SUCCESS! APK created at:
  echo app\build\outputs\apk\debug\app-debug.apk
  explorer app\build\outputs\apk\debug\
) else (
  echo.
  echo Khong tim thay Gradle wrapper.
  echo Vui long mo bang Android Studio va build tu menu:
  echo Build ^> Build Bundle^(s^)/APK^(s^) ^> Build APK^(s^)
)
pause
