package com.emergency.mesh

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.emergency.mesh.handlers.MessageHandler
import com.emergency.mesh.handlers.VoiceHandler
import com.emergency.mesh.models.MeshMessage
import com.emergency.mesh.models.MeshPeer
import com.emergency.mesh.models.UserRole
import com.emergency.mesh.services.MeshService
import java.util.*

/**
 * Main activity with simple UI for emergency mesh communication
 */
class MainActivity : AppCompatActivity() {

    private lateinit var userRole: UserRole
    private lateinit var deviceId: String
    
    private var meshService: MeshService? = null
    private var serviceBound = false
    
    private lateinit var messageHandler: MessageHandler
    private lateinit var voiceHandler: VoiceHandler
    
    // UI components
    private lateinit var tvStatus: TextView
    private lateinit var tvPeerCount: TextView
    private lateinit var btnSOS: Button
    private lateinit var btnSendText: Button
    private lateinit var btnRecordVoice: Button
    private lateinit var etMessage: EditText
    private lateinit var lvMessages: ListView
    private lateinit var messagesAdapter: ArrayAdapter<String>
    
    private val receivedMessages = mutableListOf<String>()

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSIONS_REQUEST_CODE = 100
        private const val PREF_USER_ROLE = "user_role"
        private const val PREF_DEVICE_ID = "device_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize handlers
        messageHandler = MessageHandler(this)
        voiceHandler = VoiceHandler(this)
        
        // Load or select user role
        loadOrSelectUserRole()
        
        // Set up UI
        setupUI()
        
        // Request permissions
        requestPermissions()
    }

    override fun onStart() {
        super.onStart()
        // Bind to mesh service
        bindMeshService()
    }

    override fun onStop() {
        super.onStop()
        // Unbind from service
        unbindMeshService()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceHandler.cleanup()
    }

    /**
     * Set up UI based on user role
     */
    private fun setupUI() {
        setContentView(R.layout.activity_main)
        
        // Initialize views
        tvStatus = findViewById(R.id.tvStatus)
        tvPeerCount = findViewById(R.id.tvPeerCount)
        btnSOS = findViewById(R.id.btnSOS)
        btnSendText = findViewById(R.id.btnSendText)
        btnRecordVoice = findViewById(R.id.btnRecordVoice)
        etMessage = findViewById(R.id.etMessage)
        lvMessages = findViewById(R.id.lvMessages)
        
        // Set up messages list
        messagesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, receivedMessages)
        lvMessages.adapter = messagesAdapter
        
        // Set up button listeners
        btnSOS.setOnClickListener { sendSOS() }
        btnSendText.setOnClickListener { sendTextMessage() }
        
        btnRecordVoice.setOnClickListener {
            if (voiceHandler.isRecording()) {
                voiceHandler.stopRecording()
                btnRecordVoice.text = "🎤 Record Voice"
            } else {
                startVoiceRecording()
                btnRecordVoice.text = "⏹ Stop Recording"
            }
        }
        
        // Play voice message on item click
        lvMessages.setOnItemClickListener { _, _, position, _ ->
            // In real implementation, would store and play voice messages
            Toast.makeText(this, "Message clicked", Toast.LENGTH_SHORT).show()
        }
        
        updateStatus("Initializing...")
    }

    /**
     * Load or select user role
     */
    private fun loadOrSelectUserRole() {
        val prefs = getSharedPreferences("emergency_mesh", Context.MODE_PRIVATE)
        val savedRole = prefs.getString(PREF_USER_ROLE, null)
        
        if (savedRole != null) {
            userRole = UserRole.valueOf(savedRole)
            deviceId = prefs.getString(PREF_DEVICE_ID, UUID.randomUUID().toString())
                ?: UUID.randomUUID().toString()
        } else {
            // Show role selection dialog
            showRoleSelectionDialog()
        }
    }

    /**
     * Show role selection dialog
     */
    private fun showRoleSelectionDialog() {
        val roles = arrayOf("Citizen", "Official")
        
        AlertDialog.Builder(this)
            .setTitle("Select Your Role")
            .setItems(roles) { _, which ->
                userRole = if (which == 0) UserRole.CITIZEN else UserRole.OFFICIAL
                deviceId = UUID.randomUUID().toString()
                
                // Save selection
                val prefs = getSharedPreferences("emergency_mesh", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(PREF_USER_ROLE, userRole.name)
                    .putString(PREF_DEVICE_ID, deviceId)
                    .apply()
                
                Toast.makeText(this, "Role: ${userRole.name}", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Request necessary permissions
     */
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            startMeshService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                startMeshService()
            } else {
                Toast.makeText(this, "Permissions required for mesh network", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Start mesh service
     */
    private fun startMeshService() {
        val intent = Intent(this, MeshService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        updateStatus("Service started")
    }

    /**
     * Bind to mesh service
     */
    private fun bindMeshService() {
        val intent = Intent(this, MeshService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Unbind from mesh service
     */
    private fun unbindMeshService() {
        if (serviceBound) {
            meshService?.unregisterCallback(serviceCallback)
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    /**
     * Service connection
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MeshService.MeshBinder
            meshService = binder.getService()
            serviceBound = true
            
            meshService?.registerCallback(serviceCallback)
            updateStatus("Connected to mesh network")
            
            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            serviceBound = false
            updateStatus("Disconnected from mesh network")
            
            Log.d(TAG, "Service disconnected")
        }
    }

    /**
     * Service callback
     */
    private val serviceCallback = object : MeshService.MeshServiceCallback {
        override fun onMessageReceived(message: MeshMessage) {
            runOnUiThread {
                val formattedMessage = messageHandler.formatMessageForDisplay(message)
                receivedMessages.add(0, formattedMessage)
                
                // Limit messages list
                if (receivedMessages.size > 50) {
                    receivedMessages.removeAt(receivedMessages.size - 1)
                }
                
                messagesAdapter.notifyDataSetChanged()
                
                // Play voice message if applicable
                if (message.type == com.emergency.mesh.models.MessageType.VOICE && message.audioData != null) {
                    voiceHandler.playAudio(message.audioData)
                }
            }
        }

        override fun onPeersUpdated(peers: List<MeshPeer>) {
            runOnUiThread {
                tvPeerCount.text = "Peers: ${peers.size}"
            }
        }
    }

    /**
     * Send SOS message
     */
    private fun sendSOS() {
        val sosMessage = messageHandler.createSOSMessage(deviceId)
        meshService?.sendMessage(sosMessage)
        
        Toast.makeText(this, "🚨 SOS Sent", Toast.LENGTH_SHORT).show()
        updateStatus("SOS broadcast sent")
    }

    /**
     * Send text message
     */
    private fun sendTextMessage() {
        val text = etMessage.text.toString().trim()
        
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter a message", Toast.LENGTH_SHORT).show()
            return
        }
        
        val textMessage = messageHandler.createTextMessage(text, deviceId)
        meshService?.sendMessage(textMessage)
        
        etMessage.setText("")
        Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
    }

    /**
     * Start voice recording
     */
    private fun startVoiceRecording() {
        voiceHandler.startRecording { audioData ->
            runOnUiThread {
                btnRecordVoice.text = "🎤 Record Voice"
                
                if (audioData.isNotEmpty()) {
                    val voiceMessage = messageHandler.createVoiceMessage(audioData, deviceId)
                    meshService?.sendMessage(voiceMessage)
                    
                    Toast.makeText(this, "Voice message sent", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Update status text
     */
    private fun updateStatus(status: String) {
        tvStatus.text = status
    }
}
