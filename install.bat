@echo off
setlocal enabledelayedexpansion
title Bitcoin Mesh Gateway - Installation
color 0A

echo.
echo  ============================================
echo     Bitcoin Mesh Gateway - Installation
echo  ============================================
echo.

:: Verifier si Python est installe
echo [1/4] Verification de Python...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo  Python n'est pas installe!
    echo  Telechargement de Python 3.12...
    echo.
    
    :: Telecharger Python
    powershell -Command "& {Invoke-WebRequest -Uri 'https://www.python.org/ftp/python/3.12.0/python-3.12.0-amd64.exe' -OutFile '%TEMP%\python_installer.exe'}"
    
    if exist "%TEMP%\python_installer.exe" (
        echo  Installation de Python (cela peut prendre quelques minutes^)...
        "%TEMP%\python_installer.exe" /quiet InstallAllUsers=1 PrependPath=1 Include_test=0
        
        :: Rafraichir le PATH
        set "PATH=%PATH%;C:\Program Files\Python312;C:\Program Files\Python312\Scripts"
        
        echo  Python installe avec succes!
    ) else (
        echo  ERREUR: Impossible de telecharger Python.
        echo  Veuillez installer Python manuellement depuis python.org
        pause
        exit /b 1
    )
) else (
    for /f "tokens=2" %%i in ('python --version 2^>^&1') do set PYVER=%%i
    echo  Python !PYVER! detecte.
)

echo.
echo [2/4] Mise a jour de pip...
python -m pip install --upgrade pip >nul 2>&1

echo.
echo [3/4] Installation des dependances...
echo.

:: Installer les dependances
python -m pip install meshtastic pypubsub requests pyserial pysocks

if %errorlevel% neq 0 (
    echo.
    echo  ERREUR lors de l'installation des dependances.
    pause
    exit /b 1
)

echo.
echo  Dependances installees avec succes!

echo.
echo [4/4] Configuration terminee!
echo.
echo  ============================================
echo     Installation terminee avec succes!
echo  ============================================
echo.
echo  Pour lancer le Bitcoin Mesh Gateway:
echo    - Double-cliquez sur "Bitcoin Mesh Gateway" sur le Bureau
echo    - Ou executez: python bitcoin_mesh_gateway.py
echo.

pause
