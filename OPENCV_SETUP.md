# OpenCV Android SDK Setup Guide

## Download OpenCV

### Option 1: Direct Download (Recommended)

1. Visit: https://opencv.org/releases/
2. Scroll to **Android** section
3. Download: **OpenCV 4.8.0 Android SDK** (or latest 4.x version)
4. File size: ~200 MB

### Option 2: GitHub Releases

```bash
# Download via command line (Windows PowerShell)
Invoke-WebRequest -Uri "https://github.com/opencv/opencv/releases/download/4.8.0/opencv-4.8.0-android-sdk.zip" -OutFile "opencv-android-sdk.zip"

# Extract
Expand-Archive -Path "opencv-android-sdk.zip" -DestinationPath "."
```

## Installation

### Step 1: Extract SDK

After downloading, extract the ZIP file. You should see:

```
opencv-4.8.0-android-sdk/
├── OpenCV-android-sdk/
│   └── sdk/
│       ├── native/
│       │   ├── jni/
│       │   │   ├── abi-arm64-v8a/
│       │   │   ├── abi-armeabi-v7a/
│       │   │   ├── abi-x86/
│       │   │   ├── abi-x86_64/
│       │   │   ├── include/
│       │   │   └── OpenCVConfig.cmake
│       │   ├── libs/
│       │   └── staticlibs/
│       └── java/
```

### Step 2: Copy to Project

Copy the **entire `sdk` folder** to your project:

```
flam/
├── opencv-android-sdk/
│   └── sdk/              ← Copy this folder here
│       ├── native/
│       └── java/
├── app/
├── web/
└── ...
```

**Command (Windows PowerShell):**

```powershell
# From the extracted opencv folder
Copy-Item -Path "OpenCV-android-sdk\sdk" -Destination "C:\Users\bodap\OneDrive\Desktop\flam\opencv-android-sdk\" -Recurse
```

### Step 3: Verify Installation

Check that these files exist:

```
flam/opencv-android-sdk/sdk/native/jni/OpenCVConfig.cmake
flam/opencv-android-sdk/sdk/native/jni/abi-arm64-v8a/libopencv_java4.so
flam/opencv-android-sdk/sdk/native/jni/include/opencv2/opencv.hpp
```

## Troubleshooting

### Error: "Could not find OpenCV"

**Cause**: CMake cannot locate OpenCV SDK

**Solution**:

1. Verify folder structure matches exactly:
   ```
   flam/opencv-android-sdk/sdk/native/jni/
   ```

2. Check `app/src/main/cpp/CMakeLists.txt` line 9:
   ```cmake
   set(OpenCV_DIR "${CMAKE_SOURCE_DIR}/../../../../../opencv-android-sdk/sdk/native/jni")
   ```

3. If you placed SDK in a different location, update the path

### Error: "libopencv_java4.so not found"

**Cause**: Native libraries not included in APK

**Solution**:

Add to `app/build.gradle.kts`:

```kotlin
android {
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("../opencv-android-sdk/sdk/native/libs")
        }
    }
}
```

### Error: "OpenCV version mismatch"

**Cause**: Using OpenCV 3.x instead of 4.x

**Solution**: Download OpenCV 4.8.0 or later (4.x series)

## Alternative: Manual Build (Advanced)

If you want to build OpenCV from source:

```bash
# Clone OpenCV
git clone https://github.com/opencv/opencv.git
cd opencv
git checkout 4.8.0

# Build for Android (requires Android NDK)
mkdir build && cd build
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-24 \
      ..
make -j8
```

## Verification

After setup, build the project:

```bash
# In Android Studio
Build → Clean Project
Build → Rebuild Project

# Check build output for:
"-- Found OpenCV: 4.8.0"
```

## Next Steps

Once OpenCV is installed:

1. Open project in Android Studio
2. Sync Gradle files
3. Build → Make Project
4. Run on device/emulator

---

**Need help?** Check `SETUP.md` for full build instructions.
