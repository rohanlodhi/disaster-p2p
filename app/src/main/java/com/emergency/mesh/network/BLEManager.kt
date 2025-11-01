package com.emergency.mesh.network

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.emergency.mesh.models.MeshMessage
import com.emergency.mesh.models.MeshPeer
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Bluetooth Low Energy connections for mesh networking
 * Handles discovery, advertising, and message transmission via BLE
 */
class BLEManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null
    
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val messageCallbacks = mutableListOf<(MeshMessage) -> Unit>()
    private val peerCallbacks = mutableListOf<(MeshPeer) -> Unit>()
    
    private var isAdvertising = false
    private var isScanning = false

    companion object {
        private const val TAG = "BLEManager"
        
        // Service UUID for emergency mesh
        val SERVICE_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
        
        // Characteristic for message exchange
        val MESSAGE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001235-0000-1000-8000-00805f9b34fb")
        
        // Scan settings
        private const val SCAN_DURATION_MS = 10_000L
        private const val SCAN_INTERVAL_MS = 30_000L
        private const val ADVERTISE_DURATION_MS = 10_000L
        private const val ADVERTISE_INTERVAL_MS = 30_000L
    }

    /**
     * Start BLE advertising to make device discoverable
     */
    fun startAdvertising() {
        if (!checkBluetoothEnabled()) return
        
        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bleAdvertiser == null) {
            Log.e(TAG, "BLE Advertiser not available")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        try {
            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            isAdvertising = true
            Log.d(TAG, "BLE advertising started")
            setupGattServer()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting BLE advertising", e)
        }
    }

    /**
     * Stop BLE advertising
     */
    fun stopAdvertising() {
        try {
            if (isAdvertising) {
                bleAdvertiser?.stopAdvertising(advertiseCallback)
                isAdvertising = false
                Log.d(TAG, "BLE advertising stopped")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping advertising", e)
        }
    }

    /**
     * Start scanning for nearby BLE devices
     */
    fun startScanning() {
        if (!checkBluetoothEnabled()) return
        
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            Log.e(TAG, "BLE Scanner not available")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        try {
            bleScanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            Log.d(TAG, "BLE scanning started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting BLE scan", e)
        }
    }

    /**
     * Stop scanning for BLE devices
     */
    fun stopScanning() {
        try {
            if (isScanning) {
                bleScanner?.stopScan(scanCallback)
                isScanning = false
                Log.d(TAG, "BLE scanning stopped")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping scan", e)
        }
    }

    /**
     * Set up GATT server to handle incoming connections
     */
    private fun setupGattServer() {
        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            
            val messageCharacteristic = BluetoothGattCharacteristic(
                MESSAGE_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or 
                BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE or 
                BluetoothGattCharacteristic.PERMISSION_READ
            )

            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(messageCharacteristic)
            
            gattServer?.addService(service)
            Log.d(TAG, "GATT server started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception setting up GATT server", e)
        }
    }

    /**
     * Send message to all connected devices
     */
    fun sendMessage(message: MeshMessage) {
        // Serialize message
        val messageData = serializeMessage(message)
        
        // Send to all connected devices
        connectedDevices.values.forEach { device ->
            sendToDevice(device, messageData)
        }
    }

    /**
     * Send data to specific device
     */
    private fun sendToDevice(device: BluetoothDevice, data: ByteArray) {
        // In real implementation, would use GATT connection to write data
        Log.d(TAG, "Sending ${data.size} bytes to ${device.address}")
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
     * Serialize message to byte array
     */
    private fun serializeMessage(message: MeshMessage): ByteArray {
        val baos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(baos)
        oos.writeObject(message)
        oos.flush()
        return baos.toByteArray()
    }

    /**
     * Deserialize message from byte array
     */
    private fun deserializeMessage(data: ByteArray): MeshMessage? {
        return try {
            val bais = ByteArrayInputStream(data)
            val ois = ObjectInputStream(bais)
            ois.readObject() as MeshMessage
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing message", e)
            null
        }
    }

    /**
     * Check if Bluetooth is enabled
     */
    private fun checkBluetoothEnabled(): Boolean {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
            return false
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            return false
        }
        return true
    }

    /**
     * Advertise callback
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed with error: $errorCode")
        }
    }

    /**
     * Scan callback
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                try {
                    val device = it.device
                    val deviceName = device.name ?: "Unknown"
                    val deviceId = device.address
                    
                    Log.d(TAG, "Found device: $deviceName ($deviceId)")
                    
                    // Notify peer discovered
                    val peer = MeshPeer(
                        deviceId = deviceId,
                        deviceName = deviceName,
                        connectionType = MeshPeer.ConnectionType.BLUETOOTH_LE,
                        signalStrength = it.rssi
                    )
                    peerCallbacks.forEach { callback -> callback(peer) }
                    
                    // Store device
                    connectedDevices[deviceId] = device
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception in scan callback", e)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }

    /**
     * GATT server callback
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            try {
                device?.let {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "Device connected: ${it.address}")
                        connectedDevices[it.address] = it
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "Device disconnected: ${it.address}")
                        connectedDevices.remove(it.address)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in connection state change", e)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            try {
                if (characteristic?.uuid == MESSAGE_CHARACTERISTIC_UUID && value != null) {
                    Log.d(TAG, "Received message from ${device?.address}")
                    
                    // Deserialize and process message
                    val message = deserializeMessage(value)
                    message?.let { msg ->
                        messageCallbacks.forEach { callback -> callback(msg) }
                    }
                    
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in characteristic write", e)
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopAdvertising()
        stopScanning()
        try {
            gattServer?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception closing GATT server", e)
        }
        connectedDevices.clear()
    }
}
