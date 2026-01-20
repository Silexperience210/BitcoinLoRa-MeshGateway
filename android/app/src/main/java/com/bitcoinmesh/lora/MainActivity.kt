package com.bitcoinmesh.lora

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
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BitcoinMesh"
        private const val CHUNK_SIZE = 190
        private const val CHUNK_DELAY_MS = 3000L
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    
    private lateinit var deviceSpinner: Spinner
    private lateinit var refreshButton: Button
    private lateinit var txInput: EditText
    private lateinit var pasteButton: Button
    private lateinit var sendButton: Button
    private lateinit var clearButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var chunkProgress: TextView
    
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
        refreshButton = findViewById(R.id.refreshButton)
        txInput = findViewById(R.id.txInput)
        pasteButton = findViewById(R.id.pasteButton)
        sendButton = findViewById(R.id.sendButton)
        clearButton = findViewById(R.id.clearButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        chunkProgress = findViewById(R.id.chunkProgress)
        
        refreshButton.setOnClickListener { loadPairedDevices() }
        pasteButton.setOnClickListener { pasteFromClipboard() }
        sendButton.setOnClickListener { connectAndSend() }
        clearButton.setOnClickListener { clearAll() }
        
        txInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateChunkInfo()
            }
        })
        
        deviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isConnected) disconnect()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun updateChunkInfo() {
        val tx = txInput.text.toString().trim()
        if (tx.isEmpty()) {
            chunkProgress.text = "0 caractères | 0 parties"
        } else {
            val numChunks = (tx.length + CHUNK_SIZE - 1) / CHUNK_SIZE
            chunkProgress.text = "${tx.length} caractères | $numChunks parties"
        }
    }
    
    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            txInput.setText(text)
            log("📋 Collé: ${text.length} caractères")
        }
    }
    
    private fun clearAll() {
        txInput.setText("")
        log("🗑️ Effacé")
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        
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
                log("❌ Permissions Bluetooth refusées")
                statusText.text = "❌ Permissions requises"
            }
        }
    }
    
    private fun initBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            log("⚠️ Bluetooth désactivé")
            statusText.text = "⚠️ Activez le Bluetooth"
            return
        }
        
        loadPairedDevices()
    }
    
    private fun loadPairedDevices() {
        try {
            pairedDevices.clear()
            val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
            pairedDevices.addAll(bonded)
            
            if (pairedDevices.isEmpty()) {
                log("⚠️ Aucun appareil appairé")
                statusText.text = "⚠️ Appairez le T-Beam d'abord"
                return
            }
            
            val deviceNames = pairedDevices.map { "${it.name ?: "Inconnu"}" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            deviceSpinner.adapter = adapter
            
            log("📱 ${pairedDevices.size} appareils trouvés")
            statusText.text = "✅ Prêt - Sélectionnez votre T-Beam"
            
        } catch (e: SecurityException) {
            log("❌ Permission Bluetooth manquante")
        }
    }
    
    private fun connectAndSend() {
        val tx = txInput.text.toString().trim()
        
        if (tx.isEmpty()) {
            log("❌ Transaction vide")
            statusText.text = "❌ Collez une transaction"
            return
        }
        
        val position = deviceSpinner.selectedItemPosition
        if (position < 0 || position >= pairedDevices.size) {
            log("❌ Sélectionnez un appareil")
            return
        }
        
        val device = pairedDevices[position]
        
        sendButton.isEnabled = false
        pasteButton.isEnabled = false
        clearButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Connect
                withContext(Dispatchers.Main) {
                    statusText.text = "🔄 Connexion à ${device.name}..."
                    log("🔄 Connexion...")
                }
                
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                isConnected = true
                
                withContext(Dispatchers.Main) {
                    log("✅ Connecté à ${device.name}")
                }
                
                // Send chunks
                val chunks = tx.chunked(CHUNK_SIZE)
                val totalChunks = chunks.size
                
                withContext(Dispatchers.Main) {
                    progressBar.max = totalChunks
                    progressBar.progress = 0
                    log("📦 Envoi: ${tx.length} chars → $totalChunks parties")
                }
                
                for ((index, chunk) in chunks.withIndex()) {
                    val chunkNum = index + 1
                    val message = "BTX:$chunkNum/$totalChunks:$chunk\n"
                    
                    outputStream?.write(message.toByteArray())
                    outputStream?.flush()
                    
                    withContext(Dispatchers.Main) {
                        progressBar.progress = chunkNum
                        statusText.text = "📤 Envoi partie $chunkNum/$totalChunks..."
                        log("📤 Partie $chunkNum/$totalChunks (${chunk.length} chars)")
                    }
                    
                    if (chunkNum < totalChunks) {
                        delay(CHUNK_DELAY_MS)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    log("✅ Transaction envoyée!")
                    statusText.text = "✅ TX envoyée sur LoRa!"
                }
                
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    log("❌ Erreur: ${e.message}")
                    statusText.text = "❌ Erreur connexion"
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    log("❌ Permission refusée")
                    statusText.text = "❌ Permission Bluetooth"
                }
            } finally {
                disconnect()
                withContext(Dispatchers.Main) {
                    sendButton.isEnabled = true
                    pasteButton.isEnabled = true
                    clearButton.isEnabled = true
                    progressBar.visibility = View.GONE
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
    }
    
    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logLine = "[$timestamp] $message\n"
        
        runOnUiThread {
            logText.append(logLine)
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
