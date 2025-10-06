# Build Instructions

## Prerequisites Checklist

Before building, ensure you have:

- [ ] Android Studio (Arctic Fox or later)
- [ ] JDK 17
- [ ] Android SDK (API 24-34)
- [ ] NDK 25.1.8937393+
- [ ] CMake 3.22.1+
- [ ] OpenCV Android SDK 4.8.0+ (see OPENCV_SETUP.md)
- [ ] Node.js 18+ (for web viewer)
- [ ] Git

## Step-by-Step Build Guide

### Part 1: Environment Setup

#### 1.1 Install Android Studio

1. Download from https://developer.android.com/studio
2. Run installer with default settings
3. Launch Android Studio
4. Complete setup wizard

#### 1.2 Install SDK Components

1. Open Android Studio
2. Go to: **File → Settings → Appearance & Behavior → System Settings → Android SDK**
3. **SDK Platforms** tab:
   - Check: Android 14.0 (API 34)
   - Check: Android 7.0 (API 24)
4. **SDK Tools** tab:
   - Check: Android SDK Build-Tools
   - Check: NDK (Side by side) - version 25.1.8937393+
   - Check: CMake - version 3.22.1+
   - Check: Android SDK Command-line Tools
5. Click **Apply** and wait for installation

#### 1.3 Install OpenCV Android SDK

**CRITICAL**: Project will not build without this step.

```powershell
# Download OpenCV 4.8.0 Android SDK
# Visit: https://opencv.org/releases/

# Extract the downloaded ZIP file
# You should see: opencv-4.8.0-android-sdk/OpenCV-android-sdk/sdk/

# Copy SDK to project
Copy-Item -Path "path\to\OpenCV-android-sdk\sdk" -Destination "C:\Users\bodap\OneDrive\Desktop\flam\opencv-android-sdk\" -Recurse

# Verify installation
Test-Path "C:\Users\bodap\OneDrive\Desktop\flam\opencv-android-sdk\sdk\native\jni\OpenCVConfig.cmake"
# Should return: True
```

See **OPENCV_SETUP.md** for detailed instructions.

### Part 2: Build Android App

#### 2.1 Open Project

1. Launch Android Studio
2. **File → Open**
3. Navigate to: `C:\Users\bodap\OneDrive\Desktop\flam`
4. Click **OK**

#### 2.2 Sync Gradle

1. Wait for "Gradle sync" to start automatically
2. If it doesn't start: **File → Sync Project with Gradle Files**
3. Wait for sync to complete (may take 2-5 minutes first time)

**Common Issues**:
- If sync fails with "NDK not found": Install NDK via SDK Manager
- If sync fails with "CMake not found": Install CMake via SDK Manager
- If sync fails with "OpenCV not found": Check OPENCV_SETUP.md

#### 2.3 Build Project

```
Method 1: Menu
Build → Make Project (Ctrl+F9)

Method 2: Gradle
./gradlew assembleDebug

Method 3: Terminal (PowerShell)
cd C:\Users\bodap\OneDrive\Desktop\flam
.\gradlew.bat assembleDebug
```

**Expected Output**:
```
> Task :app:externalNativeBuildDebug
C++ build completed successfully
> Task :app:assembleDebug
BUILD SUCCESSFUL in 45s
```

#### 2.4 Run on Device/Emulator

**Option A: Physical Device**

1. Enable Developer Options on your Android device:
   - Settings → About Phone → Tap "Build Number" 7 times
2. Enable USB Debugging:
   - Settings → Developer Options → USB Debugging
3. Connect device via USB
4. Click **Run** (Shift+F10) in Android Studio
5. Select your device
6. Grant camera permission when prompted

**Option B: Emulator**

1. **Tools → Device Manager**
2. Click **Create Device**
3. Select: Pixel 5 (or any device with API 24+)
4. Select: Android 14.0 (API 34)
5. Click **Finish**
6. Click **Run** (Shift+F10)
7. Select emulator
8. Grant camera permission when prompted

### Part 3: Build Web Viewer

#### 3.1 Install Node.js

1. Download from https://nodejs.org/ (LTS version)
2. Run installer with default settings
3. Verify installation:
   ```powershell
   node --version  # Should show v18.x or later
   npm --version   # Should show v9.x or later
   ```

#### 3.2 Build Web Viewer

```powershell
# Navigate to web directory
cd C:\Users\bodap\OneDrive\Desktop\flam\web

# Install dependencies
npm install

# Build TypeScript
npm run build

# Verify build
Test-Path "dist\index.js"  # Should return: True
```

#### 3.3 Run Web Viewer

```powershell
# Start development server
npm start

# Open browser to: http://localhost:3000
```

**Expected Output**:
```
Server running at http://localhost:3000
```

### Part 4: Test Integration

#### 4.1 Test Android App

1. App should launch and show camera preview
2. FPS counter should appear in top-left (showing 30-60 FPS)
3. Click **Toggle Mode** button:
   - Should cycle: Canny → Raw → Grayscale → Canny
4. Click **Save Frame** button:
   - Should show toast: "Frame saved: /path/to/file.txt"

#### 4.2 Test Web Viewer

1. Open http://localhost:3000
2. Should see sample gradient frame
3. Stats panel should show:
   - Resolution: 320 x 240
   - Format: RGBA
   - FPS: 0.0
   - Timestamp: current time

