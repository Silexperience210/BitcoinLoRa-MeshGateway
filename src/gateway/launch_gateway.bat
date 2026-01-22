@echo off
REM Bitcoin Mesh Gateway Launcher
REM Lance le gateway en mode automatique avec connexion mesh et Tor

echo ========================================
echo   Bitcoin Mesh Gateway - Mode Auto
echo ========================================
echo.

cd /d "%~dp0"

REM Vérifier si Python est installé
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Python n'est pas installé ou n'est pas dans le PATH
    echo Veuillez installer Python 3.8+ depuis https://python.org
    pause
    exit /b 1
)

REM Vérifier les dépendances
echo 🔍 Vérification des dépendances...
python -c "import meshtastic, requests, serial.tools.list_ports" >nul 2>&1
if errorlevel 1 (
    echo 📦 Installation des dépendances...
    pip install meshtastic pypubsub requests pyserial pysocks
    if errorlevel 1 (
        echo ❌ Échec installation dépendances
        pause
        exit /b 1
    )
)

echo ✅ Dépendances OK
echo.

REM Lancer le gateway en mode automatique
echo 🚀 Démarrage du gateway...
echo.
echo Le gateway va:
echo   • Se connecter automatiquement au premier port disponible
echo   • Activer Tor pour l'anonymat
echo   • Commencer à écouter les transactions Bitcoin
echo.
echo Appuyez sur Ctrl+C pour arrêter
echo.

python bitcoin_mesh_gateway.py --auto --tor

pause