package com.emergency.mesh.network

import android.content.Context
import android.util.Log
import com.emergency.mesh.models.MeshMessage
import com.emergency.mesh.models.MeshPeer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Unified connection manager that handles both BLE and Wi-Fi Direct
 * Coordinates message routing and peer discovery across both transports
 */
class ConnectionManager(private val context: Context) {

    private val bleManager = BLEManager(context)
    private val wifiDirectManager = WiFiDirectManager(context)
    
    private val activePeers = ConcurrentHashMap<String, MeshPeer>()
    private val seenMessages = ConcurrentHashMap<String, Long>()
    
    private val messageCallbacks = CopyOnWriteArrayList<(MeshMessage) -> Unit>()
    private val peerCallbacks = CopyOnWriteArrayList<(List<MeshPeer>) -> Unit>()

    companion object {
        private const val TAG = "ConnectionManager"
        private const val MESSAGE_CACHE_TIMEOUT = 300_000L // 5 minutes
    }

    /**
     * Initialize all network transports
     */
    fun initialize() {
        Log.d(TAG, "Initializing connection manager")
        
        // Initialize Wi-Fi Direct
        wifiDirectManager.initialize()
        
        // Set up message callbacks
        bleManager.onMessageReceived { message ->
            handleReceivedMessage(message, "BLE")
        }
        
        wifiDirectManager.onMessageReceived { message ->
            handleReceivedMessage(message, "WiFi-Direct")
        }
        
        // Set up peer callbacks
        bleManager.onPeerDiscovered { peer ->
            handlePeerDiscovered(peer)
        }
        
        wifiDirectManager.onPeerDiscovered { peer ->
            handlePeerDiscovered(peer)
        }
    }

    /**
     * Start discovering peers and advertising presence
     */
    fun startDiscovery() {
        Log.d(TAG, "Starting peer discovery")
        
        // Start BLE advertising and scanning
        bleManager.startAdvertising()
        bleManager.startScanning()
        
        // Start Wi-Fi Direct discovery
        wifiDirectManager.discoverPeers()
        wifiDirectManager.createGroup()
    }

    /**
     * Stop discovery and advertising
     */
    fun stopDiscovery() {
        Log.d(TAG, "Stopping peer discovery")
        
        bleManager.stopAdvertising()
        bleManager.stopScanning()
        wifiDirectManager.stopPeerDiscovery()
    }

    /**
     * Send message through all available transports
     */
    fun sendMessage(message: MeshMessage) {
        Log.d(TAG, "Sending message: ${message.type} from ${message.senderId}")
        
        // Mark message as seen to prevent echo
        seenMessages[message.id] = System.currentTimeMillis()
        
        // Send via both transports for redundancy
        bleManager.sendMessage(message)
        wifiDirectManager.sendMessage(message)
        
        // Clean up old message cache
        cleanupMessageCache()
    }

    /**
     * Handle received message from any transport
     */
    private fun handleReceivedMessage(message: MeshMessage, transport: String) {
        // Check if we've already seen this message
        if (seenMessages.containsKey(message.id)) {
            Log.d(TAG, "Ignoring duplicate message: ${message.id}")
            return
        }
        
        // Check hop count
        if (message.hasExceededMaxHops()) {
            Log.d(TAG, "Message exceeded max hops: ${message.id}")
            return
        }
        
        Log.d(TAG, "Received message via $transport: ${message.type}")
        
        // Mark as seen
        seenMessages[message.id] = System.currentTimeMillis()
        
        // Notify listeners
        messageCallbacks.forEach { callback ->
            callback(message)
        }
        
        // Relay message to other peers (mesh behavior)
        relayMessage(message)
    }

    /**
     * Relay message to other peers
     */
    private fun relayMessage(message: MeshMessage) {
        if (!message.hasExceededMaxHops()) {
            val relayedMessage = message.relay()
            
            // Small delay to avoid network congestion
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                bleManager.sendMessage(relayedMessage)
                wifiDirectManager.sendMessage(relayedMessage)
            }, 1000)
        }
    }

    /**
     * Handle discovered peer
     */
    private fun handlePeerDiscovered(peer: MeshPeer) {
        activePeers[peer.deviceId] = peer
        Log.d(TAG, "Peer discovered: ${peer.deviceName} via ${peer.connectionType}")
        
        // Notify listeners with updated peer list
        peerCallbacks.forEach { callback ->
            callback(getActivePeers())
        }
    }

    /**
     * Get list of active peers
     */
    fun getActivePeers(): List<MeshPeer> {
        // Remove inactive peers
        val now = System.currentTimeMillis()
        activePeers.entries.removeAll { (_, peer) ->
            !peer.isActive()
        }
        
        return activePeers.values.toList()
    }

    /**
     * Register callback for received messages
     */
    fun onMessageReceived(callback: (MeshMessage) -> Unit) {
        messageCallbacks.add(callback)
    }

    /**
     * Register callback for peer list updates
     */
    fun onPeersUpdated(callback: (List<MeshPeer>) -> Unit) {
        peerCallbacks.add(callback)
    }

    /**
     * Clean up old messages from cache
     */
    private fun cleanupMessageCache() {
        val now = System.currentTimeMillis()
        seenMessages.entries.removeAll { (_, timestamp) ->
            now - timestamp > MESSAGE_CACHE_TIMEOUT
        }
    }

    /**
     * Clean up all resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up connection manager")
        
        stopDiscovery()
        bleManager.cleanup()
        wifiDirectManager.cleanup()
        
        activePeers.clear()
        seenMessages.clear()
        messageCallbacks.clear()
        peerCallbacks.clear()
    }
}
