package com.flam.edgeviewer

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.util.Base64
import android.util.Log
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class WebSocketStreamer(
    private val tag: String = "WebSocketStreamer"
) {
    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var lastSentAt = 0L
    private val minSendIntervalMs = 200L // ~5 FPS

    // Replace with your PC IP on the same Wi‑Fi/LAN
    // Example: ws://192.168.1.10:8080/frames
    var serverUrl: String = "ws://<PC_IP>:8080/frames"

    fun connect(url: String? = null) {
        val finalUrl = url ?: serverUrl
        client = OkHttpClient()
        val request = Request.Builder().url(finalUrl).build()
        webSocket = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(tag, "WebSocket connected: $finalUrl")
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure", t)
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed: $code $reason")
            }
        })
    }

    fun disconnect() {
        try { webSocket?.close(1000, "app pause") } catch (_: Throwable) {}
        webSocket = null
        try { client?.dispatcher?.executorService?.shutdown() } catch (_: Throwable) {}
        client = null
    }

    fun sendRgbaFrameIfDue(rgba: ByteBuffer, width: Int, height: Int, formatLabel: String = "RGBA") {
        val now = System.currentTimeMillis()
        if (now - lastSentAt < minSendIntervalMs) return
        lastSentAt = now

        try {
            // Copy RGBA bytes
            rgba.rewind()
            val bytes = ByteArray(rgba.remaining())
            rgba.get(bytes)
            rgba.rewind()

            // Convert RGBA -> Bitmap -> PNG bytes
            val bmp = rgbaToBitmap(bytes, width, height)
            val png = bitmapToPng(bmp)
            val b64 = Base64.encodeToString(png, Base64.NO_WRAP)

            val payload = """
                {"width":$width,"height":$height,"format":"$formatLabel","base64":"$b64","timestamp":$now}
            """.trimIndent()

            webSocket?.send(payload)
        } catch (t: Throwable) {
            Log.e(tag, "Failed to send frame", t)
        }
    }

    // Send a bitmap immediately (no throttle), encoded as PNG base64 JSON
    fun sendBitmap(bmp: Bitmap, width: Int, height: Int, formatLabel: String = "PNG") {
        try {
            val now = System.currentTimeMillis()
            val png = bitmapToPng(bmp)
            val b64 = Base64.encodeToString(png, Base64.NO_WRAP)
            val payload = """
                {"width":$width,"height":$height,"format":"$formatLabel","base64":"$b64","timestamp":$now}
            """.trimIndent()
            webSocket?.send(payload)
        } catch (t: Throwable) {
            Log.e(tag, "Failed to send bitmap", t)
        }
    }

    private fun rgbaToBitmap(bytes: ByteArray, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888)
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

    private fun bitmapToPng(bmp: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }
}
