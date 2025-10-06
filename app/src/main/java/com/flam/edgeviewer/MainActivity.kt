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
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var glRenderer: GLRenderer
    private lateinit var cameraController: CameraController
    private lateinit var fpsText: TextView
    private lateinit var toggleButton: Button
    private lateinit var saveButton: Button

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
        val frame = lastProcessedFrame ?: run {
            Toast.makeText(this, "No frame to save", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Save as PNG
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val file = File(dir, "edge_frame_${System.currentTimeMillis()}.txt")
            
            // Convert to base64 for web viewer
            frame.rewind()
            val bytes = ByteArray(frame.remaining())
            frame.get(bytes)
            frame.rewind()
            
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            FileOutputStream(file).use { fos ->
                fos.write("Width: $lastFrameWidth\n".toByteArray())
                fos.write("Height: $lastFrameHeight\n".toByteArray())
                fos.write("Format: RGBA\n".toByteArray())
                fos.write("Base64:\n".toByteArray())
                fos.write(base64.toByteArray())
            }
            
            Toast.makeText(this, "Frame saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Frame saved to: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save frame", e)
            Toast.makeText(this, "Failed to save frame", Toast.LENGTH_SHORT).show()
        }
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
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        if (::cameraController.isInitialized && checkCameraPermission()) {
            cameraController.startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraController.isInitialized) {
            cameraController.release()
        }
    }
}
