<p align="center">
  <img src="https://img.shields.io/badge/Bitcoin-F7931A?style=for-the-badge&logo=bitcoin&logoColor=white" alt="Bitcoin"/>
  <img src="https://img.shields.io/badge/LoRa-00979D?style=for-the-badge&logo=arduino&logoColor=white" alt="LoRa"/>
  <img src="https://img.shields.io/badge/Meshtastic-67EA94?style=for-the-badge" alt="Meshtastic"/>
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/github/license/Silexperience210/BitcoinLoRa-MeshGateway?style=for-the-badge" alt="License"/>
</p>

<h1 align="center">⚡ BitcoinLoRa MeshGateway</h1>

<p align="center">
  <b>Broadcast Bitcoin transactions over LoRa mesh networks</b><br>
  <i>No Internet required at the sender's location</i>
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-architecture">Architecture</a> •
  <a href="#-hardware">Hardware</a> •
  <a href="#-installation">Installation</a> •
  <a href="#-android-app">Android App</a> •
  <a href="#-usage">Usage</a>
</p>

---

## 🎯 The Problem

Bitcoin requires Internet connectivity to broadcast transactions. In many scenarios, this is impossible:

| Scenario | Challenge |
|----------|-----------|
| 🏔️ **Remote Areas** | No cellular or WiFi coverage |
| 🌊 **Maritime** | Offshore with no connectivity |
| ⚡ **Disasters** | Infrastructure destroyed |
| 🚫 **Censorship** | Bitcoin access blocked |
| 🔒 **Privacy** | Avoid IP tracking |

**BitcoinLoRa MeshGateway** bridges offline users to the Bitcoin network using long-range LoRa mesh technology powered by Meshtastic.

---

## ✨ Features

- 📡 **Long Range** — Up to 10+ km line-of-sight with LoRa
- 🔗 **Mesh Networking** — Automatic multi-hop routing via Meshtastic
- 🧅 **Tor Support** — Optional .onion routing for IP privacy
- 📦 **Chunked Protocol** — Handles transactions up to 2KB
- 📱 **Android App** — Cyberpunk NEON UI with BLE connection
- 🖥️ **Desktop GUIs** — Client and Gateway applications
- 🔄 **Multi-API Fallback** — Mempool.space, Blockstream, Bitcoin Core
- ✅ **Confirmations** — ACK when TX reaches Bitcoin network

---

## 🏗️ Architecture

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│                 │  LoRa   │                 │  LoRa   │                 │
│  OFFLINE USER   │ ~~~~~~~ │  MESH RELAY(S)  │ ~~~~~~~ │    GATEWAY      │
│                 │         │                 │         │                 │
│  ┌───────────┐  │         │  ┌───────────┐  │         │  ┌───────────┐  │
│  │ T-Beam    │  │         │  │ T-Beam    │  │         │  │ T-Beam    │  │
│  │ + Android │  │         │  │ Meshtastic│  │         │  │ + Gateway │  │
│  │   App     │  │         │  │  Router   │  │         │  │   App     │  │
│  └───────────┘  │         │  └───────────┘  │         │  └─────┬─────┘  │
└─────────────────┘         └─────────────────┘         └────────┼────────┘
                                                                 │
                                                          ┌──────▼──────┐
                                                          │  Internet   │
                                                          │  (or Tor)   │
                                                          └──────┬──────┘
                                                                 │
                                                          ┌──────▼──────┐
                                                          │   Bitcoin   │
                                                          │   Network   │
                                                          └─────────────┘
```

**Data Flow:**
1. User creates signed Bitcoin transaction offline
2. Android app sends TX via BLE to T-Beam
3. T-Beam broadcasts over LoRa mesh
4. Gateway node receives and forwards to Bitcoin network
5. Confirmation relayed back through mesh

---

## 🔧 Hardware

### Recommended: LILYGO T-Beam v1.2

| Specification | Value |
|---------------|-------|
| **MCU** | ESP32-D0WDQ6-V3 (240MHz dual-core) |
| **LoRa** | SX1262 (868/915 MHz) |
| **Range** | 2-10+ km depending on terrain |
| **Battery** | 18650 holder included |
| **GPS** | NEO-6M (optional for this project) |

### Where to Buy

- [**LILYGO Official Store**](https://www.aliexpress.com/item/32915894264.html) — AliExpress
- [**Amazon**](https://amazon.com) — Search "LILYGO T-Beam v1.2"
- [**Banggood**](https://www.banggood.com) — Search "T-Beam SX1262"

> ⚠️ **Important:** Get **v1.2** with **SX1262** radio for best performance.

---

## 📥 Installation

### Prerequisites

- Python 3.8+
- PlatformIO CLI
- LILYGO T-Beam v1.2
- USB data cable

### Quick Start (Manual)

```bash
# Clone repository
git clone https://github.com/Silexperience210/BitcoinLoRa-MeshGateway.git
cd BitcoinLoRa-MeshGateway

