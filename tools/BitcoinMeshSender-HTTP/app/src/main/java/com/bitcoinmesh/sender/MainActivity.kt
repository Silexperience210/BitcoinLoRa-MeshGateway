package com.bitcoinmesh.sender

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Bitcoin Mesh Sender - HTTP Version
 * Sends Bitcoin transactions to the gateway via HTTP API
 * Much simpler and more reliable than manual BLE protobuf encoding
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        const val CHUNK_SIZE = 190
        const val DEFAULT_GATEWAY_IP = "192.168.1.100"  // Change to your PC's IP
        const val GATEWAY_PORT = 8088
    }
    
    private lateinit var etGatewayIp: EditText
    private lateinit var etTransaction: EditText
    private lateinit var btnSend: Button
    private lateinit var btnTestConnection: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var progressBar: ProgressBar
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
    }
    
    private fun setupUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Title
        TextView(this).apply {
            text = "Bitcoin Mesh Sender"
            textSize = 24f
            setPadding(0, 0, 0, 32)
            layout.addView(this)
        }
        
        // Gateway IP
        TextView(this).apply {
            text = "Gateway IP Address:"
            layout.addView(this)
        }
        
        etGatewayIp = EditText(this).apply {
            hint = "192.168.1.100"
            setText(DEFAULT_GATEWAY_IP)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layout.addView(this)
        }
        
        // Test connection button
        btnTestConnection = Button(this).apply {
            text = "Test Connection"
            setOnClickListener { testConnection() }
            layout.addView(this)
        }
        
        // Status
        tvStatus = TextView(this).apply {
            text = "Status: Not connected"
            setPadding(0, 16, 0, 16)
            layout.addView(this)
        }
        
        // Transaction hex input
        TextView(this).apply {
            text = "Transaction Hex:"
            setPadding(0, 16, 0, 0)
            layout.addView(this)
        }
        
        etTransaction = EditText(this).apply {
            hint = "Paste raw transaction hex here..."
            minLines = 4
            maxLines = 8
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            layout.addView(this)
        }
        
        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = android.view.View.GONE
            layout.addView(this)
        }
        
        // Send button
        btnSend = Button(this).apply {
            text = "Send Transaction"
            setOnClickListener { sendTransaction() }
            layout.addView(this)
        }
        
        // Log
        TextView(this).apply {
            text = "Log:"
            setPadding(0, 32, 0, 8)
            layout.addView(this)
        }
        
        val scrollView = ScrollView(this)
        tvLog = TextView(this).apply {
            text = ""
            textSize = 12f
        }
        scrollView.addView(tvLog)
        layout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        
        setContentView(layout)
        
        log("App started. Enter gateway IP and test connection.")
    }
    
    private fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        tvLog.append("[$timestamp] $message\n")
    }
    
    private fun testConnection() {
        val ip = etGatewayIp.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Enter gateway IP", Toast.LENGTH_SHORT).show()
            return
        }
        
        tvStatus.text = "Status: Testing..."
        log("Testing connection to $ip:$GATEWAY_PORT...")
        
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URL("http://$ip:$GATEWAY_PORT/api/status")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"
                    
                    val responseCode = conn.responseCode
                    if (responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().readText()
                        JSONObject(response)
                    } else {
                        throw Exception("HTTP $responseCode")
                    }
                }
                
                tvStatus.text = "Status: Connected ✓"
                log("Connected! Mesh: ${result.optBoolean("mesh_connected")}, Pending: ${result.optInt("pending_txs")}")
                
            } catch (e: Exception) {
                tvStatus.text = "Status: Connection failed"
                log("Connection failed: ${e.message}")
            }
        }
    }
    
    private fun sendTransaction() {
        val ip = etGatewayIp.text.toString().trim()
        val txHex = etTransaction.text.toString().trim().replace("\\s".toRegex(), "")
        
        if (ip.isEmpty()) {
            Toast.makeText(this, "Enter gateway IP", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (txHex.isEmpty()) {
            Toast.makeText(this, "Enter transaction hex", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate hex
        if (!txHex.matches(Regex("^[0-9a-fA-F]+$"))) {
            Toast.makeText(this, "Invalid hex format", Toast.LENGTH_SHORT).show()
            return
        }
        
        btnSend.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE
        progressBar.progress = 0
        
        log("Sending transaction (${txHex.length} chars)...")
        
        scope.launch {
            try {
                // Split into chunks
                val chunks = txHex.chunked(CHUNK_SIZE)
                val totalChunks = chunks.size
                val txId = "tx_${System.currentTimeMillis()}"
                
                log("Split into $totalChunks chunks")
                
                for ((index, chunk) in chunks.withIndex()) {
                    val chunkNum = index + 1
                    
                    val success = withContext(Dispatchers.IO) {
                        sendChunk(ip, txId, chunkNum, totalChunks, chunk)
                    }
                    
                    if (!success) {
                        throw Exception("Failed to send chunk $chunkNum")
                    }
                    
                    val progress = ((chunkNum.toFloat() / totalChunks) * 100).toInt()
                    progressBar.progress = progress
                    log("Sent chunk $chunkNum/$totalChunks")
                    
                    // Small delay between chunks
                    delay(100)
                }
                
                log("Transaction sent successfully!")
                Toast.makeText(this@MainActivity, "Transaction sent!", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                log("Send failed: ${e.message}")
                Toast.makeText(this@MainActivity, "Send failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnSend.isEnabled = true
                progressBar.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun sendChunk(ip: String, txId: String, index: Int, total: Int, data: String): Boolean {
        return try {
            val url = URL("http://$ip:$GATEWAY_PORT/api/chunk")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val json = JSONObject().apply {
                put("tx_id", txId)
                put("index", index)
                put("total", total)
                put("data", data)
            }
            
            conn.outputStream.bufferedWriter().use { it.write(json.toString()) }
            
            val responseCode = conn.responseCode
            responseCode == 200
            
        } catch (e: Exception) {
            false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
