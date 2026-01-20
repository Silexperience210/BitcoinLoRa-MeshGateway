package com.bitcoinmesh.lora

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.protobuf.ByteString
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BitcoinMesh"
        private const val CHUNK_SIZE = 190
        private const val CHUNK_DELAY_MS = 2500L
        private const val REQUEST_PERMISSIONS = 1
        
        // Meshtastic BLE UUIDs
        private val MESHTASTIC_SERVICE = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        private val TORADIO_UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        private val FROMRADIO_UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        private val FROMNUM_UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
    }
    
    // BLE
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var toRadioChar: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var isConnected = false
    private val devices = mutableListOf<BluetoothDevice>()
    
    // UI
    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    private lateinit var deviceSpinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var txInput: EditText
    private lateinit var charCount: TextView
    private lateinit var chunkCount: TextView
    private lateinit var pasteButton: Button
    private lateinit var clearButton: Button
    private lateinit var broadcastButton: Button
    private lateinit var progressContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var connectionDot: View
    private lateinit var pulseRing1: View
    private lateinit var pulseRing2: View
    private lateinit var titleText: TextView
    private lateinit var broadcastGlow: View
    
    private val handler = Handler(Looper.getMainLooper())
    private var chunks: List<String> = emptyList()
    private var currentChunk = 0
    private var myNodeNum: Int = 0
    
    // GATT Callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        log("⚡ BLE CONNECTED")
                        isConnected = true
                        updateConnectionUI(true)
                        try { gatt.discoverServices() } catch (e: SecurityException) {}
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        log("🔌 DISCONNECTED")
                        isConnected = false
                        updateConnectionUI(false)
                        bluetoothGatt = null
                    }
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(MESHTASTIC_SERVICE)
                    if (service != null) {
                        toRadioChar = service.getCharacteristic(TORADIO_UUID)
                        if (toRadioChar != null) {
                            log("✅ MESHTASTIC READY")
                            statusText.text = "⚡ MESH ONLINE"
                            broadcastButton.isEnabled = true
                            pulseSuccess()
                        } else {
                            log("❌ ToRadio not found")
                        }
                    } else {
                        log("❌ Meshtastic service not found")
                    }
                }
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    currentChunk++
                    updateProgress()
                    if (currentChunk < chunks.size) {
                        handler.postDelayed({ sendNextChunk() }, CHUNK_DELAY_MS)
                    } else {
                        onTransmitComplete()
                    }
                } else {
                    log("❌ WRITE FAILED")
                }
            }
        }
    }
    
    // Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                val device = result.device
                val name = device.name ?: return
                if (name.contains("Mesh", true) && devices.none { it.address == device.address }) {
                    devices.add(device)
                    updateDeviceSpinner()
                    log("📡 Found: $
ame")
                }
            } catch (e: SecurityException) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        startAnimations()
        checkPermissions()
    }
    
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        scanButton = findViewById(R.id.scanButton)
        deviceSpinner = findViewById(R.id.deviceSpinner)
        connectButton = findViewById(R.id.connectButton)
        txInput = findViewById(R.id.txInput)
        charCount = findViewById(R.id.charCount)
        chunkCount = findViewById(R.id.chunkCount)
        pasteButton = findViewById(R.id.pasteButton)
        clearButton = findViewById(R.id.clearButton)
        broadcastButton = findViewById(R.id.broadcastButton)
        progressContainer = findViewById(R.id.progressContainer)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        logText = findViewById(R.id.logText)
        logScroll = findViewById(R.id.logScroll)
        connectionDot = findViewById(R.id.connectionDot)
        pulseRing1 = findViewById(R.id.pulseRing1)
        pulseRing2 = findViewById(R.id.pulseRing2)
        titleText = findViewById(R.id.titleText)
        broadcastGlow = findViewById(R.id.broadcastGlow)
        
        scanButton.setOnClickListener { startScan() }
        connectButton.setOnClickListener { toggleConnect() }
        pasteButton.setOnClickListener { paste() }
        clearButton.setOnClickListener { clear() }
        broadcastButton.setOnClickListener { broadcast() }
        
        txInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateCounts() }
        })
        
        broadcastButton.isEnabled = false
        log("⚡ BITCOIN MESH v2.0")
        log("🔧 Initializing...")
    }
    
    private fun startAnimations() {
        // Pulse rings animation
        val pulse1 = ObjectAnimator.ofFloat(pulseRing1, "scaleX", 0.6f, 1.2f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val pulse1Y = ObjectAnimator.ofFloat(pulseRing1, "scaleY", 0.6f, 1.2f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val pulse1Alpha = ObjectAnimator.ofFloat(pulseRing1, "alpha", 0.8f, 0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
        }
        
        val pulse2 = ObjectAnimator.ofFloat(pulseRing2, "scaleX", 0.6f, 1.4f).apply {
            duration = 2000
            startDelay = 500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val pulse2Y = ObjectAnimator.ofFloat(pulseRing2, "scaleY", 0.6f, 1.4f).apply {
            duration = 2000
            startDelay = 500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val pulse2Alpha = ObjectAnimator.ofFloat(pulseRing2, "alpha", 0.6f, 0f).apply {
            duration = 2000
            startDelay = 500
            repeatCount = ValueAnimator.INFINITE
        }
        
        AnimatorSet().apply {
            playTogether(pulse1, pulse1Y, pulse1Alpha, pulse2, pulse2Y, pulse2Alpha)
            start()
        }
        
        // Title glow animation
        ValueAnimator.ofFloat(15f, 25f, 15f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                titleText.setShadowLayer(it.animatedValue as Float, 0f, 0f, 0xFFFF6B00.toInt())
            }
            start()
        }
        
        // Broadcast button glow pulse
        ValueAnimator.ofFloat(0.3f, 0.6f, 0.3f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                broadcastGlow.alpha = it.animatedValue as Float
            }
            start()
        }
    }
    
    private fun pulseSuccess() {
        ObjectAnimator.ofFloat(connectionDot, "scaleX", 1f, 1.5f, 1f).apply {
            duration = 300
            start()
        }
        ObjectAnimator.ofFloat(connectionDot, "scaleY", 1f, 1.5f, 1f).apply {
            duration = 300
            start()
        }
    }
    
    private fun updateConnectionUI(connected: Boolean) {
        connectionDot.setBackgroundResource(
            if (connected) R.drawable.status_dot_green else R.drawable.status_dot_red
        )
        connectButton.text = if (connected) "⚡ DISCONNECT" else "⚡ CONNECT TO MESH ⚡"
        statusText.text = if (connected) "⚡ CONNECTED" else "○ DISCONNECTED"
    }
    
    private fun updateCounts() {
        val text = txInput.text.toString().trim()
        val bytes = text.length / 2
        val numChunks = if (text.isEmpty()) 0 else (text.length + CHUNK_SIZE - 1) / CHUNK_SIZE
        charCount.text = "$ytes BYTES"
        chunkCount.text = "$
umChunks PACKETS"
    }
    
    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            initBluetooth()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQUEST_PERMISSIONS && results.all { it == PackageManager.PERMISSION_GRANTED }) {
            initBluetooth()
        } else {
            log("❌ Permissions denied")
        }
    }
    
    private fun initBluetooth() {
        val bm = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter
        if (bluetoothAdapter?.isEnabled == true) {
            log("✅ Bluetooth ready")
            statusText.text = "○ TAP SCAN TO FIND MESH"
        } else {
            log("⚠️ Enable Bluetooth")
        }
    }
    
    private fun startScan() {
        if (isScanning) return
        devices.clear()
        updateDeviceSpinner()
        
        try {
            val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
            scanner.startScan(scanCallback)
            isScanning = true
            log("🔍 Scanning...")
            statusText.text = "🔍 SCANNING..."
            scanButton.text = "⟳ ..."
            
            handler.postDelayed({
                try {
                    scanner.stopScan(scanCallback)
                } catch (e: SecurityException) {}
                isScanning = false
                scanButton.text = "⟳ SCAN"
                log("🔍 Found ${devices.size} devices")
                if (devices.isNotEmpty()) {
                    statusText.text = "✅ ${devices.size} MESH FOUND"
                }
            }, 8000)
        } catch (e: SecurityException) {
            log("❌ Scan permission denied")
        }
    }
    
    private fun updateDeviceSpinner() {
        try {
            val names = devices.map { it.name ?: it.address }
            deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        } catch (e: SecurityException) {}
    }
    
    private fun toggleConnect() {
        if (isConnected) {
            disconnect()
        } else {
            connect()
        }
    }
    
    private fun connect() {
        val pos = deviceSpinner.selectedItemPosition
        if (pos < 0 || pos >= devices.size) {
            log("❌ Select a device")
            return
        }
        try {
            log("🔄 Connecting...")
            statusText.text = "🔄 CONNECTING..."
            bluetoothGatt = devices[pos].connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            log("❌ Connect failed")
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
        broadcastButton.isEnabled = false
    }
    
    private fun paste() {
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        clip?.getItemAt(0)?.text?.let {
            txInput.setText(it)
            log("📋 Pasted ${it.length} chars")
            flashInput()
        }
    }
    
    private fun flashInput() {
        ObjectAnimator.ofFloat(txInput, "alpha", 1f, 0.5f, 1f).apply {
            duration = 200
            start()
        }
    }
    
    private fun clear() {
        txInput.setText("")
        log("🗑️ Cleared")
    }
    
    private fun broadcast() {
        val tx = txInput.text.toString().trim()
        if (tx.isEmpty()) {
            log("❌ Empty TX")
            return
        }
        if (!isConnected || toRadioChar == null) {
            log("❌ Not connected")
            return
        }
        
        // Generate chunks
        chunks = tx.chunked(CHUNK_SIZE).mapIndexed { i, chunk ->
            "BTX:${i + 1}/${(tx.length + CHUNK_SIZE - 1) / CHUNK_SIZE}:$chunk"
        }
        currentChunk = 0
        
        log("📦 ${tx.length} chars → ${chunks.size} packets")
        log("🚀 BROADCASTING TO MESH...")
        
        progressContainer.visibility = View.VISIBLE
        progressBar.max = chunks.size
        progressBar.progress = 0
        broadcastButton.isEnabled = false
        broadcastButton.text = "⚡ TRANSMITTING..."
        
        // Intense glow animation
        ValueAnimator.ofFloat(0.4f, 1f).apply {
            duration = 500
            repeatCount = chunks.size * 2
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { broadcastGlow.alpha = it.animatedValue as Float }
            start()
        }
        
        sendNextChunk()
    }
    
    private fun sendNextChunk() {
        if (currentChunk >= chunks.size) return
        
        val chunk = chunks[currentChunk]
        val char = toRadioChar ?: return
        val gatt = bluetoothGatt ?: return
        
        try {
            // Build protobuf message
            val meshPacket = buildMeshPacket(chunk)
            char.value = meshPacket
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            if (gatt.writeCharacteristic(char)) {
                log("📤 TX ${currentChunk + 1}/${chunks.size}")
            } else {
                log("❌ Write failed")
            }
        } catch (e: Exception) {
            log("❌ Error: ${e.message}")
        }
    }
    
    private fun buildMeshPacket(text: String): ByteArray {
        // Simple ToRadio packet with text message
        // This is a simplified version - full implementation would use generated protobuf
        val textBytes = text.toByteArray(Charsets.UTF_8)
        
        // Build Data message (portnum=1 for TEXT_MESSAGE_APP)
        val data = mutableListOf<Byte>()
        data.add(0x08) // field 1 (portnum)
        data.add(0x01) // TEXT_MESSAGE_APP = 1
        data.add(0x12) // field 2 (payload)
        data.add(textBytes.size.toByte())
        data.addAll(textBytes.toList())
        
        // Build MeshPacket
        val packet = mutableListOf<Byte>()
        packet.add(0x08) // field 1 (from) - will be set by radio
        packet.add(0x00)
        packet.add(0x10) // field 2 (to) - broadcast
        packet.addAll(listOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        packet.add(0x2A) // field 5 (decoded Data)
        packet.add(data.size.toByte())
        packet.addAll(data)
        packet.add(0x38) // field 7 (hop_limit)
        packet.add(0x03) // 3 hops
        
        // Build ToRadio
        val toRadio = mutableListOf<Byte>()
        toRadio.add(0x0A) // field 1 (packet)
        toRadio.add(packet.size.toByte())
        toRadio.addAll(packet)
        
        return toRadio.toByteArray()
    }
    
    private fun updateProgress() {
        progressBar.progress = currentChunk
        progressText.text = "TRANSMITTING $currentChunk/${chunks.size}"
    }
    
    private fun onTransmitComplete() {
        log("✅ TX BROADCAST COMPLETE!")
        log("⚡ ${chunks.size} packets sent to mesh")
        statusText.text = "✅ BROADCAST COMPLETE"
        progressContainer.visibility = View.GONE
        broadcastButton.isEnabled = true
        broadcastButton.text = "⚡ BROADCAST TO BITCOIN NETWORK ⚡"
        
        // Success flash
        ObjectAnimator.ofArgb(broadcastButton, "textColor", 
            Color.WHITE, Color.parseColor("#00FF88"), Color.WHITE).apply {
            duration = 1000
            start()
        }
    }
    
    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        runOnUiThread {
            logText.append("[$	s] $msg\n")
            logScroll.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}
