package com.emergency.mesh.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.util.Log
import com.emergency.mesh.models.MeshMessage
import com.emergency.mesh.models.MeshPeer
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Manages Wi-Fi Direct connections for mesh networking
 * Provides higher bandwidth alternative to BLE for larger messages
 */
class WiFiDirectManager(private val context: Context) {

    private val wifiP2pManager: WifiP2pManager? = 
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    
    private val peers = ConcurrentHashMap<String, WifiP2pDevice>()
    private val messageCallbacks = mutableListOf<(MeshMessage) -> Unit>()
    private val peerCallbacks = mutableListOf<(MeshPeer) -> Unit>()
    
    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false
    private var groupOwnerAddress: InetAddress? = null

    companion object {
        private const val TAG = "WiFiDirectManager"
        private const val SERVER_PORT = 8888
        private const val SOCKET_TIMEOUT_MS = 5000
    }

    /**
     * Initialize Wi-Fi Direct
     */
    fun initialize() {
        channel = wifiP2pManager?.initialize(context, context.mainLooper, null)
        registerReceiver()
        Log.d(TAG, "Wi-Fi Direct initialized")
    }

    /**
     * Discover nearby Wi-Fi Direct peers
     */
    fun discoverPeers() {
        try {
            wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Peer discovery started")
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Peer discovery failed: $reason")
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception discovering peers", e)
        }
    }

    /**
     * Stop peer discovery
     */
    fun stopPeerDiscovery() {
        try {
            wifiP2pManager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Peer discovery stopped")
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to stop peer discovery: $reason")
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping peer discovery", e)
        }
    }

    /**
     * Create Wi-Fi Direct group (become group owner)
     */
    fun createGroup() {
        try {
            wifiP2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Group created successfully")
                    startServer()
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Group creation failed: $reason")
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception creating group", e)
        }
    }

    /**
     * Start server socket to receive connections
     */
    private fun startServer() {
        if (isServerRunning) return
        
        thread {
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                isServerRunning = true
                Log.d(TAG, "Server started on port $SERVER_PORT")
                
                while (isServerRunning) {
                    try {
                        val client = serverSocket?.accept()
                        client?.let { handleClient(it) }
                    } catch (e: SocketTimeoutException) {
                        // Timeout is expected, continue
                    } catch (e: IOException) {
                        if (isServerRunning) {
                            Log.e(TAG, "Error accepting client", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error starting server", e)
            } finally {
                isServerRunning = false
            }
        }
    }

    /**
     * Handle client connection
     */
    private fun handleClient(socket: Socket) {
        thread {
            try {
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val inputStream = ObjectInputStream(socket.getInputStream())
                
                val message = inputStream.readObject() as? MeshMessage
                message?.let { msg ->
                    Log.d(TAG, "Received message via Wi-Fi Direct")
                    messageCallbacks.forEach { callback -> callback(msg) }
                }
                
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client", e)
            }
        }
    }

    /**
     * Send message to group owner or client
     */
    fun sendMessage(message: MeshMessage) {
        groupOwnerAddress?.let { address ->
            thread {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(address, SERVER_PORT), SOCKET_TIMEOUT_MS)
                    
                    val outputStream = ObjectOutputStream(socket.getOutputStream())
                    outputStream.writeObject(message)
                    outputStream.flush()
                    
                    socket.close()
                    Log.d(TAG, "Message sent via Wi-Fi Direct")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending message", e)
                }
            }
        }
    }

    /**
     * Register callback for received messages
     */
    fun onMessageReceived(callback: (MeshMessage) -> Unit) {
        messageCallbacks.add(callback)
    }

    /**
     * Register callback for discovered peers
     */
    fun onPeerDiscovered(callback: (MeshPeer) -> Unit) {
        peerCallbacks.add(callback)
    }

    /**
     * Register broadcast receiver for Wi-Fi Direct events
     */
    private fun registerReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        
        context.registerReceiver(wifiP2pReceiver, intentFilter)
    }

    /**
     * Unregister broadcast receiver
     */
    private fun unregisterReceiver() {
        try {
            context.unregisterReceiver(wifiP2pReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
    }

    /**
     * Wi-Fi P2P broadcast receiver
     */
    private val wifiP2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.d(TAG, "Wi-Fi P2P state changed: $isEnabled")
                }
                
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }
                
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(
                        WifiP2pManager.EXTRA_NETWORK_INFO
                    )
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    }
                }
                
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Device info changed
                }
            }
        }
    }

    /**
     * Request available peers
     */
    private fun requestPeers() {
        try {
            wifiP2pManager?.requestPeers(channel) { peerList ->
                peers.clear()
                peerList.deviceList.forEach { device ->
                    peers[device.deviceAddress] = device
                    
                    val peer = MeshPeer(
                        deviceId = device.deviceAddress,
                        deviceName = device.deviceName ?: "Unknown",
                        connectionType = MeshPeer.ConnectionType.WIFI_DIRECT
                    )
                    peerCallbacks.forEach { callback -> callback(peer) }
                }
                Log.d(TAG, "Found ${peerList.deviceList.size} peers")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception requesting peers", e)
        }
    }

    /**
     * Request connection information
     */
    private fun requestConnectionInfo() {
        try {
            wifiP2pManager?.requestConnectionInfo(channel) { info ->
                if (info.groupFormed) {
                    if (info.isGroupOwner) {
                        Log.d(TAG, "This device is group owner")
                        startServer()
                    } else {
                        Log.d(TAG, "Connected to group owner: ${info.groupOwnerAddress}")
                        groupOwnerAddress = info.groupOwnerAddress
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception requesting connection info", e)
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        isServerRunning = false
        serverSocket?.close()
        stopPeerDiscovery()
        unregisterReceiver()
        
        try {
            wifiP2pManager?.removeGroup(channel, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception removing group", e)
        }
        
        peers.clear()
    }
}
