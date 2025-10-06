package com.flam.edgeviewer

import java.nio.ByteBuffer

/**
 * JNI Bridge for native OpenCV processing
 */
object NativeProcessor {
    
    init {
        System.loadLibrary("edgeviewer")
    }

    /**
     * Process mode constants
     */
    const val MODE_RAW = 0
    const val MODE_GRAYSCALE = 1
    const val MODE_CANNY = 2

    /**
     * Process camera frame using OpenCV
     * @param yuvData YUV_420_888 image data
     * @param width Frame width
     * @param height Frame height
     * @param mode Processing mode (RAW, GRAYSCALE, CANNY)
     * @return Processed RGBA data
     */
    external fun processFrame(
        yuvData: ByteBuffer,
        width: Int,
        height: Int,
        mode: Int
    ): ByteBuffer?

    /**
     * Convert YUV to RGBA
     */
    external fun yuv420ToRgba(
        yuvData: ByteBuffer,
        width: Int,
        height: Int
    ): ByteBuffer?

    /**
     * Test native library connection
     */
    external fun testConnection(): String
}
