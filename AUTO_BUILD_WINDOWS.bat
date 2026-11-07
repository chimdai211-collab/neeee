@echo off
chcp 65001 > nul
echo.
echo ╔══════════════════════════════════════════╗
echo ║      MicEQ Pro - AUTO BUILD APK          ║
echo ║   Không cần Android Studio!              ║
echo ╚══════════════════════════════════════════╝
echo.

:: ─── CẤU HÌNH ───────────────────────────────
set TOOLS_DIR=%USERPROFILE%\android-cmdtools
set SDK_DIR=%USERPROFILE%\android-sdk
set GRADLE_VERSION=8.2
set GRADLE_DIR=%USERPROFILE%\gradle-%GRADLE_VERSION%
set CMD_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip
set GRADLE_URL=https://services.gradle.org/distributions/gradle-8.2-bin.zip

:: ─── KIỂM TRA JAVA ──────────────────────────
echo [1/6] Kiểm tra Java...
java -version > nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Chưa có Java! Tải tại: https://adoptium.net
    echo     Cài xong chạy lại file này.
    pause & exit /b 1
)
echo ✅ Java OK

:: ─── TẢI GRADLE NẾU CHƯA CÓ ───────────────
echo.
echo [2/6] Kiểm tra Gradle...
if exist "%GRADLE_DIR%\bin\gradle.bat" (
    echo ✅ Gradle đã có
) else (
    echo 📥 Đang tải Gradle %GRADLE_VERSION%...
    powershell -Command "Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%TEMP%\gradle.zip' -UseBasicParsing"
    if %ERRORLEVEL% NEQ 0 ( echo ❌ Lỗi tải Gradle & pause & exit /b 1 )
    powershell -Command "Expand-Archive -Path '%TEMP%\gradle.zip' -DestinationPath '%USERPROFILE%' -Force"
    echo ✅ Gradle xong
)
set PATH=%GRADLE_DIR%\bin;%PATH%

:: ─── TẢI ANDROID CMD TOOLS NẾU CHƯA CÓ ────
echo.
echo [3/6] Kiểm tra Android SDK tools...
if exist "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" (
    echo ✅ SDK Tools đã có
) else (
    echo 📥 Đang tải Android Command Line Tools...
    if not exist "%SDK_DIR%\cmdline-tools" mkdir "%SDK_DIR%\cmdline-tools"
    powershell -Command "Invoke-WebRequest -Uri '%CMD_TOOLS_URL%' -OutFile '%TEMP%\cmdtools.zip' -UseBasicParsing"
    if %ERRORLEVEL% NEQ 0 ( echo ❌ Lỗi tải SDK Tools & pause & exit /b 1 )
    powershell -Command "Expand-Archive -Path '%TEMP%\cmdtools.zip' -DestinationPath '%TEMP%\cmdtools_extract' -Force"
    if not exist "%SDK_DIR%\cmdline-tools\latest" mkdir "%SDK_DIR%\cmdline-tools\latest"
    xcopy /E /I /Y "%TEMP%\cmdtools_extract\cmdline-tools\*" "%SDK_DIR%\cmdline-tools\latest\" > nul
    echo ✅ SDK Tools xong
)
set ANDROID_HOME=%SDK_DIR%
set PATH=%SDK_DIR%\cmdline-tools\latest\bin;%SDK_DIR%\platform-tools;%PATH%

:: ─── CÀI ANDROID SDK 34 ──────────────────
echo.
echo [4/6] Kiểm tra Android SDK 34...
if exist "%SDK_DIR%\platforms\android-34" (
    echo ✅ SDK 34 đã có
) else (
    echo 📥 Đang cài Android SDK 34 + Build Tools...
    echo y | sdkmanager "platforms;android-34" "build-tools;34.0.0" > nul 2>&1
    if %ERRORLEVEL% NEQ 0 ( echo ❌ Lỗi cài SDK & pause & exit /b 1 )
    echo ✅ SDK 34 xong
)

:: ─── TẠO GRADLE WRAPPER ──────────────────
echo.
echo [5/6] Tạo Gradle wrapper...
cd /d "%~dp0"
if not exist "gradlew.bat" (
    gradle wrapper --gradle-version %GRADLE_VERSION% > nul 2>&1
    if %ERRORLEVEL% NEQ 0 ( echo ❌ Lỗi tạo wrapper & pause & exit /b 1 )
)
echo ✅ Wrapper OK

:: ─── BUILD APK ───────────────────────────
echo.
echo [6/6] Đang build APK...
echo     (lần đầu có thể mất 2-5 phút)
echo.
set JAVA_TOOL_OPTIONS=--add-opens java.base/java.io=ALL-UNNAMED
call gradlew.bat assembleDebug

if %ERRORLEVEL% == 0 (
    echo.
    echo ╔══════════════════════════════════════════╗
    echo ║           ✅ BUILD THÀNH CÔNG!           ║
    echo ╚══════════════════════════════════════════╝
    echo.
    echo 📱 APK ở đây:
    echo    app\build\outputs\apk\debug\app-debug.apk
    echo.
    explorer app\build\outputs\apk\debug\
) else (
    echo.
    echo ❌ Build thất bại - xem lỗi ở trên
)
pause
