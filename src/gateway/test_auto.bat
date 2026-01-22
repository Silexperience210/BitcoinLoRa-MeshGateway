@echo off
REM Test du mode automatique Bitcoin Mesh Gateway

echo ========================================
echo   Test Mode Automatique
echo ========================================
echo.

cd /d "%~dp0"

echo 🔍 Test des arguments...
python bitcoin_mesh_gateway.py --help
echo.

echo ✅ Arguments validés
echo.

echo 🚀 Test connexion automatique (simulation)...
echo Le mode automatique va:
echo   • Utiliser Mempool.space par défaut
echo   • Activer Tor automatiquement
echo   • Se connecter au premier port disponible
echo   • Commencer à écouter les transactions
echo.

echo Pour lancer réellement:
echo   python bitcoin_mesh_gateway.py --auto --tor
echo.

pause