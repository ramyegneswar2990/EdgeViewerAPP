package com.flam.edgeviewer

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.widget.ImageView
import android.view.View
import android.widget.FrameLayout
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import android.graphics.BitmapFactory
import android.widget.ImageButton

class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var glRenderer: GLRenderer
    private lateinit var cameraController: CameraController
    private var wsStreamer: WebSocketStreamer? = null
    private lateinit var fpsText: TextView
    private lateinit var toggleButton: Button
    private lateinit var saveButton: Button
    private lateinit var thumbnailList: RecyclerView
    private lateinit var imageOverlay: FrameLayout
    private lateinit var imageOverlayView: ImageView
    private lateinit var imageOverlayClose: ImageButton
    private val thumbnailAdapter = ThumbnailAdapter(onClick = { bmp ->
        showOverlay(bmp)
    })

    private var processingMode = NativeProcessor.MODE_CANNY
    private var lastProcessedFrame: ByteBuffer? = null
    private var lastFrameWidth = 0
    private var lastFrameHeight = 0

    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Test native library
        try {
            val testResult = NativeProcessor.testConnection()
            Log.d(TAG, "Native library test: $testResult")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load native library", e)
            Toast.makeText(this, "Failed to load native library", Toast.LENGTH_LONG).show()
        }

        initViews()
        setupGLSurfaceView()
        
        if (checkCameraPermission()) {
            startCameraPreview()
        } else {
            requestCameraPermission()
        }
    }

    private fun initViews() {
        glSurfaceView = findViewById(R.id.glSurfaceView)
        fpsText = findViewById(R.id.fpsText)
        toggleButton = findViewById(R.id.toggleButton)
        saveButton = findViewById(R.id.saveButton)
        thumbnailList = findViewById(R.id.thumbnailList)
        imageOverlay = findViewById(R.id.imageOverlay)
        imageOverlayView = findViewById(R.id.imageOverlayView)
        imageOverlayClose = findViewById(R.id.imageOverlayClose)

        thumbnailList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = thumbnailAdapter
        }

        // Hide thumbnails UI - show images directly instead
        thumbnailList.visibility = View.GONE

        // Tap anywhere on overlay to dismiss
        imageOverlay.setOnClickListener {
            imageOverlay.visibility = View.GONE
            imageOverlayView.setImageDrawable(null)
        }

        // Close button dismisses overlay
        imageOverlayClose.setOnClickListener {
            imageOverlay.visibility = View.GONE
            imageOverlayView.setImageDrawable(null)
        }

        toggleButton.setOnClickListener {
            toggleProcessingMode()
        }

        saveButton.setOnClickListener {
            saveCurrentFrame()
        }

        updateToggleButtonText()
    }

    private fun setupGLSurfaceView() {
        glRenderer = GLRenderer()
        glSurfaceView.apply {
            setEGLContextClientVersion(2)
            setRenderer(glRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        // Update FPS display
        glSurfaceView.post(object : Runnable {
            override fun run() {
                fpsText.text = "FPS: %.1f".format(glRenderer.currentFps)
                glSurfaceView.postDelayed(this, 100)
            }
        })
    }

    private fun startCameraPreview() {
        cameraController = CameraController(this) { yuvData, width, height ->
            processFrame(yuvData, width, height)
        }
        cameraController.startCamera()
    }

    private fun processFrame(yuvData: ByteBuffer, width: Int, height: Int) {
        try {
            val processedData = NativeProcessor.processFrame(yuvData, width, height, processingMode)
            
            if (processedData != null) {
                lastProcessedFrame = processedData
                lastFrameWidth = width
                lastFrameHeight = height
                
                glSurfaceView.queueEvent {
                    glRenderer.updateFrame(processedData, width, height)
                }

                // Stream processed frames over WebSocket when not in RAW mode
                if (processingMode == NativeProcessor.MODE_CANNY || processingMode == NativeProcessor.MODE_GRAYSCALE) {
                    val label = if (processingMode == NativeProcessor.MODE_CANNY) "CANNY" else "GRAYSCALE"
                    wsStreamer?.sendRgbaFrameIfDue(processedData, width, height, label)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error", e)
        }
    }

    private fun toggleProcessingMode() {
        processingMode = when (processingMode) {
            NativeProcessor.MODE_RAW -> NativeProcessor.MODE_GRAYSCALE
            NativeProcessor.MODE_GRAYSCALE -> NativeProcessor.MODE_CANNY
            NativeProcessor.MODE_CANNY -> NativeProcessor.MODE_RAW
            else -> NativeProcessor.MODE_CANNY
        }
        updateToggleButtonText()
    }

    private fun updateToggleButtonText() {
        val modeText = when (processingMode) {
            NativeProcessor.MODE_RAW -> "Raw"
            NativeProcessor.MODE_GRAYSCALE -> "Grayscale"
            NativeProcessor.MODE_CANNY -> "Canny"
            else -> "Unknown"
        }
        toggleButton.text = "Mode: $modeText"
    }

    private fun saveCurrentFrame() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (dir != null && !dir.exists()) dir.mkdirs()
        val ts = System.currentTimeMillis()

        // If user is in Canny or Grayscale mode, save the processed frame
        if (processingMode == NativeProcessor.MODE_CANNY || processingMode == NativeProcessor.MODE_GRAYSCALE) {
            val frame = lastProcessedFrame ?: run {
                Toast.makeText(this, "No processed frame to save", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val pngFile = File(dir, "processed_${ts}.png")
                frame.rewind()
                val bytes = ByteArray(frame.remaining())
                frame.get(bytes)
                frame.rewind()
                val bmp = rgbaToBitmap(bytes, lastFrameWidth, lastFrameHeight)
                FileOutputStream(pngFile).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                showOverlay(bmp)
                // Also send to WebSocket immediately
                wsStreamer?.sendBitmap(bmp, lastFrameWidth, lastFrameHeight, if (processingMode == NativeProcessor.MODE_CANNY) "CANNY" else "GRAYSCALE")
                Toast.makeText(this, "Saved Processed: ${pngFile.name}", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Processed saved to: ${pngFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save processed frame", e)
                Toast.makeText(this, "Failed to save processed photo", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Otherwise (RAW mode), save a high-res JPEG from the camera
        if (!::cameraController.isInitialized) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }
        val jpgFile = File(dir, "photo_${ts}.jpg")
        cameraController.captureStill { jpegBytes ->
            try {
                FileOutputStream(jpgFile).use { fos -> fos.write(jpegBytes) }
                val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                runOnUiThread {
                    showOverlay(bmp)
                    // Also send to WebSocket immediately
                    wsStreamer?.sendBitmap(bmp, bmp.width, bmp.height, "JPEG")
                    Toast.makeText(this, "Saved Photo: ${jpgFile.name}", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Photo saved to: ${jpgFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save JPEG", e)
                runOnUiThread {
                    Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun rgbaToBitmap(bytes: ByteArray, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        // RGBA to ARGB_8888 int buffer
        val intBuf = IntArray(width * height)
        var i = 0
        var p = 0
        while (i < bytes.size && p < intBuf.size) {
            val r = bytes[i].toInt() and 0xFF
            val g = bytes[i + 1].toInt() and 0xFF
            val b = bytes[i + 2].toInt() and 0xFF
            val a = bytes[i + 3].toInt() and 0xFF
            intBuf[p] = (a shl 24) or (r shl 16) or (g shl 8) or b
            i += 4
            p += 1
        }
        bmp.setPixels(intBuf, 0, width, 0, 0, width, height)
        return bmp
    }

    private fun showOverlay(bmp: Bitmap) {
        imageOverlayView.setImageBitmap(bmp)
        imageOverlay.visibility = View.VISIBLE
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraPreview()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permission_required),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        if (::cameraController.isInitialized) {
            cameraController.stopCamera()
        }
        wsStreamer?.disconnect()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        if (::cameraController.isInitialized && checkCameraPermission()) {
            cameraController.startCamera()
        }
        // Connect WebSocket streamer
        if (wsStreamer == null) wsStreamer = WebSocketStreamer()
        // Using provided PC IP on the same network
        wsStreamer?.connect("ws://10.1.174.76:8080/frames")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraController.isInitialized) {
            cameraController.release()
        }
    }
}
