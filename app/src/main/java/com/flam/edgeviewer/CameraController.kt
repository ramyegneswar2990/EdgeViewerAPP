package com.flam.edgeviewer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer

/**
 * Camera2 API controller for capturing frames
 */
class CameraController(
    private val context: Context,
    private val onFrameAvailable: (ByteBuffer, Int, Int) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var jpegReader: ImageReader? = null
    private var pendingStillCallback: ((ByteArray) -> Unit)? = null
    
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var targetWidth = 1280
    private var targetHeight = 720
    private var rotationDegrees = 0
    private var sensorOrientation = 0
    private var deviceRotationDegrees = 0

    companion object {
        private const val TAG = "CameraController"
        const val PERMISSION_REQUEST_CODE = 100
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startCamera() {
        if (!hasPermission()) {
            Log.e(TAG, "Camera permission not granted")
            return
        }

        try {
            val cameraId = selectCamera() ?: run {
                Log.e(TAG, "No suitable camera found")
                return
            }

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            
            // Select optimal size
            val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
            val optimalSize = selectOptimalSize(sizes)
            targetWidth = optimalSize.width
            targetHeight = optimalSize.height

            Log.d(TAG, "Selected camera size: ${targetWidth}x${targetHeight}")

            // Compute relative rotation between device and sensor
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val deviceRotation = try {
                // defaultDisplay is deprecated but sufficient; fallback to ROTATION_0 if unavailable
                val rot = wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                when (rot) {
                    Surface.ROTATION_0 -> 0
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> 0
                }
            } catch (t: Throwable) { 0 }
            rotationDegrees = (sensorOrientation - deviceRotation + 360) % 360
            deviceRotationDegrees = deviceRotation
            Log.d(TAG, "Sensor=${sensorOrientation}, device=${deviceRotation}, relative=${rotationDegrees}")

            // Create ImageReader for preview/processing (YUV)
            imageReader = ImageReader.newInstance(
                targetWidth,
                targetHeight,
                ImageFormat.YUV_420_888,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        processImage(image)
                    } finally {
                        image.close()
                    }
                }, cameraHandler)
            }

            // Create JPEG ImageReader at highest supported resolution for still capture
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
            val jpegSize = if (jpegSizes.isNotEmpty()) {
                jpegSizes.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: Size(targetWidth, targetHeight)
            } else Size(targetWidth, targetHeight)

            jpegReader = ImageReader.newInstance(
                jpegSize.width,
                jpegSize.height,
                ImageFormat.JPEG,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        pendingStillCallback?.invoke(bytes)
                    } catch (t: Throwable) {
                        Log.e(TAG, "JPEG read failed", t)
                    } finally {
                        pendingStillCallback = null
                        image.close()
                    }
                }, cameraHandler)
            }

            // Open camera
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, cameraHandler)

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied", e)
        }
    }

    private fun createCaptureSession() {
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return
        val jpegOut = jpegReader

        try {
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                // Improve sharpness: enable continuous AF/AE/AWB and high quality processing if supported
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
            }

            // Include JPEG surface in the session so we can capture stills without reconfiguring
            val outputs = if (jpegOut != null) listOf(reader.surface, jpegOut.surface) else listOf(reader.surface)

            camera.createCaptureSession(
                outputs,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(
                                captureRequestBuilder.build(),
                                null,
                                cameraHandler
                            )
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Failed to start capture", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure capture session")
                    }
                },
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to create capture session", e)
        }
    }

    private fun processImage(image: android.media.Image) {
        // Convert YUV_420_888 to NV21 and rotate to match device orientation.
        val w = image.width
        val h = image.height

        val nv21 = yuv420888ToNv21(image)

        val rotated: ByteArray
        val outW: Int
        val outH: Int
        when (rotationDegrees) {
            90 -> {
                rotated = rotateNV21(nv21, w, h, 90)
                outW = h; outH = w
            }
            180 -> {
                rotated = rotateNV21(nv21, w, h, 180)
                outW = w; outH = h
            }
            270 -> {
                rotated = rotateNV21(nv21, w, h, 270)
                outW = h; outH = w
            }
            else -> {
                rotated = nv21
                outW = w; outH = h
            }
        }

        val data = ByteBuffer.allocateDirect(rotated.size)
        data.put(rotated)
        data.rewind()

        onFrameAvailable(data, outW, outH)
    }

    private fun selectCamera(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    private fun selectOptimalSize(sizes: Array<Size>): Size {
        if (sizes.isEmpty()) return Size(1280, 720)

        // Prefer up to 1920x1080 (Full HD) if available, else choose the largest by area
        val fullHd = sizes.filter { it.width * it.height <= 1920 * 1080 }
        val candidateList = if (fullHd.isNotEmpty()) fullHd else sizes.toList()

        // Keep aspect ratio close to 16:9 to avoid stretching in preview
        fun aspectDiff(s: Size): Double {
            val ar = s.width.toDouble() / s.height
            return kotlin.math.abs(ar - (16.0 / 9.0))
        }

        return candidateList
            .sortedWith(compareBy<Size> { aspectDiff(it) }
                .thenByDescending { it.width * it.height })
            .first()
    }

    fun stopCamera() {
        captureSession?.close()
        captureSession = null
        
        cameraDevice?.close()
        cameraDevice = null
        
        imageReader?.close()
        imageReader = null
        jpegReader?.close()
        jpegReader = null
    }

    fun release() {
        stopCamera()
        cameraThread.quitSafely()
    }

    // Public API: capture a high-resolution JPEG still
    fun captureStill(onJpegReady: (ByteArray) -> Unit) {
        val session = captureSession ?: run {
            Log.e(TAG, "No capture session for still")
            return
        }
        val camera = cameraDevice ?: return
        val jpegOut = jpegReader ?: run {
            Log.e(TAG, "No JPEG reader")
            return
        }

        pendingStillCallback = onJpegReady

        try {
            // 1) Trigger AF to help lock focus
            val afTrigger = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(imageReader!!.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            }.build()
            session.capture(afTrigger, null, cameraHandler)

            // 2) Capture still after a brief delay
            cameraHandler.postDelayed({
                try {
                    // Standard Camera2 orientation mapping for BACK camera
                    val mapped = when (deviceRotationDegrees) {
                        0 -> 90
                        90 -> 0
                        180 -> 270
                        270 -> 180
                        else -> 0
                    }
                    val jpegOrientation = (mapped + sensorOrientation) % 360
                    val capture = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(jpegOut.surface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                        set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                        set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)
                        set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
                        set(CaptureRequest.JPEG_QUALITY, 100.toByte())
                    }.build()
                    session.capture(capture, null, cameraHandler)
                } catch (e: CameraAccessException) {
                    Log.e(TAG, "Still capture failed", e)
                    pendingStillCallback = null
                }
            }, 200)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Still capture failed", e)
            pendingStillCallback = null
        }
    }

    // -------- Helpers: YUV conversion and rotation --------

    private fun yuv420888ToNv21(image: android.media.Image): ByteArray {
        val w = image.width
        val h = image.height
        val ySize = w * h
        val uvSize = w * h / 2
        val out = ByteArray(ySize + uvSize)

        // Copy Y
        val yPlane = image.planes[0]
        copyPlane(yPlane.buffer, yPlane.rowStride, yPlane.pixelStride, w, h, out, 0)

        // NV21 expects interleaved VU. Camera2 provides U and V separate with strides.
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val chromaRowStride = uPlane.rowStride
        val chromaPixelStride = uPlane.pixelStride // usually 2

        var outputOffset = ySize
        val chromaHeight = h / 2
        val chromaWidth = w / 2
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        uBuffer.rewind()
        vBuffer.rewind()

        // Iterate over each chroma pixel and write V then U
        for (row in 0 until chromaHeight) {
            var uRowPos = row * chromaRowStride
            var vRowPos = row * vPlane.rowStride
            for (col in 0 until chromaWidth) {
                val uIndex = uRowPos + col * chromaPixelStride
                val vIndex = vRowPos + col * vPlane.pixelStride
                out[outputOffset++] = vBuffer.get(vIndex)
                out[outputOffset++] = uBuffer.get(uIndex)
            }
        }

        return out
    }

    private fun copyPlane(src: ByteBuffer, rowStride: Int, pixelStride: Int, width: Int, height: Int, dst: ByteArray, offset: Int) {
        src.rewind()
        var outPos = offset
        val rowData = ByteArray(rowStride)
        for (row in 0 until height) {
            val length: Int
            if (pixelStride == 1) {
                // when tightly packed, just copy
                length = width
                src.get(dst, outPos, length)
                outPos += length
                // skip remaining row bytes
                val skip = rowStride - width
                if (skip > 0) src.position(src.position() + skip)
            } else {
                // when not tightly packed, read a full row and pick pixels
                length = (width - 1) * pixelStride + 1
                src.get(rowData, 0, length)
                var colOut = outPos
                for (col in 0 until width) {
                    dst[colOut++] = rowData[col * pixelStride]
                }
                outPos = colOut
                val skip = rowStride - length
                if (skip > 0) src.position(src.position() + skip)
            }
        }
    }

    private fun rotateNV21(data: ByteArray, width: Int, height: Int, rotation: Int): ByteArray {
        return when (rotation) {
            90 -> rotateNV21_90(data, width, height)
            180 -> rotateNV21_180(data, width, height)
            270 -> rotateNV21_270(data, width, height)
            else -> data
        }
    }

    private fun rotateNV21_90(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val out = ByteArray(ySize + ySize / 2)

        var i = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                out[i++] = data[y * width + x]
            }
        }

        // UV plane
        val uvWidth = width
        val uvHeight = height / 2
        val uvIn = ySize
        var o = ySize
        for (x in 0 until uvWidth step 2) {
            for (y in uvHeight - 1 downTo 0) {
                val pos = uvIn + y * uvWidth + x
                out[o++] = data[pos]
                out[o++] = data[pos + 1]
            }
        }
        return out
    }

    private fun rotateNV21_180(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val out = ByteArray(ySize + ySize / 2)

        // Y
        var i = 0
        for (p in ySize - 1 downTo 0) out[i++] = data[p]
        // UV
        val uvStart = ySize
        for (p in data.size - 1 downTo uvStart step 2) {
            out[i++] = data[p - 1]
            out[i++] = data[p]
        }
        return out
    }

    private fun rotateNV21_270(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val out = ByteArray(ySize + ySize / 2)

        var i = 0
        for (x in width - 1 downTo 0) {
            for (y in 0 until height) {
                out[i++] = data[y * width + x]
            }
        }

        // UV
        val uvWidth = width
        val uvHeight = height / 2
        val uvIn = ySize
        var o = ySize
        for (x in uvWidth - 2 downTo 0 step 2) {
            for (y in 0 until uvHeight) {
                val pos = uvIn + y * uvWidth + x
                out[o++] = data[pos]
                out[o++] = data[pos + 1]
            }
        }
        return out
    }
}
