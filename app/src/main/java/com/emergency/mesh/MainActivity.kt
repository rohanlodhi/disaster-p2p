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
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emergency.mesh.adapters.MessageAdapter
import com.emergency.mesh.handlers.MessageHandler
import com.emergency.mesh.handlers.VoiceHandler
import com.emergency.mesh.models.MeshMessage
import com.emergency.mesh.models.MeshPeer
import com.emergency.mesh.models.MessageType
import com.emergency.mesh.models.UserProfile
import com.emergency.mesh.models.UserRole
import com.emergency.mesh.services.MeshService
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.*

/**
 * Main activity with simple UI for emergency mesh communication
 */
class MainActivity : AppCompatActivity() {

    private lateinit var userRole: UserRole
    private lateinit var deviceId: String
    private var userProfile: UserProfile? = null
    
    private var meshService: MeshService? = null
    private var serviceBound = false
    
    private lateinit var messageHandler: MessageHandler
    private lateinit var voiceHandler: VoiceHandler
    
    // UI components
    private lateinit var tvStatus: TextView
    private lateinit var tvPeerCount: TextView
    private lateinit var btnSOS: View
    private lateinit var btnSendText: ImageButton
    private lateinit var btnRecordVoice: ImageButton
    private lateinit var etMessage: EditText
    private lateinit var rvMessages: RecyclerView
    private lateinit var switchPowerMode: SwitchMaterial
    private lateinit var messageAdapter: MessageAdapter
    
    // Track recording state
    private var isRecording = false
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSIONS_REQUEST_CODE = 100
        private const val PREF_USER_ROLE = "user_role"
        private const val PREF_DEVICE_ID = "device_id"
        private const val MAX_STORED_MESSAGES = 50
        private const val MAX_VOICE_MESSAGES = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize handlers
        messageHandler = MessageHandler(this)
        voiceHandler = VoiceHandler(this)
        
        // Check if user registration is needed
        if (!UserProfile.exists(this)) {
            showRegistrationDialog {
                // After registration, proceed with role selection
                loadOrSelectUserRole()
            }
        } else {
            userProfile = UserProfile.load(this)
            loadOrSelectUserRole()
        }
    }

    /**
     * Show user registration dialog on first launch
     */
    private fun showRegistrationDialog(onComplete: () -> Unit) {
        val dialogView: View = LayoutInflater.from(this).inflate(R.layout.dialog_registration, null)
        
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhone)
        val etEmergencyContact = dialogView.findViewById<EditText>(R.id.etEmergencyContact)
        val etBloodType = dialogView.findViewById<EditText>(R.id.etBloodType)
        val etMedicalInfo = dialogView.findViewById<EditText>(R.id.etMedicalInfo)
        
        AlertDialog.Builder(this)
            .setTitle("User Registration")
            .setMessage("Please provide your information for emergency situations")
            .setView(dialogView)
            .setPositiveButton("Register") { dialog, _ ->
                val name = etName.text.toString().trim()
                
                if (name.isBlank()) {
                    Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
                    showRegistrationDialog(onComplete)
                    return@setPositiveButton
                }
                
                val profile = UserProfile(
                    name = name,
                    phone = etPhone.text.toString().trim(),
                    emergencyContact = etEmergencyContact.text.toString().trim(),
                    bloodType = etBloodType.text.toString().trim(),
                    medicalInfo = etMedicalInfo.text.toString().trim()
                )
                
                UserProfile.save(this, profile)
                userProfile = profile
                
                Toast.makeText(this, "Registration complete", Toast.LENGTH_SHORT).show()
                onComplete()
            }
            .setCancelable(false)
            .show()
    }

    private fun continueSetup() {
        // Set up UI
        setupUI()
        
        // Request permissions
        requestPermissions()
    }

    override fun onStart() {
        super.onStart()
        // Bind to mesh service
        if (::userRole.isInitialized) {
            bindMeshService()
        }
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
        rvMessages = findViewById(R.id.rvMessages)
        switchPowerMode = findViewById(R.id.switchPowerMode)
        
        // Set up RecyclerView with adapter
        messageAdapter = MessageAdapter(this) { message ->
            // Voice message click handler
            if (message.audioData != null) {
                Toast.makeText(this, "Playing voice message...", Toast.LENGTH_SHORT).show()
                voiceHandler.playAudio(message.audioData)
            }
        }
        
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true // New messages appear at bottom
        rvMessages.layoutManager = layoutManager
        rvMessages.adapter = messageAdapter
        
        // Set up button listeners
        btnSOS.setOnClickListener { sendSOS() }
        btnSendText.setOnClickListener { sendTextMessage() }
        
        btnRecordVoice.setOnClickListener {
            if (isRecording) {
                voiceHandler.stopRecording()
                isRecording = false
                btnRecordVoice.setImageResource(android.R.drawable.ic_btn_speak_now)
            } else {
                startVoiceRecording()
                isRecording = true
                btnRecordVoice.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
        
        // Power mode toggle (High Range vs Power Saving)
        switchPowerMode.isChecked = true // Default to high range
        switchPowerMode.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) "High Range" else "Power Saving"
            Toast.makeText(this, "Mode: $mode", Toast.LENGTH_SHORT).show()
            meshService?.setPowerMode(isChecked)
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
            continueSetup()
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
                continueSetup()
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
            Manifest.permission.BLUETOOTH_ADMIN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
            
            // Set user role on service
            if (::userRole.isInitialized) {
                meshService?.setUserRole(userRole)
            }
            
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
            Log.d(TAG, "Activity received message: ${message.id}. Current role: $userRole")
            runOnUiThread {
                try {
                    addMessageToList(message, isSent = false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating UI with message", e)
                }
            }
        }

        override fun onPeersUpdated(peers: List<MeshPeer>) {
            runOnUiThread {
                tvPeerCount.text = "👥 ${peers.size} peer${if (peers.size != 1) "s" else ""}"
            }
        }
    }

    /**
     * Add a message to the display list
     */
    private fun addMessageToList(message: MeshMessage, isSent: Boolean) {
        messageAdapter.addMessage(message, isSent)
        messageAdapter.trimToSize(MAX_STORED_MESSAGES)
        
        // Scroll to bottom to show new message
        rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
        
        Log.d(TAG, "UI updated with message: ${message.id}, isSent: $isSent")
    }

    /**
     * Send SOS message
     */
    private fun sendSOS() {
        val sosMessage = messageHandler.createSOSMessage(deviceId)
        meshService?.sendMessage(sosMessage)
        
        // Display sent message in UI
        addMessageToList(sosMessage, isSent = true)
        
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
        
        // Display sent message in UI
        addMessageToList(textMessage, isSent = true)
        
        etMessage.setText("")
        Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
    }

    /**
     * Start voice recording
     */
    private fun startVoiceRecording() {
        voiceHandler.startRecording { audioData ->
            runOnUiThread {
                isRecording = false
                btnRecordVoice.setImageResource(android.R.drawable.ic_btn_speak_now)
                
                if (audioData.isNotEmpty()) {
                    val voiceMessage = messageHandler.createVoiceMessage(audioData, deviceId)
                    meshService?.sendMessage(voiceMessage)
                    
                    // Display sent message in UI
                    addMessageToList(voiceMessage, isSent = true)
                    
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