#### 4.3 Test Android → Web Integration

1. Run Android app
2. Click **Save Frame** button
3. Note the file path from toast message
4. Open web viewer (http://localhost:3000)
5. Click **Load Frame File** button
6. Navigate to saved file (usually in `/storage/emulated/0/Android/data/com.flam.edgeviewer/files/Pictures/`)
7. Select the `.txt` file
8. Frame should display in web viewer
9. Stats should update with actual frame resolution

## Troubleshooting

### Build Errors

#### Error: "Could not find OpenCV"

**Solution**:
```powershell
# Verify OpenCV path
Test-Path "C:\Users\bodap\OneDrive\Desktop\flam\opencv-android-sdk\sdk\native\jni\OpenCVConfig.cmake"

# If False, reinstall OpenCV (see OPENCV_SETUP.md)
```

#### Error: "NDK not configured"

**Solution**:
1. File → Settings → Appearance & Behavior → System Settings → Android SDK
2. SDK Tools tab → Check "NDK (Side by side)"
3. Click Apply

#### Error: "CMake executable not found"

**Solution**:
1. File → Settings → Appearance & Behavior → System Settings → Android SDK
2. SDK Tools tab → Check "CMake"
3. Click Apply

#### Error: "Gradle sync failed"

**Solution**:
```powershell
# Clear Gradle cache
cd C:\Users\bodap\OneDrive\Desktop\flam
Remove-Item -Recurse -Force .gradle
Remove-Item -Recurse -Force app\build

# Sync again
# File → Sync Project with Gradle Files
```

### Runtime Errors

#### Error: "Camera permission denied"

**Solution**:
1. Settings → Apps → Edge Viewer → Permissions
2. Enable Camera
3. Restart app

#### Error: "App crashes on launch"

**Solution**:
1. Check Logcat: View → Tool Windows → Logcat
2. Filter by "EdgeViewer" or "NativeProcessor"
3. Look for error messages
4. Common causes:
   - OpenCV library not found: Rebuild project
   - Camera not available: Check device permissions
   - OpenGL not supported: Use newer emulator/device

### Web Viewer Errors

#### Error: "npm install fails"

**Solution**:
```powershell
# Clear npm cache
npm cache clean --force

# Delete node_modules
Remove-Item -Recurse -Force node_modules

# Reinstall
npm install
```

#### Error: "Server won't start"

**Solution**:
```powershell
# Check if port 3000 is in use
netstat -ano | findstr :3000

# Kill process if needed
taskkill /PID <PID> /F

# Restart server
npm start
```

## Build Verification

### Android App Checklist

- [ ] App launches without crashes
- [ ] Camera preview displays
- [ ] FPS counter shows 30-60 FPS
- [ ] Toggle button switches modes
- [ ] Edge detection works (white edges on black background)
- [ ] Grayscale mode works
- [ ] Raw mode shows original camera feed
- [ ] Save button exports frame successfully

### Web Viewer Checklist

- [ ] Page loads at http://localhost:3000
- [ ] Sample frame displays
- [ ] Stats panel shows data
- [ ] File upload button works
- [ ] Can load Android-exported frames
- [ ] Frame displays correctly
- [ ] Stats update with loaded frame

### Performance Checklist

- [ ] FPS ≥ 30 on modern device (2020+)
- [ ] FPS ≥ 10 minimum (requirement)
- [ ] No visible lag or stuttering
- [ ] Smooth mode transitions
- [ ] Quick frame processing (<50ms)

## Build Artifacts

After successful build, you should have:

```
flam/
├── app/build/outputs/apk/debug/
│   └── app-debug.apk          ← Android APK
├── web/dist/
│   └── index.js               ← Compiled TypeScript
└── opencv-android-sdk/
    └── sdk/                   ← OpenCV SDK (not in git)
```

## Next Steps

1. **Test thoroughly**: Try all features
2. **Take screenshots**: For documentation
3. **Record GIF**: Show app in action
4. **Push to GitHub**: Share your work
5. **Update README**: Add screenshots/GIF

## Performance Optimization Tips

### For Better FPS

1. **Lower resolution**: Change target to 640×480 in `CameraController.kt`
2. **Reduce processing**: Use MODE_RAW for testing
3. **Optimize Canny**: Adjust thresholds in `processor.cpp`

### For Lower Latency

1. **Use PBOs**: Pixel Buffer Objects for texture upload
2. **Reduce blur**: Smaller kernel in GaussianBlur
3. **Skip frames**: Process every 2nd frame

### For Lower Memory

1. **Reuse buffers**: Don't allocate new ByteBuffers
2. **Lower resolution**: Smaller frames = less memory
3. **Compress exports**: Use JPEG instead of raw RGBA

## Support

If you encounter issues:

1. Check **Logcat** for Android errors
2. Check **Browser Console** for web errors
3. Review **SETUP.md** for common issues
4. Review **OPENCV_SETUP.md** for OpenCV issues
5. Check **ARCHITECTURE.md** for system understanding

## Build Time Estimates

- **First build**: 5-10 minutes (includes downloads)
- **Clean build**: 2-3 minutes
- **Incremental build**: 10-30 seconds
- **Web build**: 5-10 seconds

---

**Last Updated**: 2025-10-06
**Tested On**: Windows 11, Android Studio Hedgehog, OpenCV 4.8.0
