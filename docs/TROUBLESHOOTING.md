# 🔍 Troubleshooting Guide

Common issues and their solutions.

---

## 🔌 Connection Issues

### USB Not Recognized

**Symptoms:**
- No COM port appears when connecting T-Beam
- Device Manager shows "Unknown Device"

**Solutions:**
1. **Install CP210x driver**: [Silicon Labs CP210x Driver](https://www.silabs.com/developers/usb-to-uart-bridge-vcp-drivers)
2. **Try a different USB cable** (some are charge-only)
3. **Try a different USB port** (USB 2.0 often works better)
4. **Restart your computer**

---

### "Cannot open port" Error

**Symptoms:**
- `serial.serialutil.SerialException: could not open port`

**Solutions:**
1. **Close other applications** using the port (Arduino IDE, Meshtastic app, etc.)
2. **Check if correct port** is selected
3. **Unplug and replug** the device
4. **Run as Administrator** (Windows)

---

## 📡 Mesh Network Issues

### Nodes Not Discovering Each Other

**Symptoms:**
- No other nodes visible in Meshtastic app
- Messages not being relayed

**Solutions:**
1. **Verify same channel settings**:
   ```bash
   meshtastic --port COM83 --info
   ```
   Compare channel name and PSK on both devices

2. **Check antenna connection** - Transmitting without antenna can damage the radio!

3. **Verify frequency region**:
   ```bash
   meshtastic --port COM83 --set lora.region EU_868  # or US, etc.
   ```

4. **Reduce distance** for initial testing (start at 10 meters)

5. **Check battery level** - Low battery affects transmission

---

### "BitcoinTxModule not found" in Logs

**Symptoms:**
- Module doesn't initialize
- No "BitcoinTx:" messages in serial output

**Solutions:**
1. **Verify files are copied correctly**:
   ```
   src/modules/BitcoinTxModule.h
   src/modules/BitcoinTxModule.cpp
   ```

2. **Check Modules.cpp modification**:
   - Include statement at top
   - `new BitcoinTxModule();` in setupModules()

3. **Rebuild completely**:
   ```bash
   pio run -e tbeam -t clean
   pio run -e tbeam
   ```

---

## 💸 Transaction Issues

### "Transaction too large" Error

**Symptoms:**
- Error code 1 received
- Large transactions rejected

**Solutions:**
1. **Optimize transaction size**:
   - Use single-input transactions when possible
   - Consolidate UTXOs beforehand
   - Use Native SegWit (bc1q...) addresses

2. **Current limit is 2048 bytes** - Most simple transactions are under 500 bytes

---

### "Timeout" Error

**Symptoms:**
- Error code 2 received
- Transaction starts but never completes

**Solutions:**
1. **Check signal strength** between nodes
2. **Reduce distance** or add relay nodes
3. **Avoid obstacles** - LoRa penetrates walls but loses range
4. **Check for interference** - Other 868/915 MHz devices

---

### "Broadcast Failed" Error

**Symptoms:**
- Error code 4 received
- Transaction reached gateway but not Bitcoin network

**Solutions:**
1. **Check Internet connection** on gateway
2. **Test API manually**:
   ```bash
   curl -X POST -d "YOUR_TX_HEX" https://mempool.space/api/tx
   ```
3. **Verify transaction is valid**:
   - Sufficient fees
   - Valid signatures
   - Inputs not already spent
4. **Try different API** (switch Mempool → Blockstream)
5. **If using Tor**, verify it's running:
   ```bash
   curl --socks5 127.0.0.1:9050 https://check.torproject.org/api/ip
   ```

---

## 🔧 Build Issues

### "Flash size exceeded"

**Symptoms:**
- Build fails with memory error
- `.pio/build/tbeam/firmware.bin` too large

**Solutions:**
1. **This is expected** - T-Beam has 4MB flash, Meshtastic uses most of it
2. If still failing, **disable unused features** in `platformio.ini`:
   ```ini
   build_flags = 
     -DMESHTASTIC_EXCLUDE_CANNEDMESSAGES=1
     -DMESHTASTIC_EXCLUDE_ATAK=1
   ```

---

### "Wrong chip" Error During Flash

**Symptoms:**
- `This chip is ESP32-S3, not ESP32. Wrong --chip argument?`
- Or vice versa

**Solutions:**
1. **Identify your board version**:
   - T-Beam v1.2 = ESP32 (not S3)
   - T-Beam Supreme = ESP32-S3
   
2. **Use correct environment**:
   ```bash
   # For T-Beam v1.2 (standard)
   pio run -e tbeam -t upload
   
   # For T-Beam Supreme (S3)
   pio run -e tbeam-s3-core -t upload
   ```

---

## 🧅 Tor Issues

### "Tor connection failed"

**Symptoms:**
- Tor checkbox enables but test fails
- "SOCKS error" messages

**Solutions:**
1. **Verify Tor is running**:
   - Windows: Check "Tor Browser" or Tor service
   - Linux: `systemctl status tor`

2. **Check SOCKS port**:
   - Tor Browser uses port **9150**
   - Tor daemon uses port **9050**
   - Update the port in the GUI accordingly

3. **Test Tor manually**:
   ```bash
   curl --socks5 127.0.0.1:9050 https://check.torproject.org/api/ip
   ```

4. **Install PySocks** if not present:
   ```bash
   pip install pysocks
   ```

---

## 📊 Performance Issues

### Slow Transaction Transmission

**Symptoms:**
- Transactions take very long
- Chunks timing out

**Solutions:**
1. **LoRa is inherently slow** - 1KB takes ~10 seconds
2. **Optimize LoRa settings** (tradeoff: faster = shorter range):
   ```bash
   meshtastic --set lora.bandwidth 250
   meshtastic --set lora.spread_factor 10
   ```

3. **Keep transactions small** (use SegWit, minimize inputs)

---

## 🆘 Getting Help

If you're still stuck:

1. **Check serial output**:
   ```bash
   pio device monitor -e tbeam
   ```

2. **Enable verbose logging** in firmware

3. **Open an issue** on GitHub with:
   - Your hardware version
   - Firmware version
   - Full error message
   - Steps to reproduce

---

## 📋 Quick Diagnostic Commands

```bash
# Check firmware version
meshtastic --port COM83 --info

# Check channel settings
meshtastic --port COM83 --ch-index 0 --info

# List nearby nodes
meshtastic --port COM83 --nodes

# Send test message
meshtastic --port COM83 --sendtext "test"

# Factory reset (last resort)
meshtastic --port COM83 --factory-reset
```
