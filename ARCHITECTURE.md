# Architecture Documentation

## System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      Android Application                     │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────┐    │
│  │ MainActivity│→ │CameraController│→│  ImageReader     │    │
│  │   (Kotlin) │  │   (Camera2)   │  │ (YUV_420_888)   │    │
│  └─────┬──────┘  └──────────────┘  └────────┬─────────┘    │
│        │                                      │               │
│        │ ┌────────────────────────────────────┘              │
│        │ │                                                    │
│        ▼ ▼                                                    │
│  ┌──────────────┐         ┌─────────────────┐               │
│  │ GLSurfaceView│◄────────│   GLRenderer    │               │
│  │  (Display)   │         │  (OpenGL ES 2.0)│               │
│  └──────────────┘         └────────▲────────┘               │
│                                     │                         │
│        ┌────────────────────────────┘                        │
│        │                                                      │
│  ┌─────▼──────────┐                                          │
│  │ NativeProcessor│ (JNI Bridge)                             │
│  │    (Kotlin)    │                                          │
│  └────────┬───────┘                                          │
└───────────┼──────────────────────────────────────────────────┘
            │ JNI Call
            ▼
┌───────────────────────────────────────────────────────────────┐
│                    Native C++ Layer (NDK)                     │
│  ┌──────────────────────────────────────────────────────┐    │
│  │                   processor.cpp                       │    │
│  │                                                       │    │
│  │  processFrame(yuv, width, height, mode)              │    │
│  │    ├─ YUV → RGBA conversion                          │    │
│  │    ├─ MODE_RAW: Return as-is                         │    │
│  │    ├─ MODE_GRAYSCALE: cv::cvtColor()                 │    │
│  │    └─ MODE_CANNY: cv::Canny()                        │    │
│  │                                                       │    │
│  │  Uses: OpenCV 4.8.0 (C++ API)                        │    │
│  └──────────────────────────────────────────────────────┘    │
└───────────────────────────────────────────────────────────────┘
            │
            ▼ RGBA ByteBuffer
┌───────────────────────────────────────────────────────────────┐
│                      Web Viewer (TypeScript)                  │
│  ┌──────────────┐  ┌─────────────┐  ┌──────────────┐        │
│  │  index.html  │→ │  index.ts   │→ │   Canvas     │        │
│  │   (UI)       │  │ (Logic)     │  │  (Display)   │        │
│  └──────────────┘  └─────────────┘  └──────────────┘        │
│                                                                │
│  Input: Base64 RGBA frame from Android export                 │
│  Output: Visual display + stats (FPS, resolution)             │
└───────────────────────────────────────────────────────────────┘
```

## Data Flow

### 1. Camera Capture (Camera2 API)

```kotlin
CameraController.startCamera()
  → CameraDevice.createCaptureSession()
  → ImageReader.setOnImageAvailableListener()
  → onImageAvailable(ImageReader)
    → image.planes[0,1,2] (Y, U, V planes)
    → ByteBuffer (YUV_420_888 format)
```

**Format**: YUV_420_888 (Android standard)
- Y plane: Luminance (width × height)
- U plane: Chrominance (width/2 × height/2)
- V plane: Chrominance (width/2 × height/2)

### 2. JNI Bridge

```kotlin
// Kotlin side
val processedData = NativeProcessor.processFrame(yuvData, width, height, mode)

// Native side (C++)
JNIEXPORT jobject JNICALL Java_com_flam_edgeviewer_NativeProcessor_processFrame(
    JNIEnv *env, jobject, jobject yuvBuffer, jint width, jint height, jint mode)
```

**Data Transfer**:
- Input: Direct ByteBuffer (zero-copy)
- Output: New ByteBuffer (RGBA format)

### 3. OpenCV Processing

```cpp
// YUV → RGBA conversion
cv::Mat yuvMat(height + height/2, width, CV_8UC1, yuvData);
cv::Mat rgbaMat(height, width, CV_8UC4);
cv::cvtColor(yuvMat, rgbaMat, cv::COLOR_YUV2RGBA_NV21);

// Processing modes
switch (mode) {
    case MODE_CANNY:
        cv::cvtColor(rgbaMat, grayMat, cv::COLOR_RGBA2GRAY);
        cv::GaussianBlur(grayMat, blurredMat, cv::Size(5,5), 1.5);
        cv::Canny(blurredMat, edgesMat, 50, 150);
        cv::cvtColor(edgesMat, processedMat, cv::COLOR_GRAY2RGBA);
        break;
}
```

**Performance**:
- YUV conversion: ~5ms
- Canny detection: ~10-15ms
- Total: ~20ms per frame (50 FPS theoretical max)

### 4. OpenGL Rendering

```kotlin
// Upload texture
GLES20.glTexImage2D(
    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
    width, height, 0,
    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
    processedData
)

