package com.vivek.yolov11instancesegmentation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

// ✅ Define a callback interface
interface WebSocketMessageListener {
    fun onTextMessage(message: String)
}

class CustomWebSocketClient(
    private val listener: WebSocketMessageListener,
    private val clientId: String
) {

    private lateinit var webSocketClient: WebSocketClient
    private val serverUri = URI("ws://192.168.1.5:8000/ws/$clientId") // Replace with your server IP

    fun connect() {
        webSocketClient = object : WebSocketClient(serverUri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("WebSocket", "✅ Connected as $clientId")
            }

            override fun onMessage(message: String?) {
                Log.d("WebSocket", "📩 Message received: $message")
                val json = JSONObject(message ?: "{}")
                val msg = json.optString("message", "No message")

                // 🔁 Post back to UI using callback
                Handler(Looper.getMainLooper()).post {
                    listener?.onTextMessage(msg)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d("WebSocket", "🔌 Disconnected: $reason")
            }

            override fun onError(ex: Exception?) {
                Log.e("WebSocket", "❌ Error: ${ex?.message}")
            }
        }
        webSocketClient.connect()
    }

    fun disconnect() {
        if (::webSocketClient.isInitialized) {
            webSocketClient.close()
        }
    }

    fun sendMessage(msg: String) {
        if (::webSocketClient.isInitialized && webSocketClient.isOpen) {
            webSocketClient.send(msg)
        }
    }
}
