# ⚡ BitcoinMesh Gateway

<div align="center">

![Bitcoin](https://img.shields.io/badge/Bitcoin-F7931A?style=for-the-badge&logo=bitcoin&logoColor=white)
![LoRa](https://img.shields.io/badge/LoRa-00979D?style=for-the-badge&logo=arduino&logoColor=white)
![Meshtastic](https://img.shields.io/badge/Meshtastic-67EA94?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Beta-yellow?style=for-the-badge)

### 🛡️ Bitcoin Becomes Truly Uncensorable

*Broadcast Bitcoin transactions over LoRa mesh networks - no Internet required at the sender's location*

[Features](#-features) • [Hardware](#-hardware) • [Installation](#-installation) • [Usage](#-usage) • [Protocol](#-protocol)

</div>

---

## 🌍 The Problem

In many scenarios, direct Internet access is unavailable, unreliable, or actively censored:
- 🏔️ Remote areas without connectivity
- 🌊 Maritime/offshore environments
- ⚡ Natural disasters disrupting infrastructure
- 🚫 Authoritarian regimes blocking Bitcoin
- 🔒 Privacy-conscious users avoiding IP tracking

**BitcoinMesh Gateway solves this** by creating a bridge between offline users and the Bitcoin network using long-range LoRa mesh technology.

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 📡 **Long Range** | Up to 10+ km line-of-sight with LoRa |
| 🔗 **Mesh Networking** | Automatic multi-hop routing via Meshtastic |
| 🧅 **Tor Support** | Optional .onion routing for IP privacy |
| 📦 **Chunked Protocol** | Handles transactions up to 2KB |
| 🖥️ **GUI Applications** | Easy-to-use client and gateway interfaces |
| 🔄 **Multi-API Fallback** | Mempool.space, Blockstream, Bitcoin Core |
| ✅ **Acknowledgments** | Confirmation when TX reaches Bitcoin network |

---

## 🏗️ Architecture

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│                 │  LoRa   │                 │  LoRa   │                 │
│  OFFLINE USER   │ ~~~~~~~ │  MESH RELAY(S)  │ ~~~~~~~ │    GATEWAY      │
│                 │         │                 │         │                 │
│  ┌───────────┐  │         │  ┌───────────┐  │         │  ┌───────────┐  │
│  │ T-Beam    │  │         │  │ T-Beam    │  │         │  │ T-Beam    │  │
│  │ + Client  │  │         │  │ Meshtastic│  │         │  │ + Gateway │  │
│  │   GUI     │  │         │  │  Router   │  │         │  │   App     │  │
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

---

## 🔧 Hardware

### Recommended: LILYGO T-Beam v1.2

| Specification | Value |
|--------------|-------|
| **MCU** | ESP32-D0WDQ6-V3 (240MHz dual-core) |
| **LoRa** | SX1262 (868/915 MHz) |
| **GPS** | NEO-6M (optional for this project) |
| **Flash** | 4MB |
| **Battery** | 18650 holder included |
| **Range** | 2-10+ km depending on terrain |

### 📦 Where to Buy

| Store | Link | Region |
|-------|------|--------|
| **LILYGO Official** | [AliExpress](https://www.aliexpress.com/item/32915894264.html) | Worldwide |
| **Amazon** | Search "LILYGO T-Beam v1.2" | US/EU |
| **Banggood** | [T-Beam](https://www.banggood.com/search/lilygo-t-beam.html) | Worldwide |

> ⚠️ **Important**: Get the **v1.2** version with **SX1262** radio for best performance. Ensure you select the correct frequency for your region (868MHz for EU, 915MHz for US).

---

## 📥 Installation

### Prerequisites

- Python 3.8+
- PlatformIO (for firmware compilation)
- LILYGO T-Beam v1.2 hardware
- USB cable (data-capable, not charge-only)

### Quick Start

```bash
# Clone this repository
git clone https://github.com/Silexperience/BitcoinMeshGateway.git
cd BitcoinMeshGateway

# Install Python dependencies
pip install -r requirements.txt
```

### Firmware Installation

See the detailed [Setup Guide](docs/SETUP_GUIDE.md) for step-by-step instructions.

**Quick summary:**
1. Clone Meshtastic firmware
2. Copy `src/firmware/BitcoinTxModule.*` to `src/modules/`
3. Modify `Modules.cpp` to include the module
4. Build and flash: `pio run -e tbeam -t upload`

---

## 🚀 Usage

### Client (Offline Location)

```bash
python src/client/bitcoin_mesh_gui.py
```

1. Select COM port connected to T-Beam
2. Paste or load your signed transaction (hex format)
3. Click "Send Transaction"
4. Wait for acknowledgment

### Gateway (Internet-Connected Location)

```bash
python src/gateway/bitcoin_mesh_gateway.py
```

1. Select COM port connected to T-Beam
2. Enable Tor if desired (recommended)
3. Click "Start Gateway"
4. Gateway will automatically relay received transactions

---

## 📋 Protocol

BitcoinMesh Gateway uses a custom chunked protocol over Meshtastic's mesh network:

| Message Type | Code | Description |
|--------------|------|-------------|
| `TX_START` | 0x01 | Initiates transaction, contains total size |
| `TX_CHUNK` | 0x02 | Transaction data chunk (≤180 bytes) |
| `TX_END` | 0x03 | Marks end of transmission |
| `TX_ACK` | 0x04 | Acknowledgment with txid |
| `TX_ERROR` | 0x05 | Error notification with code |

See [Protocol Documentation](docs/PROTOCOL.md) for full specification.

---

## 🧅 Tor Integration

For maximum privacy, the gateway can route all Bitcoin network requests through Tor:

```
Gateway → Tor SOCKS5 (127.0.0.1:9050) → .onion endpoints → Bitcoin Network
```

Supported .onion endpoints:
- `mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion`
- `explorerzydxu5ecjrkwceayqybiz8qjcry3a7t7v2ppln5pmhpc3sj.onion` (Blockstream)

---

## 📊 Limitations

| Constraint | Value | Notes |
|------------|-------|-------|
| Max TX size | 2048 bytes | ~95% of standard transactions |
| Throughput | ~1 KB/10 sec | LoRa bandwidth limited |
| Range | 2-10+ km | Varies with terrain/antenna |

---

## 🛡️ Security Considerations

- **Sign transactions offline** before sending
- **Use hardware wallets** for key management
- **Enable Tor** on gateway for IP privacy
- **Verify transactions** before broadcasting

This system does NOT protect against:
- Compromised signing devices
- Physical radio triangulation
- Invalid/malformed transactions

---

## 🗺️ Roadmap

- [x] Basic chunked protocol
- [x] Client GUI
- [x] Gateway with Tor support
- [ ] Lightning Network support
- [ ] PSBT support
- [ ] Mobile app (Android)
- [ ] Satellite gateway (Blockstream)

---

## 🤝 Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## 📜 License

MIT License - see [LICENSE](LICENSE) file.

---

## 👤 Author

**Created for Bitcoiners by [Silexperience](https://github.com/Silexperience)**

*With the participation of [@ProfEduStream](https://github.com/ProfEduStream)* 🎓

---

## 💝 Support the Project

If this project helps you, consider supporting development:

**Bitcoin:** bc1qva34vcnefrlde23puratcdyg3gvyd0xq70kutw

**Lightning:** silexperience@getalby.com 

---

<div align="center">

### ₿ Bitcoin is Freedom. Mesh Makes it Unstoppable. ⚡

*"They can't stop the signal."*

</div>
