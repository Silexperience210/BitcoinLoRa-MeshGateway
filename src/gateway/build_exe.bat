@echo off
REM Build Bitcoin Mesh Gateway .exe

echo ========================================
echo   Build Bitcoin Mesh Gateway .exe
echo ========================================
echo.

cd /d "%~dp0"

REM Vérifier si Python est installé
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Python n'est pas installé
    pause
    exit /b 1
)

REM Vérifier si PyInstaller est installé
pyinstaller --version >nul 2>&1
if errorlevel 1 (
    echo 📦 Installation de PyInstaller...
    pip install pyinstaller
    if errorlevel 1 (
        echo ❌ Échec installation PyInstaller
        pause
        exit /b 1
    )
)

echo ✅ PyInstaller OK
echo.

REM Nettoyer les builds précédents
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist

echo 🔨 Construction de l'exécutable...
echo.

pyinstaller --clean gateway.spec

if errorlevel 1 (
    echo ❌ Échec de la construction
    pause
    exit /b 1
)

echo.
echo ✅ Construction réussie!
echo.
echo Fichiers générés dans le dossier 'dist':
dir /b dist\*.exe
echo.
echo Pour distribuer, copiez le fichier .exe et partagez-le.
echo L'exécutable est autonome et ne nécessite pas Python.
echo.

pause