@echo off
cd /d "%~dp0"
python bitcoin_mesh_gateway.py
if %errorlevel% neq 0 (
    echo.
    echo Erreur lors du lancement. Appuyez sur une touche...
    pause >nul
)
