# Setup Guide - Edge Viewer

## Quick Start

### 1. Download OpenCV Android SDK

**IMPORTANT**: You must download OpenCV Android SDK before building the project.

1. Visit [OpenCV Releases](https://opencv.org/releases/)
2. Download **OpenCV 4.8.0** (or later) for Android
3. Extract the downloaded file
4. Copy the `sdk` folder to: `flam/opencv-android-sdk/sdk/`

Your directory structure should look like:
```
flam/
├── opencv-android-sdk/
│   └── sdk/
│       ├── native/
│       │   └── jni/
│       │       ├── abi-arm64-v8a/
│       │       ├── abi-armeabi-v7a/
│       │       └── OpenCVConfig.cmake
│       └── java/
└── app/
```

### 2. Install Android Studio

1. Download [Android Studio](https://developer.android.com/studio)
2. Install with default settings
3. Open Android Studio and install:
   - Android SDK (API 24-34)
   - NDK (version 25.1.8937393 or later)
   - CMake (3.22.1 or later)

### 3. Build Android App

```bash
# Clone or navigate to project
cd flam

# Open in Android Studio
# File → Open → Select 'flam' folder

# Sync Gradle (should happen automatically)
# Build → Make Project (Ctrl+F9)

# Connect Android device or start emulator
# Run → Run 'app' (Shift+F10)
```

### 4. Build Web Viewer

```bash
cd web

# Install dependencies
npm install

# Build TypeScript
npm run build

# Start local server
npm start

# Open browser to http://localhost:3000
```

## Troubleshooting

### OpenCV Not Found

**Error**: `Could not find OpenCV`

**Solution**:
1. Verify `opencv-android-sdk/sdk/native/jni/` exists
2. Check CMakeLists.txt path matches your structure
3. Clean and rebuild: Build → Clean Project → Rebuild Project

### NDK/CMake Issues

**Error**: `CMake not found` or `NDK not configured`

**Solution**:
1. File → Settings → Appearance & Behavior → System Settings → Android SDK
2. SDK Tools tab → Check:
   - NDK (Side by side)
   - CMake
3. Click Apply and let Android Studio install

### Camera Permission Denied

**Solution**:
1. Settings → Apps → Edge Viewer → Permissions
2. Enable Camera permission
3. Restart app

### Web Viewer Not Loading

**Solution**:
```bash
cd web
rm -rf node_modules dist
npm install
npm run build
npm start
```

## Testing

### Android App
1. Grant camera permission when prompted
2. App should show live camera feed with edge detection
3. Click "Toggle Mode" to switch between Raw/Grayscale/Canny
4. FPS counter should show 30-60 FPS on modern devices
5. Click "Save Frame" to export processed frame

### Web Viewer
1. Open http://localhost:3000
2. Should see sample gradient frame
3. Click "Load Frame File" to upload Android export
4. Stats panel should update with frame info

## Performance Tips

- **Target Resolution**: 1280x720 (optimal for real-time processing)
- **Expected FPS**: 30-60 on mid-range devices (2020+)
- **Minimum FPS**: 10-15 (requirement met on most devices)

## Next Steps

1. Run Android app and save a frame
2. Load saved frame in web viewer
3. Implement WebSocket server for live streaming (optional)
4. Add custom OpenCV filters (optional)

## Support

For issues, check:
- Android Logcat: View → Tool Windows → Logcat
- Browser Console: F12 → Console tab
- CMake output: Build → Build Output

---

**Ready to build!** Follow the steps above and you'll have the app running in ~15 minutes.
