# 🔧 Setup Guide

Complete step-by-step guide to set up your Bitcoin Mesh Relay network.

---

## 📋 Prerequisites

### Hardware (per node)

- ✅ LILYGO T-Beam v1.2 (with SX1262 LoRa chip)
- ✅ 18650 Li-Ion battery (3.7V, 2600mAh+ recommended)
- ✅ Antenna for your frequency band (868MHz EU / 915MHz US)
- ✅ USB-C cable for flashing and power

### Software

- ✅ [Visual Studio Code](https://code.visualstudio.com/)
- ✅ [PlatformIO Extension](https://platformio.org/install/ide?install=vscode)
- ✅ [Python 3.10+](https://python.org)
- ✅ [Git](https://git-scm.com/)

---

## Part 1: Flash the Firmware

### Step 1.1: Clone Repositories

```bash
# Create project directory
mkdir BitcoinMesh && cd BitcoinMesh

# Clone this project
git clone https://github.com/Silexperience/BitcoinMeshRelay.git

# Clone Meshtastic firmware
git clone https://github.com/meshtastic/firmware.git meshtastic-firmware
cd meshtastic-firmware
git checkout v2.7.18  # Use a stable version
```

### Step 1.2: Install BitcoinTxModule

```bash
# Copy the module files
cp ../BitcoinMeshRelay/src/firmware/BitcoinTxModule.h src/modules/
cp ../BitcoinMeshRelay/src/firmware/BitcoinTxModule.cpp src/modules/
```

### Step 1.3: Register the Module

Edit `src/modules/Modules.cpp`:

```cpp
// Add at the top with other includes
#include "BitcoinTxModule.h"

// Add in setupModules() function, near other module instantiations
new BitcoinTxModule();
```

### Step 1.4: Connect T-Beam

1. Insert 18650 battery into holder (observe polarity!)
2. Connect T-Beam via USB-C
3. Note the COM port (Windows) or /dev/ttyUSB0 (Linux)

### Step 1.5: Build and Flash

```bash
# Build for T-Beam v1.2
pio run -e tbeam

# Flash the firmware (replace COM83 with your port)
pio run -e tbeam -t upload --upload-port COM83
```

**Expected output:**
```
Writing at 0x00010000... (1 %)
...
Writing at 0x00239928... (100 %)
Wrote 2290880 bytes...
Hash of data verified.
Hard resetting via RTS pin...
[SUCCESS]
```

### Step 1.6: Verify Installation

```bash
# Open serial monitor
pio device monitor -e tbeam

# You should see Meshtastic boot messages
# Look for: "BitcoinTxModule initialized"
```

---

## Part 2: Set Up Gateway Computer

The gateway computer bridges the mesh network to the Internet.

### Step 2.1: Install Python Dependencies

```bash
pip install meshtastic pypubsub requests pyserial pysocks
```

### Step 2.2: Test Meshtastic Connection

```python
# Quick test
import meshtastic
import meshtastic.serial_interface

interface = meshtastic.serial_interface.SerialInterface("COM83")
print(interface.getMyNodeInfo())
interface.close()
```

### Step 2.3: Run Gateway Software

```bash
cd BitcoinMeshRelay/src/gateway
python bitcoin_mesh_gateway.py
```

### Step 2.4: Configure Gateway

1. **Select COM Port** - Choose your T-Beam's port
2. **Click Connect** - Status should turn green
3. **Select Bitcoin API**:
   - `Mempool.space` - Easy, public (default)
   - `Blockstream` - Alternative public API
   - `Bitcoin Core` - Your own node (best privacy)
4. **Optional: Enable Tor** - For anonymity
5. **Click "Test Connection"** - Verify Bitcoin network access

---

## Part 3: Set Up Client

The client is used in the censored/offline area.

### Step 3.1: Flash Another T-Beam

Repeat Part 1 to flash another T-Beam with the same firmware.

### Step 3.2: Run Client Software

```bash
cd BitcoinMeshRelay/src/client
python bitcoin_mesh_gui.py
```

### Step 3.3: Connect and Send

1. **Select COM Port** - Your client T-Beam
2. **Click Connect**
3. **Paste a Bitcoin transaction** (hex format)
4. **Click "Send to Mesh"**
5. **Watch the log** for ACK confirmation

---

## Part 4: Network Configuration

### Basic Setup (2 nodes)

```
📱 Client ──USB──► 📡 T-Beam ◄──LoRa──► 📡 Gateway ──USB──► 💻 Internet
```

### Extended Setup (with relays)

For longer range, add relay nodes:

```
Client ◄──► Relay1 ◄──► Relay2 ◄──► Gateway ──► Internet
```

Relay nodes just need the standard Meshtastic firmware with BitcoinTxModule.

### Channel Configuration

All nodes must be on the same Meshtastic channel:

```bash
# Using Meshtastic CLI
meshtastic --port COM83 --ch-set name "BTCMesh" --ch-index 0
meshtastic --port COM83 --ch-set psk random --ch-index 0
```

Save the PSK and apply to all nodes!

---

## Part 5: Testing

### Test 1: Mesh Connectivity

1. Power both T-Beams
2. They should discover each other within 30 seconds
3. Check with Meshtastic app or CLI

### Test 2: End-to-End (Testnet)

1. Generate a testnet transaction (use Electrum in testnet mode)
2. Send via Bitcoin Mesh Client
3. Verify on [mempool.space/testnet](https://mempool.space/testnet)

### Test 3: Range Test

1. Keep gateway running
2. Walk away with client T-Beam
3. Send transactions at various distances
4. Note where signal drops

---

## 🚨 Troubleshooting

### "Connection failed"

- Check USB cable (use data cable, not charge-only)
- Try different USB port
- Install CP210x drivers if needed

### "No ACK received"

- Verify both nodes on same channel
- Check antenna connections
- Reduce distance for testing

### "Bitcoin broadcast failed"

- Check Internet connection
- Try different API (Mempool vs Blockstream)
- If using Tor, verify Tor is running

### Flash size exceeded

- The T-Beam has 4MB flash
- Meshtastic + BitcoinTxModule uses ~94%
- This is normal, no action needed

---

## ✅ Checklist

- [ ] T-Beam v1.2 with battery
- [ ] Firmware flashed successfully
- [ ] BitcoinTxModule appears in boot log
- [ ] Gateway connected and online
- [ ] Bitcoin API test successful
- [ ] Client connected
- [ ] Test transaction sent and confirmed

---

## 📚 Next Steps

- Read [PROTOCOL.md](PROTOCOL.md) for technical details
- Check [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for common issues
- Join the community for support
