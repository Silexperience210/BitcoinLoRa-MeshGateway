@echo off
REM Installation rapide du Bitcoin Mesh Gateway

echo ========================================
echo   Installation Bitcoin Mesh Gateway
echo ========================================
echo.

cd /d "%~dp0"

echo 🔍 Vérification de Python...
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Python n'est pas installé!
    echo.
    echo Veuillez installer Python 3.8+ depuis:
    echo https://www.python.org/downloads/
    echo.
    echo Assurez-vous de cocher "Add Python to PATH" pendant l'installation.
    pause
    exit /b 1
)

for /f "tokens=2" %%i in ('python --version 2^>^&1') do set python_version=%%i
echo ✅ Python trouvé: %python_version%
echo.

echo 📦 Installation des dépendances...
pip install --upgrade pip
pip install meshtastic pypubsub requests pyserial pysocks pyinstaller

if errorlevel 1 (
    echo ❌ Échec installation dépendances
    pause
    exit /b 1
)

echo ✅ Dépendances installées
echo.

echo 🔨 Construction de l'exécutable...
call build_exe.bat
if errorlevel 1 (
    echo ❌ Échec construction
    pause
    exit /b 1
)

echo.
echo 🎉 Installation terminée!
echo.
echo Fichiers créés:
echo   • bitcoin_mesh_gateway.py (script Python)
echo   • launch_gateway.bat (lancement automatique)
echo   • launch_gateway.ps1 (lancement PowerShell)
echo   • BitcoinMeshGateway.exe (exécutable autonome)
echo   • gateway.ini (configuration)
echo.
echo Pour démarrer: double-cliquez sur launch_gateway.bat
echo.

pause