@echo off
setlocal EnableDelayedExpansion

echo ============================================
echo   Squishy Blob -- Setup and Run
echo ============================================
echo.

:: ============================================================
::  STEP 1 -- Find a compatible JDK (Java 11-21)
::  Gradle 8.5 supports Java 8-21. Java 22+ will fail.
::  We search common install locations before falling back
::  to winget to install Eclipse Temurin 17 (LTS).
:: ============================================================

set "GRADLE_JAVA="

:: Eclipse Temurin / Adoptium -- JDK 17
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do (
    if exist "%%~D\bin\java.exe" if not defined GRADLE_JAVA set "GRADLE_JAVA=%%~D"
)

:: Eclipse Temurin / Adoptium -- JDK 21
if not defined GRADLE_JAVA (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do (
        if exist "%%~D\bin\java.exe" if not defined GRADLE_JAVA set "GRADLE_JAVA=%%~D"
    )
)

:: Microsoft Build of OpenJDK -- JDK 17
if not defined GRADLE_JAVA (
    for /d %%D in ("C:\Program Files\Microsoft\jdk-17*") do (
        if exist "%%~D\bin\java.exe" if not defined GRADLE_JAVA set "GRADLE_JAVA=%%~D"
    )
)

:: Microsoft Build of OpenJDK -- JDK 21
if not defined GRADLE_JAVA (
    for /d %%D in ("C:\Program Files\Microsoft\jdk-21*") do (
        if exist "%%~D\bin\java.exe" if not defined GRADLE_JAVA set "GRADLE_JAVA=%%~D"
    )
)

:: Oracle JDK 17 (skip 25+ which is incompatible with Gradle 8.5)
if not defined GRADLE_JAVA (
    for /d %%D in ("C:\Program Files\Java\jdk-17*") do (
        if exist "%%~D\bin\java.exe" if not defined GRADLE_JAVA set "GRADLE_JAVA=%%~D"
    )
)

:: Oracle JDK 21
if not defined GRADLE_JAVA (
    for /d %%D in ("C:\Program Files\Java\jdk-21*") do (
        if exist "%%~D\bin\java.exe" if not defined GRADLE_JAVA set "GRADLE_JAVA=%%~D"
    )
)

:: Nothing compatible found -- install Temurin 17 via winget
if not defined GRADLE_JAVA (
    echo [INFO] No compatible JDK ^(17 or 21^) found.
    echo [INFO] Installing Eclipse Temurin JDK 17 via winget...
    echo.

    where winget >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] winget is not available on this machine.
        echo         Please install JDK 17 manually from:
        echo         https://adoptium.net/
        pause
        exit /b 1
    )

    winget install EclipseAdoptium.Temurin.17.JDK --accept-source-agreements --accept-package-agreements
    if errorlevel 1 (
        echo [ERROR] JDK installation failed.
        echo         Please install JDK 17 manually from:
        echo         https://adoptium.net/
        pause
        exit /b 1
    )

    :: Re-scan after install
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do (
        if exist "%%~D\bin\java.exe" if not defined GRADLE_JAVA set "GRADLE_JAVA=%%~D"
    )
)

if not defined GRADLE_JAVA (
    echo [ERROR] Could not locate a compatible JDK even after installation.
    echo         Please install JDK 17 manually from https://adoptium.net/
    echo         then re-run this script.
    pause
    exit /b 1
)

echo [OK] Java  : %GRADLE_JAVA%

:: ============================================================
::  STEP 2 -- Check for Node.js
::  Gradle's Kotlin/JS plugin delegates JS bundling to webpack
::  via Node, so Node must be on PATH.
:: ============================================================

where node >nul 2>&1
if errorlevel 1 (
    echo [INFO] Node.js not found.
    echo [INFO] Installing Node.js LTS via winget...
    echo.

    where winget >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] winget is not available on this machine.
        echo         Please install Node.js manually from https://nodejs.org/
        pause
        exit /b 1
    )

    winget install OpenJS.NodeJS.LTS --accept-source-agreements --accept-package-agreements
    if errorlevel 1 (
        echo [ERROR] Node.js installation failed.
        echo         Please install it manually from https://nodejs.org/
        pause
        exit /b 1
    )

    :: winget installs to a new PATH entry -- add it for this session
    set "PATH=%PATH%;C:\Program Files\nodejs"

    where node >nul 2>&1
    if errorlevel 1 (
        echo [WARN] Node.js was installed but is not yet visible in PATH.
        echo        Please open a new terminal and run this script again.
        pause
        exit /b 1
    )
)

for /f "delims=" %%v in ('node --version') do set NODE_VER=%%v
echo [OK] Node  : %NODE_VER%

:: ============================================================
::  STEP 3 -- Launch the dev server
:: ============================================================

echo.
echo ============================================
echo   Starting at http://localhost:8080/
echo   Press Ctrl+C to stop the server.
echo ============================================
echo.

call gradlew.bat jsBrowserDevelopmentRun "-Dorg.gradle.java.home=%GRADLE_JAVA%"

endlocal
