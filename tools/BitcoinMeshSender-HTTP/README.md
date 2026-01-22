# Bitcoin Mesh Sender (HTTP Version)

A simple Android app that sends Bitcoin transactions to the Bitcoin Mesh Gateway via HTTP API.

## Why HTTP instead of BLE?

The original BLE approach required manually encoding Meshtastic protobuf messages, which is complex and error-prone. This HTTP version:

1. **Simpler**: Just HTTP POST requests with JSON
2. **More reliable**: No complex protobuf encoding
3. **Easier to debug**: Standard HTTP tools work
4. **Same functionality**: Gateway still broadcasts to LoRa mesh

## Architecture

```
[Android App] --HTTP--> [Gateway PC:8088] --LoRa--> [Mesh Network]
                              |
                              v
                        [Bitcoin Network]
```

## Setup

1. Run the gateway on your PC with the T-Beam connected
2. Note your PC's local IP address (e.g., 192.168.1.100)
3. Install the app on your Android device
4. Enter the gateway IP in the app
5. Test connection
6. Paste transaction hex and send

## Building

```bash
cd tools/BitcoinMeshSender-HTTP
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## API Endpoints

The gateway exposes:

- `GET /api/status` - Check gateway status
- `POST /api/tx` - Send complete transaction (body: `{"tx_hex": "..."}`)
- `POST /api/chunk` - Send chunked transaction (body: `{"tx_id": "...", "index": 1, "total": 3, "data": "..."}`)
