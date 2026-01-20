package org.silexperience.bitcoinmesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread

/**
 * ⚡ BitcoinMesh Client - Android
 * Broadcast Bitcoin transactions over LoRa mesh network
 * Created by Silexperience & ProfEduStream
 */
class MainActivity : AppCompatActivity() {

    companion object {
        // Meshtastic BLE Service UUID
        private val MESHTASTIC_SERVICE_UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        private val MESHTASTIC_TORADIO_UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        
        // Standard Serial Port Profile UUID for Bluetooth Classic
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        private const val CHUNK_SIZE = 190
        private const val REQUEST_PERMISSIONS = 1
        private const val DELAY_BETWEEN_CHUNKS_MS = 3000L // 3 secondes entre chaque chunk
    }

    private lateinit var txInput: EditText
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sendButton: Button
    private lateinit var pasteButton: Button
    private lateinit var clearButton: Button
    private lateinit var deviceSpinner: Spinner
    private lateinit var refreshButton: Button
    private lateinit var logText: TextView
    private lateinit var chunkProgress: TextView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var pairedDevices: List<BluetoothDevice> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupBluetooth()
        checkPermissions()
    }

    private fun initViews() {
        txInput = findViewById(R.id.txInput)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        sendButton = findViewById(R.id.sendButton)
        pasteButton = findViewById(R.id.pasteButton)
        clearButton = findViewById(R.id.clearButton)
        deviceSpinner = findViewById(R.id.deviceSpinner)
        refreshButton = findViewById(R.id.refreshButton)
        logText = findViewById(R.id.logText)
        chunkProgress = findViewById(R.id.chunkProgress)

        sendButton.setOnClickListener { sendTransaction() }
        pasteButton.setOnClickListener { pasteFromClipboard() }
        clearButton.setOnClickListener { clearAll() }
        refreshButton.setOnClickListener { refreshDevices() }

        txInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateCharCount()
            }
        })
    }

    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            statusText.text = "❌ Bluetooth non disponible"
            sendButton.isEnabled = false
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            refreshDevices()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                refreshDevices()
            } else {
                statusText.text = "❌ Permissions Bluetooth requises"
            }
        }
    }

    private fun refreshDevices() {
        try {
            pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            
            // Filter for Meshtastic devices (usually named "Meshtastic_xxxx")
            val deviceNames = pairedDevices.map { device ->
                "${device.name ?: "Unknown"} (${device.address})"
            }.toMutableList()
            
            if (deviceNames.isEmpty()) {
                deviceNames.add("Aucun appareil appairé")
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            deviceSpinner.adapter = adapter

            log("🔍 ${pairedDevices.size} appareils trouvés")
            statusText.text = "✅ Sélectionnez votre T-Beam"
            
        } catch (e: SecurityException) {
            log("❌ Permission Bluetooth refusée")
            statusText.text = "❌ Permission refusée"
        }
    }

    private fun updateCharCount() {
        val text = txInput.text.toString()
        val cleanHex = text.replace(" ", "").replace("\n", "").replace("0x", "")
        val chars = cleanHex.length
        val chunks = if (chars > 0) (chars + CHUNK_SIZE - 1) / CHUNK_SIZE else 0
        
        chunkProgress.text = "$chars caractères | $chunks parties"
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text.toString()
            txInput.setText(text)
            log("📋 Collé depuis le presse-papier")
        }
    }

    private fun clearAll() {
        txInput.setText("")
        logText.text = ""
        chunkProgress.text = "0 caractères | 0 parties"
        statusText.text = "✅ Prêt"
    }

    private fun sendTransaction() {
        val rawTx = txInput.text.toString()
        val cleanHex = rawTx.replace(" ", "").replace("\n", "").replace("\r", "").replace("0x", "")

        // Validate
        if (cleanHex.isEmpty()) {
            statusText.text = "❌ Aucune transaction"
            return
        }

        if (!cleanHex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            statusText.text = "❌ Format invalide"
            return
        }

        if (pairedDevices.isEmpty() || deviceSpinner.selectedItemPosition >= pairedDevices.size) {
            statusText.text = "❌ Sélectionnez un appareil"
            return
        }

        val device = pairedDevices[deviceSpinner.selectedItemPosition]
        
        // Create chunks
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < cleanHex.length) {
            chunks.add(cleanHex.substring(i, minOf(i + CHUNK_SIZE, cleanHex.length)))
            i += CHUNK_SIZE
        }

        log("⚡ Envoi de ${chunks.size} parties vers ${device.name}")
        statusText.text = "📡 Connexion..."
        progressBar.visibility = View.VISIBLE
        progressBar.max = chunks.size
        progressBar.progress = 0
        sendButton.isEnabled = false

        // Send in background thread
        thread {
            try {
                connectAndSend(device, chunks)
            } catch (e: Exception) {
                handler.post {
                    log("❌ Erreur: ${e.message}")
                    statusText.text = "❌ Échec: ${e.message}"
                    progressBar.visibility = View.GONE
                    sendButton.isEnabled = true
                }
            }
        }
    }

    @Throws(IOException::class, SecurityException::class)
    private fun connectAndSend(device: BluetoothDevice, chunks: List<String>) {
        handler.post { log("🔗 Connexion à ${device.name}...") }

        // Try to connect via Bluetooth Classic (SPP)
        bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        
        try {
            bluetoothSocket?.connect()
        } catch (e: IOException) {
            // Fallback: try reflection method for some devices
            handler.post { log("⚠️ Tentative connexion alternative...") }
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            bluetoothSocket = method.invoke(device, 1) as BluetoothSocket
            bluetoothSocket?.connect()
        }

        outputStream = bluetoothSocket?.outputStream
        
        handler.post { 
            log("✅ Connecté!")
            statusText.text = "📡 Envoi en cours..."
        }

        // Send each chunk with delay
        for ((index, chunk) in chunks.withIndex()) {
            val partNum = index + 1
            
            handler.post {
                log("📤 Envoi partie $partNum/${chunks.size}...")
                progressBar.progress = partNum
                chunkProgress.text = "Envoi $partNum/${chunks.size}"
            }

            // Send the chunk as text message
            // Format: just the hex data, the gateway will reassemble
            val message = chunk + "\n"
            outputStream?.write(message.toByteArray())
            outputStream?.flush()

            handler.post { log("  ✓ Partie $partNum envoyée (${chunk.length} chars)") }

            // Wait between chunks (important for LoRa transmission)
            if (index < chunks.size - 1) {
                Thread.sleep(DELAY_BETWEEN_CHUNKS_MS)
            }
        }

        // Cleanup
        outputStream?.close()
        bluetoothSocket?.close()

        handler.post {
            log("🎉 Transaction envoyée avec succès!")
            statusText.text = "✅ Envoyé! (${chunks.size} parties)"
            progressBar.visibility = View.GONE
            sendButton.isEnabled = true
        }
    }

    private fun log(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logLine = "[$time] $message\n"
        
        handler.post {
            logText.append(logLine)
            // Auto-scroll
            val scrollView = logText.parent as? ScrollView
            scrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            // Ignore
        }
    }
}
