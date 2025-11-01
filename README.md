# Emergency Mesh - Offline Emergency Communication System

A barebones, decentralized Android application for emergency communication in disaster scenarios without internet connectivity.

## Features

### Core Functionality
- **Offline Mesh Networking**: Uses Wi-Fi Direct and Bluetooth Low Energy (BLE) for peer-to-peer communication
- **Automatic Peer Discovery**: Discovers nearby devices and forms ad-hoc mesh network
- **Message Relay**: Automatically relays messages through peers to extend network range
- **GPS Coordinates**: Every message includes sender's GPS coordinates
- **Emergency SOS**: One-tap SOS broadcast with location
- **Voice Messages**: Record and send short voice messages (≤15 seconds)
- **Two User Roles**: Citizen mode (send SOS, messages) and Official mode (view SOS on map)

### Power Optimization
- Low-power BLE advertising and scanning cycles
- Lightweight Wi-Fi Direct connections
- Auto-disconnect after message delivery
- Foreground service with minimal battery impact
- Automatic cleanup of temporary audio files

## Architecture

### Modular Components

1. **ConnectionManager** (`network/ConnectionManager.kt`)
   - Unified interface for BLE and Wi-Fi Direct
   - Handles peer discovery and message routing
   - Prevents message loops with hop count and seen message cache

2. **BLEManager** (`network/BLEManager.kt`)
   - Manages Bluetooth Low Energy advertising and scanning
   - GATT server for incoming connections
   - Low-power operation modes

3. **WiFiDirectManager** (`network/WiFiDirectManager.kt`)
   - Handles Wi-Fi Direct peer discovery
   - Creates P2P groups for higher bandwidth
   - Socket-based message transmission

4. **MessageHandler** (`handlers/MessageHandler.kt`)
   - Creates messages with GPS coordinates
   - Location service integration
   - Message formatting for display

5. **VoiceHandler** (`handlers/VoiceHandler.kt`)
   - Records audio messages (max 15 seconds)
   - Plays received voice messages
   - Automatic cleanup to save storage

6. **MeshService** (`services/MeshService.kt`)
   - Foreground service keeps mesh network running
   - Background message relay
   - SOS notifications

## Technical Requirements

- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin
- **Required Hardware**: 
  - Bluetooth Low Energy
  - Wi-Fi Direct (optional but recommended)
  - GPS (optional, falls back gracefully)

## Permissions

The app requires the following permissions:
- `BLUETOOTH`, `BLUETOOTH_ADMIN` - For BLE operations
- `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` - Android 12+
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` - Required for BLE/Wi-Fi Direct scanning
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` - For Wi-Fi Direct
- `RECORD_AUDIO` - For voice messages
- `FOREGROUND_SERVICE` - Keep mesh running in background
- `POST_NOTIFICATIONS` - For SOS alerts

**Note**: No internet permission is required or used.

## Usage

### First Launch
1. Select your role: **Citizen** or **Official**
2. Grant required permissions
3. Mesh network starts automatically

### Citizen Mode
- **Send SOS**: Tap red emergency button to broadcast SOS with location
- **Send Text**: Type message and tap SEND
- **Voice Message**: Press "Record Voice", speak (max 15s), press again to send
- **View Messages**: Scroll through received messages with coordinates

### Official Mode
- View all SOS requests with locations
- Send response messages
- Monitor active peers

## Message Format

Every message includes:
```
[Sender ID]
HH:MM:SS (timestamp)
Lat: XX.XXXXXX, Lon: YY.YYYYYY
Message content
```

If GPS is unavailable: `Location not available`

## Mesh Behavior

- Messages are automatically relayed up to 5 hops
- Duplicate messages are filtered using message ID cache
- Peer timeout: 1 minute of inactivity
- Message cache: 5 minutes

## Power Optimization Strategy

1. **BLE**: Low-power advertising mode with 30-second intervals
2. **Wi-Fi Direct**: Connections closed after message delivery
3. **Location**: Uses last known location, no continuous tracking
4. **Audio**: Immediate cleanup after sending voice messages
5. **Service**: Runs as foreground service, system manages lifecycle

## Building the Project

### Prerequisites
- Android Studio Hedgehog or later
- JDK 8 or higher
- Android SDK 34

### Build Steps
```bash
cd EmergencyMesh
./gradlew build
```

### Install on Device
```bash
./gradlew installDebug
```

## Testing

### Testing Mesh Network
1. Install on 2+ devices
2. Grant all permissions on each device
3. Devices should auto-discover each other
4. Send messages - they should appear on all devices
5. Turn off one device and send from another - messages relay through mesh

### Testing Range Extension
1. Place 3 devices: A -- B -- C (where A and C can't reach each other directly)
2. Send message from A - should reach C via B (relay)
3. Check message shows hop count

## Limitations

- No encryption or authentication (by design for simplicity)
- No persistent message storage
- Limited to nearby devices (BLE: ~50m, Wi-Fi Direct: ~100m)
- Voice messages limited to 15 seconds
- Maximum 5 hops to prevent network congestion

## Future Enhancements (Out of Scope)

- End-to-end encryption
- Offline maps for SOS visualization
- Message priority queuing
- Battery level sharing
- Group messaging

## License

This is a demonstration/educational project for emergency communication scenarios.

## Safety Notice

This app is designed for emergency scenarios where traditional communication infrastructure is unavailable. It should complement, not replace, official emergency services.

## Technical Notes

### Why BLE + Wi-Fi Direct?
- **BLE**: Low power, good for discovery and small messages
- **Wi-Fi Direct**: Higher bandwidth for voice messages and faster relay
- **Both**: Redundancy and reliability

### Message Serialization
Messages are serialized using Java's `ObjectOutputStream` for simplicity. In production, use Protocol Buffers or similar for efficiency.

### Location Accuracy
Uses `getLastKnownLocation()` to avoid battery drain from continuous GPS tracking. In emergency scenarios, approximate location is sufficient.

## Contributing

This is a barebones implementation focused on core functionality. Contributions should maintain:
- Simplicity and minimal dependencies
- Low power consumption
- Offline-first design
- No internet connectivity

---

**Built for disaster scenarios where every connection matters.**
