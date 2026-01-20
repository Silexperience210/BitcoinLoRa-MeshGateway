package com.bitcoinmesh.lora

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        val MESHTASTIC_SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val TORADIO_UUID: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        const val MAX_CHUNK_SIZE = 190
        const val REQUEST_PERMISSIONS = 1001
    }

    // UI Elements
    private lateinit var statusText: TextView
    private lateinit var txInput: EditText
    private lateinit var broadcastButton: Button
    private lateinit var scanButton: Button
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var charCount: TextView
    private lateinit var chunkCount: TextView
    private lateinit var connectionDot: View
    private lateinit var pulseRing1: View
    private lateinit var pulseRing2: View
    private lateinit var progressBar: ProgressBar

    // Bluetooth
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var toRadioCharacteristic: BluetoothGattCharacteristic? = null
    private var isConnected = false
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private var writeQueue = mutableListOf<ByteArray>()
    private var isWriting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initBluetooth()
        setupListeners()
        startPulseAnimations()
        checkPermissions()

        log("⚡ BITCOIN MESH v2.1")
        log("🔗 LoRa Transaction Relay")
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        txInput = findViewById(R.id.txInput)
        broadcastButton = findViewById(R.id.broadcastButton)
        scanButton = findViewById(R.id.scanButton)
        logText = findViewById(R.id.logText)
        logScroll = findViewById(R.id.logScroll)
        charCount = findViewById(R.id.charCount)
        chunkCount = findViewById(R.id.chunkCount)
        connectionDot = findViewById(R.id.connectionDot)
        pulseRing1 = findViewById(R.id.pulseRing1)
        pulseRing2 = findViewById(R.id.pulseRing2)
        progressBar = findViewById(R.id.progressBar)

        broadcastButton.isEnabled = false
        progressBar.visibility = View.GONE
    }

    private fun initBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            log("❌ Bluetooth not available")
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            log("⚠️ Please enable Bluetooth")
        }
    }

    private fun setupListeners() {
        scanButton.setOnClickListener {
            if (isConnected) {
                disconnect()
            } else {
                startScan()
            }
        }

        broadcastButton.setOnClickListener {
            val txHex = txInput.text.toString().trim()
            if (txHex.isNotEmpty()) {
                sendTransaction(txHex)
            }
        }

        txInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCounters()
            }
        })
    }

    private fun startPulseAnimations() {
        ObjectAnimator.ofFloat(pulseRing1, "alpha", 0.8f, 0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(pulseRing1, "scaleX", 0.5f, 1.5f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        ObjectAnimator.ofFloat(pulseRing1, "scaleY", 0.5f, 1.5f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        handler.postDelayed({
            ObjectAnimator.ofFloat(pulseRing2, "alpha", 0.6f, 0f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                start()
            }
            ObjectAnimator.ofFloat(pulseRing2, "scaleX", 0.5f, 1.5f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                start()
            }
            ObjectAnimator.ofFloat(pulseRing2, "scaleY", 0.5f, 1.5f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }, 1000)
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun startScan() {
        if (isScanning) return

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            log("❌ BLE Scanner not available")
            return
        }

        isScanning = true
        scanButton.text = "SCANNING..."
        log("🔍 Scanning for Meshtastic...")

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: "Unknown"

                if (name.contains("Meshtastic", ignoreCase = true)) {
                    log("📡 Found: $name")
                    stopScan(scanner, this)
                    connectToDevice(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                log("❌ Scan failed: $errorCode")
                isScanning = false
                scanButton.text = "⟳ SCAN"
            }
        }

        try {
            scanner.startScan(null, ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(), scanCallback)

            handler.postDelayed({
                if (isScanning) {
                    stopScan(scanner, scanCallback)
                    log("⏱️ Scan timeout")
                }
            }, 15000)
        } catch (e: SecurityException) {
            log("❌ Permission denied")
            isScanning = false
        }
    }

    private fun stopScan(scanner: BluetoothLeScanner, callback: ScanCallback) {
        try { scanner.stopScan(callback) } catch (e: SecurityException) {}
        isScanning = false
        runOnUiThread { scanButton.text = if (isConnected) "DISCONNECT" else "⟳ SCAN" }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        log("🔗 Connecting to ${device.name ?: device.address}...")
        try {
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            log("❌ Connection permission denied")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    runOnUiThread {
                        log("✅ Connected!")
                        isConnected = true
                        updateConnectionUI(true)
                    }
                    try { gatt.discoverServices() } catch (e: SecurityException) {}
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    runOnUiThread {
                        log("🔌 Disconnected")
                        isConnected = false
                        updateConnectionUI(false)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(MESHTASTIC_SERVICE_UUID)
                if (service != null) {
                    toRadioCharacteristic = service.getCharacteristic(TORADIO_UUID)
                    runOnUiThread {
                        if (toRadioCharacteristic != null) {
                            log("📻 Meshtastic ready!")
                            broadcastButton.isEnabled = true
                        } else {
                            log("❌ ToRadio not found")
                        }
                    }
                } else {
                    runOnUiThread { log("❌ Meshtastic service not found") }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log("📤 Packet sent OK")
                } else {
                    log("❌ Write failed: $status")
                }
            }
            // Process next in queue
            isWriting = false
            processWriteQueue()
        }
    }

    private fun updateConnectionUI(connected: Boolean) {
        if (connected) {
            connectionDot.setBackgroundResource(R.drawable.indicator_connected)
            statusText.text = "MESH CONNECTED"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
            scanButton.text = "DISCONNECT"
            broadcastButton.isEnabled = true
        } else {
            connectionDot.setBackgroundResource(R.drawable.indicator_disconnected)
            statusText.text = "OFFLINE"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.neon_red))
            scanButton.text = "⟳ SCAN"
            broadcastButton.isEnabled = false
            toRadioCharacteristic = null
        }
    }

    private fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {}
        bluetoothGatt = null
        isConnected = false
        updateConnectionUI(false)
        log("🔌 Disconnected")
    }

    private fun updateCounters() {
        val text = txInput.text.toString()
        val bytes = text.length
        val numChunks = if (bytes > 0) ((bytes - 1) / MAX_CHUNK_SIZE) + 1 else 0
        charCount.text = "$bytes BYTES"
        chunkCount.text = "$numChunks PACKETS"
    }

    private fun sendTransaction(txHex: String) {
        if (!isConnected || toRadioCharacteristic == null) {
            log("❌ Not connected")
            return
        }

        val chunks = txHex.chunked(MAX_CHUNK_SIZE)
        val totalChunks = chunks.size

        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        log("🚀 BROADCASTING TX")
        log("📊 Size: ${txHex.length} bytes")
        log("📦 Packets: $totalChunks")
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        progressBar.visibility = View.VISIBLE
        progressBar.max = totalChunks
        progressBar.progress = 0
        broadcastButton.isEnabled = false

        // Queue all messages
        writeQueue.clear()
        chunks.forEachIndexed { index, chunk ->
            val message = "BTX:${index + 1}/$totalChunks:$chunk"
            val packet = buildToRadioPacket(message)
            writeQueue.add(packet)
        }

        // Start sending
        Thread {
            var sentCount = 0
            chunks.forEachIndexed { index, _ ->
                runOnUiThread { log("📡 [${index + 1}/$totalChunks] Sending...") }
                
                // Send packet
                val packet = writeQueue[index]
                sendPacket(packet)
                
                // Wait for BLE write to complete
                Thread.sleep(800)
                
                sentCount++
                runOnUiThread { progressBar.progress = sentCount }
            }

            runOnUiThread {
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                log("✅ BROADCAST COMPLETE!")
                log("⚡ TX sent to LoRa mesh")
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                progressBar.visibility = View.GONE
                broadcastButton.isEnabled = true
            }
        }.start()
    }

    private fun sendPacket(data: ByteArray) {
        val characteristic = toRadioCharacteristic ?: return
        val gatt = bluetoothGatt ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        } catch (e: SecurityException) {
            runOnUiThread { log("❌ Write permission denied") }
        }
    }

    private fun processWriteQueue() {
        if (isWriting || writeQueue.isEmpty()) return
        isWriting = true
        val data = writeQueue.removeAt(0)
        sendPacket(data)
    }

    /**
     * Build a proper Meshtastic ToRadio protobuf packet
     * 
     * ToRadio {
     *   packet: MeshPacket {  // field 1
     *     to: 0xFFFFFFFF     // field 2 - broadcast
     *     channel: 0         // field 3 - primary channel
     *     decoded: Data {    // field 5 (NOT 6!)
     *       portnum: 1       // TEXT_MESSAGE_APP
     *       payload: bytes
     *     }
     *     want_ack: true     // field 6
     *     hop_limit: 3       // field 11
     *   }
     * }
     */
    private fun buildToRadioPacket(message: String): ByteArray {
        val payload = message.toByteArray(Charsets.UTF_8)

        // Build Data message
        val data = ByteArrayOutputStream()
        // portnum = 1 (TEXT_MESSAGE_APP) - field 1, varint
        data.write(0x08)  // (1 << 3) | 0 = 0x08
        data.write(0x01)  // value = 1
        // payload - field 2, length-delimited
        data.write(0x12)  // (2 << 3) | 2 = 0x12
        writeVarint(data, payload.size)
        data.write(payload)
        val dataBytes = data.toByteArray()

        // Build MeshPacket
        val meshPacket = ByteArrayOutputStream()
        
        // to = 0xFFFFFFFF (broadcast) - field 2, varint
        meshPacket.write(0x10)  // (2 << 3) | 0 = 0x10
        // Write 0xFFFFFFFF as unsigned varint (5 bytes for 32-bit max)
        meshPacket.write(0xFF)
        meshPacket.write(0xFF)
        meshPacket.write(0xFF)
        meshPacket.write(0xFF)
        meshPacket.write(0x0F)
        
        // channel = 0 - field 3, varint (optional, 0 is default)
        meshPacket.write(0x18)  // (3 << 3) | 0 = 0x18
        meshPacket.write(0x00)
        
        // decoded = Data - field 5, length-delimited (NOT field 6!)
        meshPacket.write(0x2A)  // (5 << 3) | 2 = 0x2A
        writeVarint(meshPacket, dataBytes.size)
        meshPacket.write(dataBytes)
        
        // want_ack = true - field 6, varint
        meshPacket.write(0x30)  // (6 << 3) | 0 = 0x30
        meshPacket.write(0x01)  // true
        
        // hop_limit = 3 - field 11, varint
        meshPacket.write(0x58)  // (11 << 3) | 0 = 0x58
        meshPacket.write(0x03)  // value = 3
        
        val meshPacketBytes = meshPacket.toByteArray()

        // Build ToRadio
        val toRadio = ByteArrayOutputStream()
        // packet - field 1, length-delimited
        toRadio.write(0x0A)  // (1 << 3) | 2 = 0x0A
        writeVarint(toRadio, meshPacketBytes.size)
        toRadio.write(meshPacketBytes)

        return toRadio.toByteArray()
    }

    private fun writeVarint(stream: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            stream.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        stream.write(v and 0x7F)
    }

    private fun log(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            logText.append("[$timestamp] $message\n")
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}

class ByteArrayOutputStream : java.io.ByteArrayOutputStream()
