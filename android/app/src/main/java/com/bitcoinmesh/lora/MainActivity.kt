package com.bitcoinmesh.lora

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Bitcoin LoRa Mesh - Interface simplifiée
 * 
 * Cette app prépare les chunks de transaction Bitcoin
 * et les envoie via l'application Meshtastic officielle.
 * 
 * Le gateway reçoit les TEXT_MESSAGE et broadcast la TX sur le réseau Bitcoin.
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val CHUNK_SIZE = 190
        private const val MESHTASTIC_PACKAGE = "com.geeksville.mesh"
    }
    
    private lateinit var txInput: EditText
    private lateinit var pasteButton: Button
    private lateinit var sendButton: Button
    private lateinit var clearButton: Button
    private lateinit var installMeshButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var chunkProgress: TextView
    private lateinit var chunksDisplay: TextView
    
    private var currentChunks: List<String> = emptyList()
    private var currentChunkIndex = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        checkMeshtasticInstalled()
    }
    
    private fun initViews() {
        txInput = findViewById(R.id.txInput)
        pasteButton = findViewById(R.id.pasteButton)
        sendButton = findViewById(R.id.sendButton)
        clearButton = findViewById(R.id.clearButton)
        installMeshButton = findViewById(R.id.installMeshButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        chunkProgress = findViewById(R.id.chunkProgress)
        chunksDisplay = findViewById(R.id.chunksDisplay)
        
        pasteButton.setOnClickListener { pasteFromClipboard() }
        sendButton.setOnClickListener { prepareAndSend() }
        clearButton.setOnClickListener { clearAll() }
        installMeshButton.setOnClickListener { openPlayStore() }
        
        txInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateChunkPreview() }
        })
    }
    
    private fun checkMeshtasticInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(MESHTASTIC_PACKAGE, 0)
            installMeshButton.visibility = View.GONE
            statusText.text = "✅ Meshtastic détecté - Prêt!"
            log("✅ App Meshtastic installée")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            installMeshButton.visibility = View.VISIBLE
            statusText.text = "⚠️ Installez Meshtastic"
            log("⚠️ App Meshtastic non trouvée")
            false
        }
    }
    
    private fun openPlayStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$MESHTASTIC_PACKAGE")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$MESHTASTIC_PACKAGE")))
        }
    }
    
    private fun updateChunkPreview() {
        val tx = txInput.text.toString().trim()
        if (tx.isEmpty()) {
            chunkProgress.text = "0 caractères | 0 parties"
            chunksDisplay.text = ""
            return
        }
        
        val numChunks = (tx.length + CHUNK_SIZE - 1) / CHUNK_SIZE
        chunkProgress.text = "${tx.length} caractères | $numChunks parties"
        
        // Générer les chunks
        currentChunks = tx.chunked(CHUNK_SIZE).mapIndexed { index, chunk ->
            "BTX:${index + 1}/$numChunks:$chunk"
        }
        
        // Afficher preview
        val preview = currentChunks.take(3).joinToString("\n") { 
            if (it.length > 50) it.substring(0, 50) + "..." else it
        }
        if (currentChunks.size > 3) {
            chunksDisplay.text = "$preview\n... et ${currentChunks.size - 3} autres"
        } else {
            chunksDisplay.text = preview
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
        chunksDisplay.text = ""
        currentChunks = emptyList()
        currentChunkIndex = 0
        progressBar.visibility = View.GONE
        log("🗑️ Effacé")
    }
    
    private fun prepareAndSend() {
        if (currentChunks.isEmpty()) {
            log("❌ Entrez une transaction")
            Toast.makeText(this, "Entrez d'abord une transaction", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!checkMeshtasticInstalled()) {
            log("❌ Installez d'abord Meshtastic")
            return
        }
        
        currentChunkIndex = 0
        progressBar.max = currentChunks.size
        progressBar.progress = 0
        progressBar.visibility = View.VISIBLE
        
        log("📦 ${currentChunks.size} parties à envoyer")
        log("ℹ️ Copiez et collez chaque message dans Meshtastic")
        
        sendNextChunk()
    }
    
    private fun sendNextChunk() {
        if (currentChunkIndex >= currentChunks.size) {
            log("✅ Tous les chunks préparés!")
            statusText.text = "✅ Transaction envoyée!"
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Tous les messages envoyés!", Toast.LENGTH_LONG).show()
            return
        }
        
        val chunk = currentChunks[currentChunkIndex]
        
        // Copier dans le presse-papier
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("BTX Chunk", chunk)
        clipboard.setPrimaryClip(clip)
        
        log("📋 Partie ${currentChunkIndex + 1}/${currentChunks.size} copiée!")
        statusText.text = "📋 ${currentChunkIndex + 1}/${currentChunks.size} copié - Collez dans Meshtastic"
        
        // Ouvrir Meshtastic
        try {
            val intent = packageManager.getLaunchIntentForPackage(MESHTASTIC_PACKAGE)
            if (intent != null) {
                startActivity(intent)
                
                Toast.makeText(
                    this, 
                    "Collez le message (${currentChunkIndex + 1}/${currentChunks.size}) dans Meshtastic et envoyez!\nRevenez pour le suivant.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            log("❌ Impossible d'ouvrir Meshtastic")
        }
        
        currentChunkIndex++
        progressBar.progress = currentChunkIndex
    }
    
    override fun onResume() {
        super.onResume()
        // Quand l'utilisateur revient, proposer le prochain chunk
        if (currentChunkIndex > 0 && currentChunkIndex < currentChunks.size) {
            // Petit délai pour laisser l'UI se charger
            window.decorView.postDelayed({
                sendNextChunk()
            }, 500)
        }
    }
    
    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        runOnUiThread {
            logText.append("[$timestamp] $message\n")
            (logText.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
        }
    }
}
