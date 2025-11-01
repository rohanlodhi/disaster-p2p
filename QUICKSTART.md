# Quick Start Guide - Emergency Mesh

## Prerequisites

### Required Software
- **Android Studio**: Hedgehog (2023.1.1) or later
- **Java Development Kit**: JDK 8 or higher
- **Android SDK**: API 34 (Android 14)
- **Gradle**: 8.2+ (included via wrapper)

### Required Hardware (for testing)
- **Minimum**: 2 Android devices with:
  - Android 8.0 (API 26) or higher
  - Bluetooth Low Energy support
  - GPS (optional but recommended)
- **Recommended**: 3+ devices for mesh relay testing
- **Optional**: Wi-Fi Direct support for higher bandwidth

## Installation Steps

### 1. Clone/Download Project
```bash
cd /home/rohan/Desktop/flame-courses/imp/EmergencyMesh
```

### 2. Open in Android Studio
1. Launch Android Studio
2. File → Open → Select `EmergencyMesh` folder
3. Wait for Gradle sync to complete

### 3. Build Project
```bash
# From terminal
cd EmergencyMesh
./gradlew build

# Or use Android Studio
Build → Make Project (Ctrl+F9)
```

### 4. Install on Device

#### Via Android Studio
1. Connect Android device via USB
2. Enable USB debugging on device:
   - Settings → About Phone → Tap "Build Number" 7 times
   - Settings → Developer Options → Enable USB Debugging
3. Click "Run" button (▶) in Android Studio
4. Select your device from list

#### Via Command Line
```bash
# Install debug APK
./gradlew installDebug

# Or manually install
adb install app/build/outputs/apk/debug/app-debug.apk
```

## First Launch Setup

### Step 1: Select Role
On first launch, choose your role:
- **Citizen**: For general users sending/receiving messages
- **Official**: For emergency responders (future map view)

This choice is saved and won't be asked again (unless app data is cleared).

### Step 2: Grant Permissions
The app will request several permissions:
- ✓ **Location** (Fine & Coarse) - Required for BLE/Wi-Fi scanning
- ✓ **Bluetooth** - For mesh networking
- ✓ **Nearby Devices** - Android 12+ requirement
- ✓ **Microphone** - For voice messages
- ✓ **Notifications** - For SOS alerts

**Important**: Grant ALL permissions for full functionality.

### Step 3: Wait for Service Start
- Notification appears: "Emergency Mesh Active"
- Status changes to: "Connected to mesh network"
- Mesh service now running in background

## Testing the Mesh Network

### Basic Test (2 Devices)

**Device A Setup:**
1. Install and launch app
2. Select "Citizen" role
3. Grant all permissions
4. Wait for "Peers: 0" → "Peers: 1"

**Device B Setup:**
1. Install and launch app
2. Select "Citizen" role  
3. Grant all permissions
4. Should auto-discover Device A

**Send Test Message:**
1. On Device A: Type "Hello from A" → Tap SEND
2. On Device B: Message appears in list with coordinates
3. Try reverse: Send from B to A

### Mesh Relay Test (3+ Devices)

**Scenario**: Test message relay through intermediate device

**Setup:**
```
Device A ←→ Device B ←→ Device C
(Move A and C out of direct range)
```

**Test:**
1. Device A sends message
2. Device B receives and auto-relays
3. Device C receives relayed message
4. Check message shows hop count

### SOS Test

1. Tap red "🚨 EMERGENCY SOS" button
2. SOS broadcast sent to all peers
3. All devices receive notification
4. Message includes GPS coordinates

### Voice Message Test

1. Tap "🎤 Record Voice" button
2. Speak for up to 15 seconds
3. Tap "⏹ Stop Recording"
4. Voice message sent to peers
5. Recipients tap message to play audio

## Troubleshooting

### Problem: Devices Don't Discover Each Other

**Solutions:**
- ✓ Verify Bluetooth is enabled on both devices
- ✓ Check Location permission granted
- ✓ Ensure devices are within 50m (BLE range)
- ✓ Wait 30-60 seconds for discovery cycle
- ✓ Restart app on both devices
- ✓ Check Android version ≥ 8.0