// Draw quad
GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
```

**Shaders**:
- Vertex: Pass-through with texture coordinates
- Fragment: Simple texture sampling

### 5. Frame Export

```kotlin
// Save to file
val base64 = Base64.encodeToString(frameBytes, Base64.NO_WRAP)
file.write("Width: $width\nHeight: $height\nFormat: RGBA\nBase64:\n$base64")
```

### 6. Web Display

```typescript
// Load frame
const img = new Image();
img.src = 'data:image/png;base64,' + frame.base64;
img.onload = () => {
    ctx.drawImage(img, 0, 0, width, height);
};
```

## Component Details

### MainActivity.kt

**Responsibilities**:
- UI lifecycle management
- Permission handling
- Mode switching (Raw/Grayscale/Canny)
- Frame export coordination

**Key Methods**:
- `startCameraPreview()`: Initialize camera
- `processFrame()`: Send frame to native processing
- `toggleProcessingMode()`: Switch between modes
- `saveCurrentFrame()`: Export frame as base64

### CameraController.kt

**Responsibilities**:
- Camera2 API management
- Frame capture configuration
- YUV buffer handling

**Key Methods**:
- `startCamera()`: Open camera and create session
- `createCaptureSession()`: Configure repeating capture
- `processImage()`: Extract YUV planes
- `selectOptimalSize()`: Choose best resolution

**Configuration**:
- Target: 1280×720 (optimal balance)
- Format: YUV_420_888
- Capture mode: Repeating (continuous)

### GLRenderer.kt

**Responsibilities**:
- OpenGL ES 2.0 context management
- Texture upload and rendering
- FPS calculation

**Key Methods**:
- `onSurfaceCreated()`: Initialize shaders and textures
- `onDrawFrame()`: Render frame
- `updateFrame()`: Receive new frame data

**Performance Optimizations**:
- Direct ByteBuffer (no copy)
- Texture reuse (single texture ID)
- Triangle strip rendering (4 vertices)

### processor.cpp

**Responsibilities**:
- OpenCV integration
- Image format conversion
- Edge detection algorithms

**Key Functions**:
- `processFrame()`: Main processing pipeline
- `yuv420ToRgba()`: Color space conversion

**OpenCV Operations**:
- `cv::cvtColor()`: Color conversion
- `cv::GaussianBlur()`: Noise reduction
- `cv::Canny()`: Edge detection

### Web Viewer (index.ts)

**Responsibilities**:
- Frame display
- Statistics overlay
- File upload handling
- WebSocket mock

**Key Classes**:
- `EdgeViewerWeb`: Main application class
- `FrameData`: Frame metadata interface

## Performance Characteristics

### Latency Breakdown

```
Camera capture:        16ms (60 FPS)
YUV → RGBA:            5ms
OpenCV processing:     10-15ms
Texture upload:        2ms
OpenGL render:         1ms
─────────────────────────────
Total:                 34-39ms (25-30 FPS)
```

### Memory Usage

- YUV buffer: width × height × 1.5 bytes
- RGBA buffer: width × height × 4 bytes
- Example (1280×720):
  - YUV: 1.38 MB
  - RGBA: 3.69 MB
  - Total per frame: ~5 MB

### Threading Model

```
Main Thread:
  ├─ UI updates
  ├─ GLSurfaceView rendering
  └─ Frame display

Camera Thread:
  ├─ Camera callbacks
  ├─ Image capture
  └─ Native processing calls

GL Thread:
  ├─ OpenGL operations
  └─ Texture uploads
```

## Build System

### Gradle (Android)

```
app/build.gradle.kts
  ├─ Android plugin
  ├─ Kotlin plugin
  ├─ NDK configuration
  └─ CMake external build
```

### CMake (Native)

```
CMakeLists.txt
  ├─ Find OpenCV package
  ├─ Include directories
  ├─ Add native library
  └─ Link OpenCV libs
```

### TypeScript (Web)

```
tsconfig.json
  ├─ ES2020 target
  ├─ DOM lib
  └─ Strict mode
```

## Testing Strategy

### Unit Tests
- Native: OpenCV operations (C++)
- Android: Camera controller, JNI bridge
- Web: Frame parsing, display logic

### Integration Tests
- Camera → Native → OpenGL pipeline
- Frame export → Web viewer

### Performance Tests
- FPS benchmarking
- Memory profiling
- Latency measurement

## Future Enhancements

1. **Real-time WebSocket streaming**
   - Android HTTP server
   - Frame streaming protocol
   - Web viewer live updates

2. **Additional filters**
   - Sobel edge detection
   - Bilateral filter
   - Morphological operations

3. **GPU acceleration**
   - OpenGL compute shaders
   - OpenCV GPU module (CUDA)

4. **Multi-camera support**
   - Front/back camera switching
   - Dual camera processing

---

**Last Updated**: 2025-10-06
**Version**: 1.0.0
