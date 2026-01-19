# 📡 Protocol Specification

## Overview

The Bitcoin Mesh Relay protocol enables transmission of Bitcoin transactions over LoRa mesh networks with limited bandwidth (typically 180 bytes per message).

## Protocol Design Goals

1. **Reliability** - Ensure complete transaction delivery
2. **Efficiency** - Minimize bandwidth usage
3. **Simplicity** - Easy to implement and debug
4. **Compatibility** - Works with standard Meshtastic infrastructure

---

## Message Types

### TX_START (0x01)

Initiates a new transaction transfer.

```
Byte 0:     Message Type (0x01)
Byte 1:     Transaction ID (0-255)
Bytes 2-3:  Total Size (uint16 little-endian)
```

**Example:** `01 05 E8 03` = Start TX #5, size 1000 bytes

---

### TX_CHUNK (0x02)

Contains a portion of the transaction data.

```
Byte 0:     Message Type (0x02)
Byte 1:     Transaction ID (0-255)
Byte 2:     Chunk Index (0-255)
Bytes 3-N:  Chunk Data (1-180 bytes)
```

**Example:** `02 05 00 01000000...` = TX #5, chunk 0, data follows

---

### TX_END (0x03)

Signals that all chunks have been sent.

```
Byte 0:     Message Type (0x03)
Byte 1:     Transaction ID (0-255)
```

**Example:** `03 05` = End of TX #5

---

### TX_ACK (0x04)

Acknowledgment that transaction was successfully broadcast to Bitcoin network.

```
Byte 0:     Message Type (0x04)
Byte 1:     Transaction ID (0-255)
```

**Example:** `04 05` = ACK for TX #5

---

### TX_ERROR (0x05)

Error response indicating transaction processing failed.

```
Byte 0:     Message Type (0x05)
Byte 1:     Transaction ID (0-255)
Byte 2:     Error Code
```

**Error Codes:**

| Code | Name | Description |
|------|------|-------------|
| 1 | `ERR_TOO_LARGE` | Transaction exceeds 2048 byte limit |
| 2 | `ERR_TIMEOUT` | Timeout waiting for chunks (30s) |
| 3 | `ERR_INVALID` | Transaction incomplete or malformed |
| 4 | `ERR_BROADCAST_FAIL` | Failed to broadcast to Bitcoin network |

**Example:** `05 05 04` = Error for TX #5, broadcast failed

---

## Transaction Flow

### Successful Flow

```
Client                    Gateway                  Bitcoin Network
  │                          │                          │
  │ ──── TX_START ────────►  │                          │
  │ ──── TX_CHUNK (0) ────►  │                          │
  │ ──── TX_CHUNK (1) ────►  │                          │
  │ ──── TX_CHUNK (N) ────►  │                          │
  │ ──── TX_END ───────────► │                          │
  │                          │ ──── sendrawtransaction ─►│
  │                          │ ◄──── txid ──────────────│
  │ ◄──── TX_ACK ───────────│                          │
  │                          │                          │
```

### Error Flow (Timeout)

```
Client                    Gateway
  │                          │
  │ ──── TX_START ────────►  │
  │ ──── TX_CHUNK (0) ────►  │
  │         (packet lost)    │
  │                          │ (30 second timeout)
  │ ◄──── TX_ERROR (2) ──── │
  │                          │
```

---

## Chunking Algorithm

### Sender Side

```python
def send_transaction(tx_bytes, interface):
    tx_id = get_next_tx_id()
    tx_size = len(tx_bytes)
    
    # 1. Send TX_START
    start_msg = pack("<BBH", 0x01, tx_id, tx_size)
    interface.send(start_msg)
    
    # 2. Send chunks
    chunk_size = 180
    for i, offset in enumerate(range(0, tx_size, chunk_size)):
        chunk = tx_bytes[offset:offset + chunk_size]
        chunk_msg = pack("<BBB", 0x02, tx_id, i) + chunk
        interface.send(chunk_msg)
        time.sleep(0.3)  # Prevent network congestion
    
    # 3. Send TX_END
    end_msg = pack("<BB", 0x03, tx_id)
    interface.send(end_msg)
```

### Receiver Side

```python
pending_txs = {}

def on_receive(msg):
    msg_type = msg[0]
    tx_id = msg[1]
    
    if msg_type == 0x01:  # TX_START
        size = unpack("<H", msg[2:4])[0]
        pending_txs[tx_id] = {
            'size': size,
            'chunks': {},
            'start_time': time.time()
        }
        
    elif msg_type == 0x02:  # TX_CHUNK
        chunk_idx = msg[2]
        chunk_data = msg[3:]
        if tx_id in pending_txs:
            pending_txs[tx_id]['chunks'][chunk_idx] = chunk_data
            
    elif msg_type == 0x03:  # TX_END
        if tx_id in pending_txs:
            tx = reassemble(pending_txs[tx_id])
            broadcast_to_bitcoin(tx)
            send_ack(tx_id)
```

---

## Constraints & Limitations

| Parameter | Value | Reason |
|-----------|-------|--------|
| Max TX size | 2048 bytes | Memory constraints on ESP32 |
| Chunk size | 180 bytes | Meshtastic message limit |
| Timeout | 30 seconds | Prevent resource exhaustion |
| TX ID range | 0-255 | Single byte for efficiency |
| Max concurrent TXs | ~10 | Memory constraints |

---

## LoRa Considerations

### Transmission Time

At typical LoRa settings (SF12, 125kHz):
- 180 byte message ≈ 1.5 seconds airtime
- 1000 byte transaction ≈ 6 chunks ≈ 9 seconds total
- 2048 byte transaction ≈ 12 chunks ≈ 18 seconds total

### Duty Cycle

Most regions have 1% duty cycle limits:
- With 1.5s per message, wait 150s between messages
- In practice, mesh networking distributes the load

### Range vs Speed Tradeoff

| Setting | Range | Airtime | Use Case |
|---------|-------|---------|----------|
| SF7 | Short | Fast | Urban, many nodes |
| SF10 | Medium | Medium | Suburban |
| SF12 | Long | Slow | Rural, emergency |

---

## Security Considerations

1. **No encryption at protocol level** - Bitcoin transactions are already signed
2. **Replay protection** - TX IDs cycle, stale transactions rejected
3. **DoS protection** - Size limits, timeouts, rate limiting possible
4. **Privacy** - Use Tor at gateway for IP privacy

---

## Future Improvements

- [ ] Message acknowledgment at chunk level
- [ ] Forward error correction
- [ ] Transaction priority levels
- [ ] Compression for larger transactions
- [ ] Multi-gateway redundancy