# Install dependencies
pip install -r requirements.txt

# Flash Meshtastic firmware to T-Beam
# (See Meshtastic documentation)
```

### Windows Installer (Recommended)

For Windows users, we provide an **automatic installer** that handles everything:

📦 **[Download BitcoinMeshGateway_Setup.exe](https://github.com/Silexperience210/BitcoinLoRa-MeshGateway/releases/latest)**

**The installer will automatically:**
- ✅ Install Python 3.12 (if not present)
- ✅ Install all required dependencies (meshtastic, requests, pyserial, pysocks)
- ✅ Create desktop shortcut (optional)
- ✅ Add to Windows startup (optional)

**Manual Windows install:**
```powershell
# If you prefer manual installation
pip install meshtastic pypubsub requests pyserial pysocks
python bitcoin_mesh_gateway.py
```

### Firmware

The T-Beam runs standard **Meshtastic firmware** with the BitcoinTx module enabled. See [docs/FIRMWARE.md](docs/FIRMWARE.md) for detailed instructions.

---

## 📱 Android App

### BitcoinMesh NEON v2.0

<p align="center">
  <b>🌑 Cyberpunk Dark Theme • ⚡ Neon Glow Effects • 📡 Direct BLE Connection</b>
</p>

| Feature | Description |
|---------|-------------|
| 🎨 **Cyberpunk UI** | Dark theme with neon orange/green accents |
| ⚡ **Animations** | Pulse rings and glow effects |
| 🔗 **Bluetooth LE** | Direct connection to Meshtastic |
| 📦 **Auto-Chunking** | Splits TX into 190-byte packets |
| 📊 **Progress** | Real-time transmission status |

### Download

📦 **[Download APK](https://github.com/Silexperience210/BitcoinLoRa-MeshGateway/releases/latest)**

### Build from Source

```bash
cd android
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/
```

---

## 🚀 Usage

### Mobile (Android App)

1. Install the APK and grant Bluetooth permissions
2. Power on your T-Beam with Meshtastic firmware
3. Open **BitcoinMesh NEON** app
4. Tap **CONNECT** — app scans for Meshtastic devices
5. Paste your signed Bitcoin transaction (hex format)
6. Tap **⚡ BROADCAST TO MESH**
7. Watch the transmission progress

### Desktop Client

```bash
python src/client/bitcoin_mesh_client.py
```

### Gateway Server

```bash
python src/gateway/bitcoin_mesh_gateway.py
```

The gateway listens for mesh messages and broadcasts valid transactions to the Bitcoin network.

---

## 📋 Protocol

BitcoinMesh uses a chunked text protocol over Meshtastic TEXT_MESSAGE:

```
BTX:<chunk>/<total>:<hex_data>
```

| Example | Description |
|---------|-------------|
| `BTX:1/3:0100000001...` | Chunk 1 of 3 |
| `BTX:2/3:a1b2c3d4e5...` | Chunk 2 of 3 |
| `BTX:3/3:f6g7h8i9j0` | Chunk 3 of 3 (final) |

Gateway reassembles chunks and broadcasts complete transaction.

---

## 🧅 Tor Integration

Gateway supports Tor for enhanced privacy:

```
Gateway → Tor SOCKS5 → .onion endpoints → Bitcoin Network
```

Configure in gateway settings to route through Tor.

---

## 📊 Specifications

| Parameter | Value |
|-----------|-------|
| Max TX Size | ~2 KB |
| Chunk Size | 190 bytes |
| LoRa Range | 2-10+ km |
| Throughput | ~1 KB / 10 sec |

---

## 🗺️ Roadmap

- [x] Chunked BTX protocol
- [x] Desktop Client GUI
- [x] Gateway with Tor support
- [x] Android App (NEON v2.0)
- [ ] Lightning Network support
- [ ] PSBT signing support
- [ ] iOS application
- [ ] Satellite uplink (Blockstream)

---

## 🤝 Contributing

Contributions welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting PRs.

---

## 📜 License

This project is licensed under the MIT License — see [LICENSE](LICENSE) for details.

---

## 👤 Authors

**[Silexperience210](https://github.com/Silexperience210)** — Creator & Lead Developer

Special thanks to **[@ProfEduStream](https://github.com/ProfEduStream)** 🎓

---

## 💝 Support

If this project helps you, consider supporting development:

| Method | Address |
|--------|---------|
| **Bitcoin** | `bc1qva34vcnefrlde23puratcdyg3gvyd0xq70kutw` |
| **Lightning** | `silexperience@getalby.com` |

---

<p align="center">
  <b>₿ Bitcoin is Freedom. Mesh Makes it Unstoppable. ⚡</b><br>
  <i>"They can't stop the signal."</i>
</p>
# Build trigger 2026-01-20 21:23
