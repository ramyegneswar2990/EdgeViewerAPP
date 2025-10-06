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
    
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var targetWidth = 1280
    private var targetHeight = 720

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

            // Create ImageReader
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

        try {
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            }

            camera.createCaptureSession(
                listOf(reader.surface),
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
        // Convert YUV_420_888 to byte buffer
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val ySize = yPlane.buffer.remaining()
        val uSize = uPlane.buffer.remaining()
        val vSize = vPlane.buffer.remaining()

        val data = ByteBuffer.allocateDirect(ySize + uSize + vSize)
        
        yPlane.buffer.rewind()
        uPlane.buffer.rewind()
        vPlane.buffer.rewind()
        
        data.put(yPlane.buffer)
        data.put(uPlane.buffer)
        data.put(vPlane.buffer)
        
        data.rewind()

        onFrameAvailable(data, image.width, image.height)
    }

    private fun selectCamera(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    private fun selectOptimalSize(sizes: Array<Size>): Size {
        // Prefer 1280x720 or closest
        val preferred = sizes.firstOrNull { it.width == 1280 && it.height == 720 }
        if (preferred != null) return preferred

        // Find closest to 1280x720
        return sizes.minByOrNull {
            Math.abs(it.width - 1280) + Math.abs(it.height - 720)
        } ?: Size(1280, 720)
    }

    fun stopCamera() {
        captureSession?.close()
        captureSession = null
        
        cameraDevice?.close()
        cameraDevice = null
        
        imageReader?.close()
        imageReader = null
    }

    fun release() {
        stopCamera()
        cameraThread.quitSafely()
    }
}
