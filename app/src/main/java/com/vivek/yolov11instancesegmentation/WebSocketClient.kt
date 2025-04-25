package com.vivek.yolov11instancesegmentation

import okhttp3.*
import okio.ByteString
import java.util.UUID

class WebSocketClient(private val listener: WebSocketListenerCallback) {

    private lateinit var webSocket: WebSocket

    fun startWebSocket() {
        val clientId = UUID.randomUUID().toString()  // 👈 Or store in SharedPreferences
        val request = Request.Builder()
            .url("$(Constants.WS_URL)/ws/$clientId") // Replace with your IP
            .build()

        val client = OkHttpClient()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {

                println("✅ Connected to WebSocket")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onTextMessage(text)
                println("📩 Received message_: $text")
                // Example: parse JSON and show notification or update UI
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                println("📦 Received binary message: ${bytes.hex()}")
                listener.onBinaryMessage(bytes)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                println("🚪 Closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("❌ WebSocket failure: ${t.message}")
            }
        })
    }

    fun sendMessage(message: String) {
        webSocket.send(message)
    }

    fun closeWebSocket() {
        webSocket.close(1000, "App closed")
    }
}
interface WebSocketListenerCallback {
    fun onTextMessage(message: String)
    fun onBinaryMessage(bytes: ByteString)
}