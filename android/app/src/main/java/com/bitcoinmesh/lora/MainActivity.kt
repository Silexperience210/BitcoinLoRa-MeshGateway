package com.bitcoinmesh.lora

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BitcoinMesh"
        private const val CHUNK_SIZE = 190
        private const val CHUNK_DELAY_MS = 3000L
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
        // UUID SPP standard pour communication série Bluetooth
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    
    private lateinit var deviceSpinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var txInput: EditText
    private lateinit var sendButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var chunkInfo: TextView
    
    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private var isConnected = false
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        checkPermissions()
    }
    
    private fun initViews() {
        deviceSpinner = findViewById(R.id.deviceSpinner)
        connectButton = findViewById(R.id.connectButton)
        txInput = findViewById(R.id.txInput)
        sendButton = findViewById(R.id.sendButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        chunkInfo = findViewById(R.id.chunkInfo)
        
        connectButton.setOnClickListener { toggleConnection() }
        sendButton.setOnClickListener { sendTransaction() }
        
        // Mettre à jour l'info chunks quand le texte change
        txInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateChunkInfo()
            }
        })
        
        sendButton.isEnabled = false
    }
    
    private fun updateChunkInfo() {
        val tx = txInput.text.toString().trim()
        if (tx.isEmpty()) {
            chunkInfo.text = "0 caractères → 0 chunks"
        } else {
            val numChunks = (tx.length + CHUNK_SIZE - 1) / CHUNK_SIZE
            chunkInfo.text = "${tx.length} caractères → $numChunks chunks"
        }
    }
    
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        } else {
            initBluetooth()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initBluetooth()
            } else {
                log("❌ Permissions Bluetooth refusées", Color.RED)
            }
        }
    }
    
    private fun initBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        if (!bluetoothAdapter.isEnabled) {
            log("⚠️ Bluetooth désactivé - Activez-le dans les paramètres", Color.YELLOW)
            return
        }
        
        loadPairedDevices()
    }
    
    private fun loadPairedDevices() {
        try {
            pairedDevices.clear()
            val bonded = bluetoothAdapter.bondedDevices ?: emptySet()
            pairedDevices.addAll(bonded)
            
            if (pairedDevices.isEmpty()) {
                log("⚠️ Aucun appareil appairé - Appairez le T-Beam d'abord", Color.YELLOW)
                return
            }
            
            val deviceNames = pairedDevices.map { "${it.name ?: "Inconnu"} (${it.address})" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            deviceSpinner.adapter = adapter
            
            log("📱 ${pairedDevices.size} appareils trouvés", Color.WHITE)
            
        } catch (e: SecurityException) {
            log("❌ Permission Bluetooth manquante", Color.RED)
        }
    }
    
    private fun toggleConnection() {
        if (isConnected) {
            disconnect()
        } else {
            connect()
        }
    }
    
    private fun connect() {
        val position = deviceSpinner.selectedItemPosition
        if (position < 0 || position >= pairedDevices.size) {
            log("❌ Sélectionnez un appareil", Color.RED)
            return
        }
        
        val device = pairedDevices[position]
        log("🔄 Connexion à ${device.name}...", Color.CYAN)
        connectButton.isEnabled = false
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                
                withContext(Dispatchers.Main) {
                    isConnected = true
                    connectButton.text = "Déconnecter"
                    connectButton.isEnabled = true
                    sendButton.isEnabled = true
                    statusText.text = "🟢 Connecté à ${device.name}"
                    statusText.setTextColor(Color.parseColor("#00FF00"))
                    log("✅ Connecté à ${device.name}", Color.GREEN)
                }
                
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    log("❌ Échec connexion: ${e.message}", Color.RED)
                    connectButton.isEnabled = true
                    disconnect()
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    log("❌ Permission refusée", Color.RED)
                    connectButton.isEnabled = true
                }
            }
        }
    }
    
    private fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        }
        
        outputStream = null
        bluetoothSocket = null
        isConnected = false
        
        connectButton.text = "Connecter"
        sendButton.isEnabled = false
        statusText.text = "🔴 Déconnecté"
        statusText.setTextColor(Color.parseColor("#FF6B00"))
        log("🔌 Déconnecté", Color.GRAY)
    }
    
    private fun sendTransaction() {
        val tx = txInput.text.toString().trim()
        
        if (tx.isEmpty()) {
            log("❌ Transaction vide", Color.RED)
            return
        }
        
        if (!isConnected || outputStream == null) {
            log("❌ Non connecté", Color.RED)
            return
        }
        
        // Découper en chunks
        val chunks = tx.chunked(CHUNK_SIZE)
        val totalChunks = chunks.size
        
        log("📦 Transaction: ${tx.length} chars → $totalChunks chunks", Color.WHITE)
        
        // Désactiver les contrôles pendant l'envoi
        sendButton.isEnabled = false
        txInput.isEnabled = false
        connectButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.max = totalChunks
        progressBar.progress = 0
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                for ((index, chunk) in chunks.withIndex()) {
                    val chunkNum = index + 1
                    val message = "BTX:$chunkNum/$totalChunks:$chunk\n"
                    
                    outputStream?.write(message.toByteArray())
                    outputStream?.flush()
                    
                    withContext(Dispatchers.Main) {
                        progressBar.progress = chunkNum
                        log("📤 Chunk $chunkNum/$totalChunks envoyé (${chunk.length} chars)", Color.parseColor("#FF6B00"))
                    }
                    
                    // Attendre entre les chunks (sauf le dernier)
                    if (chunkNum < totalChunks) {
                        withContext(Dispatchers.Main) {
                            statusText.text = "⏳ Attente 3s avant chunk ${chunkNum + 1}..."
                        }
                        delay(CHUNK_DELAY_MS)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    log("✅ Transaction envoyée! $totalChunks chunks transmis", Color.GREEN)
                    statusText.text = "✅ TX envoyée sur LoRa!"
                    statusText.setTextColor(Color.GREEN)
                    
                    // Effet de succès
                    sendButton.text = "✅ ENVOYÉ!"
                    mainHandler.postDelayed({
                        sendButton.text = "⚡ ENVOYER SUR LORA"
                    }, 2000)
                }
                
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    log("❌ Erreur envoi: ${e.message}", Color.RED)
                    disconnect()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    sendButton.isEnabled = isConnected
                    txInput.isEnabled = true
                    connectButton.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
    }
    
    private fun log(message: String, color: Int) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logLine = "[$timestamp] $message\n"
        
        runOnUiThread {
            logText.append(logLine)
            // Auto-scroll vers le bas
            val scrollView = logText.parent as? ScrollView
            scrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        coroutineScope.cancel()
    }
}
