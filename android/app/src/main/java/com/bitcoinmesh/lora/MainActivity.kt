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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class MainActivity : AppCompatActivity() {

    // Meshtastic BLE UUIDs
    companion object {
        val MESHTASTIC_SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val TORADIO_UUID: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val FROMRADIO_UUID: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        const val MAX_CHUNK_SIZE = 190
        const val REQUEST_PERMISSIONS = 1001
    }

    // UI Elements
    private lateinit var statusText: TextView
    private lateinit var txInput: EditText
    private lateinit var sendButton: Button
    private lateinit var scanButton: Button
    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var charCount: TextView
    private lateinit var chunkCount: TextView
    private lateinit var connectionIndicator: View
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

    // Animations
    private var pulseAnimator1: ObjectAnimator? = null
    private var pulseAnimator2: ObjectAnimator? = null
    private var glowAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initBluetooth()
        setupListeners()
        startPulseAnimations()
        checkPermissions()

        log("⚡ BITCOIN MESH v2.0 NEON")
        log("🔗 LoRa Transaction Relay System")
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        txInput = findViewById(R.id.txInput)
        sendButton = findViewById(R.id.sendButton)
        scanButton = findViewById(R.id.scanButton)
        logView = findViewById(R.id.logView)
        scrollView = findViewById(R.id.scrollView)
        charCount = findViewById(R.id.charCount)
        chunkCount = findViewById(R.id.chunkCount)
        connectionIndicator = findViewById(R.id.connectionIndicator)
        pulseRing1 = findViewById(R.id.pulseRing1)
        pulseRing2 = findViewById(R.id.pulseRing2)
        progressBar = findViewById(R.id.progressBar)

        sendButton.isEnabled = false
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

        sendButton.setOnClickListener {
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
        pulseAnimator1 = ObjectAnimator.ofFloat(pulseRing1, "alpha", 0.8f, 0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(pulseRing1, "scaleX", 0.5f, 1.5f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(pulseRing1, "scaleY", 0.5f, 1.5f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        handler.postDelayed({
            pulseAnimator2 = ObjectAnimator.ofFloat(pulseRing2, "alpha", 0.6f, 0f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }

            ObjectAnimator.ofFloat(pulseRing2, "scaleX", 0.5f, 1.5f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }

            ObjectAnimator.ofFloat(pulseRing2, "scaleY", 0.5f, 1.5f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
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
        log("🔍 Scanning for Meshtastic devices...")

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: "Unknown"

                if (name.contains("Meshtastic", ignoreCase = true) ||
                    result.scanRecord?.serviceUuids?.any { it.uuid == MESHTASTIC_SERVICE_UUID } == true) {

                    log("📡 Found: $name")
                    stopScan(scanner, this)
                    connectToDevice(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                log("❌ Scan failed: $errorCode")
                isScanning = false
                scanButton.text = "CONNECT"
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)

            handler.postDelayed({
                if (isScanning) {
                    stopScan(scanner, scanCallback)
                    log("⏱️ Scan timeout - no device found")
                }
            }, 15000)
        } catch (e: SecurityException) {
            log("❌ Permission denied for scanning")
            isScanning = false
        }
    }

    private fun stopScan(scanner: BluetoothLeScanner, callback: ScanCallback) {
        try {
            scanner.stopScan(callback)
        } catch (e: SecurityException) {
            // Ignore
        }
        isScanning = false
        runOnUiThread {
            scanButton.text = if (isConnected) "DISCONNECT" else "CONNECT"
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        log("🔗 Connecting to ${device.name ?: device.address}...")

        try {
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            log("❌ Permission denied for connection")
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
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        runOnUiThread { log("❌ Permission denied") }
                    }
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
                    if (toRadioCharacteristic != null) {
                        runOnUiThread {
                            log("📻 Meshtastic service ready")
                            sendButton.isEnabled = true
                        }
                    } else {
                        runOnUiThread { log("❌ ToRadio characteristic not found") }
                    }
                } else {
                    runOnUiThread { log("❌ Meshtastic service not found") }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    log("📤 Chunk sent successfully")
                } else {
                    log("❌ Write failed: $status")
                }
            }
        }
    }

    private fun updateConnectionUI(connected: Boolean) {
        if (connected) {
            connectionIndicator.setBackgroundResource(R.drawable.indicator_connected)
            statusText.text = "MESH CONNECTED"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
            scanButton.text = "DISCONNECT"
            sendButton.isEnabled = true
        } else {
            connectionIndicator.setBackgroundResource(R.drawable.indicator_disconnected)
            statusText.text = "OFFLINE"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.neon_red))
            scanButton.text = "CONNECT"
            sendButton.isEnabled = false
            toRadioCharacteristic = null
        }
    }

    private fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            // Ignore
        }
        bluetoothGatt = null
        isConnected = false
        updateConnectionUI(false)
        log("🔌 Disconnected from mesh")
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
            log("❌ Not connected to mesh")
            return
        }

        val chunks = txHex.chunked(MAX_CHUNK_SIZE)
        val totalChunks = chunks.size

        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        log("🚀 BROADCASTING TRANSACTION")
        log("📊 Size: ${txHex.length} bytes")
        log("📦 Chunks: $totalChunks packets")
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        progressBar.visibility = View.VISIBLE
        progressBar.max = totalChunks
        progressBar.progress = 0
        sendButton.isEnabled = false

        Thread {
            chunks.forEachIndexed { index, chunk ->
                val message = "BTX:${index + 1}/$totalChunks:$chunk"
                val sent = sendMeshMessage(message)

                runOnUiThread {
                    if (sent) {
                        log("📡 [${index + 1}/$totalChunks] Transmitted")
                    } else {
                        log("❌ [${index + 1}/$totalChunks] Failed")
                    }
                    progressBar.progress = index + 1
                }

                Thread.sleep(500)
            }

            runOnUiThread {
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                log("✅ BROADCAST COMPLETE")
                log("⚡ TX relayed to LoRa mesh!")
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                progressBar.visibility = View.GONE
                sendButton.isEnabled = true
                flashSuccess()
            }
        }.start()
    }

    private fun sendMeshMessage(message: String): Boolean {
        val characteristic = toRadioCharacteristic ?: return false
        val gatt = bluetoothGatt ?: return false

        try {
            val toRadioBytes = buildToRadioPacket(message)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, toRadioBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = toRadioBytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
            return true
        } catch (e: SecurityException) {
            runOnUiThread { log("❌ Permission denied for write") }
            return false
        } catch (e: Exception) {
            runOnUiThread { log("❌ Error: ${e.message}") }
            return false
        }
    }

    private fun buildToRadioPacket(message: String): ByteArray {
        // Simplified protobuf-like packet for TEXT_MESSAGE
        // Field 1: packet (wire type 2 = length-delimited)
        // Nested: to=0xFFFFFFFF (broadcast), decoded.portnum=1 (TEXT_MESSAGE), decoded.payload

        val payload = message.toByteArray(Charsets.UTF_8)

        // Build inner decoded message
        val decoded = ByteArrayOutputStream()
        // portnum = 1 (TEXT_MESSAGE_APP)
        decoded.write(0x08) // Field 1, varint
        decoded.write(0x01) // Value 1
        // payload
        decoded.write(0x12) // Field 2, length-delimited
        writeVarint(decoded, payload.size)
        decoded.write(payload)

        val decodedBytes = decoded.toByteArray()

        // Build MeshPacket
        val meshPacket = ByteArrayOutputStream()
        // to = broadcast (0xFFFFFFFF)
        meshPacket.write(0x10) // Field 2, varint
        writeVarint(meshPacket, 0xFFFFFFFF.toInt())
        // decoded (field 6)
        meshPacket.write(0x32) // Field 6, length-delimited
        writeVarint(meshPacket, decodedBytes.size)
        meshPacket.write(decodedBytes)

        val meshPacketBytes = meshPacket.toByteArray()

        // Build ToRadio
        val toRadio = ByteArrayOutputStream()
        // packet (field 1)
        toRadio.write(0x0A) // Field 1, length-delimited
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

    private fun flashSuccess() {
        val originalColor = ContextCompat.getColor(this, R.color.neon_orange)
        val successColor = ContextCompat.getColor(this, R.color.neon_green)

        ValueAnimator.ofArgb(successColor, originalColor).apply {
            duration = 1500
            addUpdateListener { animator ->
                sendButton.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun log(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            logView.append("[$timestamp] $message\n")
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnimator1?.cancel()
        pulseAnimator2?.cancel()
        glowAnimator?.cancel()
        disconnect()
    }
}

class ByteArrayOutputStream : java.io.ByteArrayOutputStream()
