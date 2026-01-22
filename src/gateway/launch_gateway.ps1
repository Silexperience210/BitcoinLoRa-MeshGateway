# Bitcoin Mesh Gateway Launcher
# Lance le gateway en mode automatique avec connexion mesh et Tor

param(
    [switch]$NoTor,
    [string]$Api = "Mempool",
    [string]$Network = "mainnet"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Bitcoin Mesh Gateway - Mode Auto" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier si Python est installé
try {
    $pythonVersion = python --version 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Python not found" }
}
catch {
    Write-Host "❌ Python n'est pas installé ou n'est pas dans le PATH" -ForegroundColor Red
    Write-Host "Veuillez installer Python 3.8+ depuis https://python.org" -ForegroundColor Yellow
    Read-Host "Appuyez sur Entrée pour quitter"
    exit 1
}

# Vérifier les dépendances
Write-Host "🔍 Vérification des dépendances..." -ForegroundColor Blue
try {
    python -c "import meshtastic, requests, serial.tools.list_ports" 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Dependencies missing" }
}
catch {
    Write-Host "📦 Installation des dépendances..." -ForegroundColor Blue
    pip install meshtastic pypubsub requests pyserial pysocks
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Échec installation dépendances" -ForegroundColor Red
        Read-Host "Appuyez sur Entrée pour quitter"
        exit 1
    }
}

Write-Host "✅ Dépendances OK" -ForegroundColor Green
Write-Host ""

# Préparer les arguments

$args = @("--auto", "--api", $Api, "--network", $Network)
if (-not $NoTor) {
    $args += "--tor"
}

Write-Host "🚀 Démarrage du gateway..." -ForegroundColor Green
Write-Host ""
Write-Host "Le gateway va:" -ForegroundColor White
Write-Host "  • Se connecter automatiquement au premier port disponible" -ForegroundColor White
if (-not $NoTor) {
    Write-Host "  • Activer Tor pour l'anonymat" -ForegroundColor White
}
Write-Host "  • Utiliser l'API $Api sur $Network" -ForegroundColor White
Write-Host "  • Commencer à écouter les transactions Bitcoin" -ForegroundColor White
Write-Host ""
Write-Host "Appuyez sur Ctrl+C pour arrêter" -ForegroundColor Yellow
Write-Host ""

# Lancer le gateway
try {
    & python bitcoin_mesh_gateway.py $args
}
catch {
    Write-Host "❌ Erreur lors du lancement: $_" -ForegroundColor Red
}
finally {
    Read-Host "Appuyez sur Entrée pour quitter"
}