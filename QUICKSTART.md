# Quick Start Guide ⚡

Get the Edge Viewer app running in **under 10 minutes**.

## 🎯 Prerequisites

- [ ] Windows 10/11
- [ ] Android Studio installed
- [ ] Android device or emulator (API 24+)
- [ ] Node.js 18+ (for web viewer)

## 📥 Step 1: Download OpenCV (2 minutes)

**CRITICAL**: The app won't build without OpenCV.

1. Visit: https://opencv.org/releases/
2. Download: **OpenCV 4.8.0 Android SDK** (~200 MB)
3. Extract the ZIP file
4. Copy the `sdk` folder to:
   ```
   C:\Users\bodap\OneDrive\Desktop\flam\opencv-android-sdk\sdk\
   ```

**Verify**:
```powershell
Test-Path "C:\Users\bodap\OneDrive\Desktop\flam\opencv-android-sdk\sdk\native\jni\OpenCVConfig.cmake"
# Should return: True
```

## 🔨 Step 2: Build Android App (3 minutes)

1. **Open Android Studio**
2. **File → Open** → Select `C:\Users\bodap\OneDrive\Desktop\flam`
3. Wait for Gradle sync (automatic)
4. **Build → Make Project** (Ctrl+F9)
5. Wait for build to complete (~2-3 minutes first time)

**Expected Output**:
```
BUILD SUCCESSFUL in 2m 15s
```

## 📱 Step 3: Run on Device (1 minute)

### Option A: Physical Device

1. Enable **Developer Options** on your phone:
   - Settings → About Phone → Tap "Build Number" 7 times
2. Enable **USB Debugging**:
   - Settings → Developer Options → USB Debugging
3. Connect via USB
4. Click **Run** (Shift+F10) in Android Studio
5. Select your device
6. Grant camera permission when prompted

### Option B: Emulator

1. **Tools → Device Manager**
2. Click **Create Device** (if no emulator exists)
3. Select: **Pixel 5**, **API 34**
4. Click **Run** (Shift+F10)
5. Select emulator
6. Grant camera permission when prompted

## ✅ Step 4: Test the App (1 minute)

1. **App should launch** and show camera preview
2. **FPS counter** should appear in top-left (30-60 FPS)
3. Click **Toggle Mode** button:
   - Canny (white edges on black)
   - Raw (normal camera)
   - Grayscale (black & white)
4. Click **Save Frame** button:
   - Should show: "Frame saved: /path/to/file.txt"

**If you see the camera preview with edge detection, you're done! 🎉**

## 🌐 Step 5: Web Viewer (Optional, 2 minutes)

```powershell
# Navigate to web directory
cd C:\Users\bodap\OneDrive\Desktop\flam\web

# Install dependencies
npm install

# Build and start
npm run build
npm start

# Open browser to: http://localhost:3000
```

**Test**:
1. Should see sample gradient frame
2. Click **Load Frame File**
3. Select saved frame from Android app
4. Frame should display with stats

## 🎊 You're Done!

The app is now running with:
- ✅ Real-time camera feed
- ✅ OpenCV edge detection
- ✅ OpenGL ES rendering
- ✅ 30-60 FPS performance
- ✅ Web viewer ready

## 🚨 Troubleshooting

### "Could not find OpenCV"

**Fix**: Verify OpenCV SDK is in the correct location:
```powershell
Test-Path "C:\Users\bodap\OneDrive\Desktop\flam\opencv-android-sdk\sdk\native\jni\OpenCVConfig.cmake"
```
If False, re-download and extract OpenCV SDK.

### "NDK not configured"

**Fix**: 
1. File → Settings → Android SDK
2. SDK Tools tab → Check "NDK (Side by side)"
3. Click Apply

### "Camera permission denied"

**Fix**:
1. Settings → Apps → Edge Viewer → Permissions
2. Enable Camera
3. Restart app

### App crashes on launch

**Fix**: Check Logcat (View → Tool Windows → Logcat) for errors.

## 📚 More Information

- **Detailed build**: See `BUILD_INSTRUCTIONS.md`
- **OpenCV setup**: See `OPENCV_SETUP.md`
- **Architecture**: See `ARCHITECTURE.md`
- **Full docs**: See `README.md`

## 🎯 What's Next?

1. **Take screenshots** of the running app
2. **Record a GIF** showing edge detection
3. **Test all modes** (Raw, Grayscale, Canny)
4. **Export frames** and load in web viewer
5. **Push to GitHub** and share your work

## ⏱️ Time Breakdown

- OpenCV download: 2 minutes
- Android build: 3 minutes
- Run on device: 1 minute
- Test app: 1 minute
- Web viewer: 2 minutes (optional)

**Total**: ~7-10 minutes

---

**Need help?** Check the detailed guides in the project root or review the Git commit history for implementation details.

**Ready to submit?** See `SUBMISSION.md` for the complete checklist.
