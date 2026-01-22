# Quick Start (English)

You can simply download the provided Bitcoin Mesh Gateway .exe file and run it directly on Windows. No Python or manual installation is required. Just double-click the .exe to launch the gateway and follow the on-screen instructions. For advanced options (Tor, configuration), see below.

# Bitcoin Mesh Gateway

Un gateway LoRa pour relayer des transactions Bitcoin depuis un réseau mesh Meshtastic vers le réseau Bitcoin, avec support Tor pour l'anonymat.

## Architecture

```
[Zone sans Internet]          [Gateway avec Internet]           [Bitcoin Network]

📱 Client ──LoRa──▶ 📡 T-Beam ──LoRa──▶ 📡 Gateway T-Beam ──USB──▶ 💻 Gateway ──Tor/Web──▶ ₿ Bitcoin Node
```

## Installation

### Dépendances

```bash
pip install meshtastic pypubsub requests pyserial pysocks
```

### Configuration

1. Connectez votre T-Beam gateway en USB
2. Lancez le gateway avec l'une des méthodes suivantes

## Distribution (.exe)

Pour créer un exécutable Windows autonome:

```cmd
build_exe.bat
```

Cela génère un fichier `BitcoinMeshGateway.exe` dans le dossier `dist/` qui peut être distribué sans nécessiter Python.

### Dépendances pour le build

```bash
pip install pyinstaller
```

## Configuration

Le gateway peut être configuré via le fichier `gateway.ini` :

```ini
[mesh]
port = COM3  # Port série (optionnel, détection automatique sinon)

[bitcoin]
api = Mempool  # Mempool, Blockstream, ou BitcoinCore
network = mainnet  # mainnet ou testnet

[tor]
enabled = true
host = 127.0.0.1
port = 9050

[bitcoincore]
rpc_user = bitcoinrpc
rpc_password = yourpassword
```

## Utilisation

### Mode Automatique (Recommandé)

Le mode automatique utilise **Mempool.space** par défaut pour diffuser les transactions Bitcoin en raw hex, avec support Tor pour l'anonymat.

### Mode Automatique (Recommandé)

#### Windows (Batch)
```cmd
launch_gateway.bat
```

#### Windows (PowerShell)
```powershell
.\launch_gateway.ps1
```

#### Manuel
```bash
python bitcoin_mesh_gateway.py --auto --tor
```

### Mode Interface Graphique

```bash
python bitcoin_mesh_gateway.py
```

## Options de Lancement

- `--auto`: Connexion automatique au premier port série disponible
- `--tor`: Active automatiquement Tor pour l'anonymat
- `--api [Mempool|Blockstream|BitcoinCore]`: API Bitcoin à utiliser (défaut: Mempool)
- `--network [mainnet|testnet]`: Réseau Bitcoin (défaut: mainnet)

## Exemples

```bash
# Mode automatique complet
python bitcoin_mesh_gateway.py --auto --tor

# Testnet sans Tor
python bitcoin_mesh_gateway.py --auto --network testnet --api Blockstream

# Avec Bitcoin Core local
python bitcoin_mesh_gateway.py --auto --api "Bitcoin Core (local)"
```

## Fonctionnalités

- ✅ **Connexion automatique** au mesh Meshtastic
- 🧅 **Support Tor** pour l'anonymat
- 📊 **Interface graphique** moderne avec statistiques
- 🔄 **Protocole chunké** pour transactions volumineuses
- 📱 **Support multi-clients** (Android app + firmware)
- ⚡ **Broadcast automatique** vers APIs Bitcoin
- 📋 **Logs détaillés** et historique des transactions

## APIs Supportées

- **Mempool.space** ⭐ (Recommandé - API par défaut en mode automatique)
- **Blockstream** (Alternative publique)
- **Bitcoin Core (local)** (Nœud local RPC)

- **Mempool.space**: API publique rapide
- **Blockstream**: API publique alternative
- **Bitcoin Core**: Nœud local RPC (nécessite configuration)

## Sécurité

- Utilise Tor par défaut pour l'anonymat
- Vérification d'intégrité des transactions
- Timeout automatique des transactions partielles
- Logs détaillés pour audit

## Dépannage

### Port série non trouvé
- Vérifiez que votre T-Beam est connecté en USB
- Installez les drivers USB-Série si nécessaire

### Erreur Tor
- Lancez Tor Browser ou un proxy SOCKS5 sur le port 9050
- Vérifiez les paramètres Tor dans l'interface

### Transactions non broadcastées
- Vérifiez la connexion internet
- Testez la connexion Bitcoin avec le bouton "Tester"
- Consultez les logs pour les détails d'erreur

## Développement

Le code source est organisé comme suit:

- `bitcoin_mesh_gateway.py`: Interface principale avec GUI
- `launch_gateway.bat`: Launcher Windows
- `launch_gateway.ps1`: Launcher PowerShell

## Licence

MIT License - voir le fichier LICENSE du projet Meshtastic.