### Problem: No GPS Coordinates in Messages

**Solutions:**
- ✓ Grant Location permission
- ✓ Enable Location Services in device settings
- ✓ Move to outdoor area for GPS lock
- ✓ Wait for GPS acquisition (can take 30-60 sec)
- ✓ App works without GPS (shows "Location not available")

### Problem: Voice Recording Fails

**Solutions:**
- ✓ Grant Microphone permission
- ✓ Check no other app using microphone
- ✓ Restart app
- ✓ Test with different recording duration

### Problem: App Crashes on Launch

**Solutions:**
- ✓ Clear app data: Settings → Apps → Emergency Mesh → Clear Data
- ✓ Reinstall app
- ✓ Check Android version ≥ 8.0
- ✓ Verify device has BLE support

### Problem: High Battery Drain

**Expected Behavior:**
- Mesh service runs continuously
- BLE scanning is low-power but not zero
- ~5-10% battery per hour is normal

**Solutions:**
- ✓ Reduce screen brightness
- ✓ Close other apps
- ✓ In real emergency, battery life is secondary

## Development Tips

### Enable Debug Logging
```bash
# View logs in real-time
adb logcat -s EmergencyMesh:* BLEManager:* WiFiDirectManager:* ConnectionManager:* MeshService:*

# Filter specific component
adb logcat -s BLEManager:D
```

### Monitor Network Activity
```bash
# Watch peer discovery
adb logcat -s ConnectionManager:D | grep "Peer discovered"

# Watch message flow
adb logcat -s MeshService:D | grep "Received message"
```

### Test Without Multiple Devices
- Use Android Emulator (limited BLE support)
- BLE simulation tools (nRF Connect app)
- Mock peer discovery in code

### Build Variants
```bash
# Debug build (with logging)
./gradlew assembleDebug

# Release build (optimized)
./gradlew assembleRelease
```

## Project Structure Quick Reference
```
EmergencyMesh/
├── app/src/main/
│   ├── java/com/emergency/mesh/
│   │   ├── MainActivity.kt          # Main UI
│   │   ├── models/                  # Data models
│   │   │   ├── MeshMessage.kt
│   │   │   ├── MeshPeer.kt
│   │   │   └── UserRole.kt
│   │   ├── network/                 # Networking
│   │   │   ├── ConnectionManager.kt
│   │   │   ├── BLEManager.kt
│   │   │   └── WiFiDirectManager.kt
│   │   ├── handlers/                # Business logic
│   │   │   ├── MessageHandler.kt
│   │   │   └── VoiceHandler.kt
│   │   └── services/
│   │       └── MeshService.kt       # Background service
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml    # UI layout
│   │   └── values/
│   │       ├── strings.xml
│   │       └── themes.xml
│   └── AndroidManifest.xml          # Permissions & components
└── build.gradle                      # Dependencies
```

## Next Steps

1. **Read Documentation**:
   - `README.md` - Feature overview
   - `ARCHITECTURE.md` - Technical deep dive

2. **Customize**:
   - Modify UI colors in `themes.xml`
   - Adjust mesh parameters in respective managers
   - Add custom message types

3. **Deploy**:
   - Build release APK
   - Test in real-world scenarios
   - Gather feedback

## Support & Resources

### Debugging
- Android Studio Logcat
- Device monitor (adb shell dumpsys)
- Network analyzer apps (Wireshark, nRF Connect)

### Testing Tools
- **nRF Connect**: BLE scanner and debugger
- **WiFi Analyzer**: Wi-Fi Direct monitoring
- **GPS Test**: Verify GPS functionality

## Safety Reminder

⚠️ **This app is for emergency scenarios only**

- Does NOT replace 911/emergency services
- No encryption - messages not private
- No internet - cannot contact remote authorities
- Limited range - devices must be nearby

Use as a complement to official emergency response systems.

---

**You're ready to go! Start with the basic 2-device test, then explore mesh relay with 3+ devices.**
