package com.vivek.yolov11instancesegmentation

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONException
import org.json.JSONObject
import java.net.URI

// ✅ Define a callback interface
interface WebSocketMessageListener {
    fun onTextMessage(message: String)
    fun onAssignJob(
        jobId: String,
        modelName: String,
        modelUrl: String,
        modelHash: String,
        imageUrl: String,
        returnType: String,
        returnUrl: String
    )
}

class CustomWebSocketClient(
    private val listener: WebSocketMessageListener,
    private val clientId: String
) {

    private lateinit var webSocketClient: WebSocketClient
    private val serverUri = URI("ws://192.168.1.3:8000/ws/$clientId") // Replace with your server IP
    private val reconnectDelayMillis: Long = 5000
    private var shouldReconnect = true // 🔁 Flag to control reconnection
    private val handler = Handler(Looper.getMainLooper())


    fun connect() {
        shouldReconnect = true // ✅ Mark for auto-reconnect
        webSocketClient = object : WebSocketClient(serverUri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("WebSocket", "✅ Connected as $clientId")
            }

            override fun onMessage(message: String?) {
                Log.d("WebSocket", "📩 Message received: $message")

                // Guard: Check if it's a JSON string
                if (message != null && message.trim().startsWith("{")) {
                    try {
                        val json = JSONObject(message)
                        val type = json.optString("type", "message")

                        when (type) {
                            "instruction" -> {
                                val payload = json.optJSONObject("payload")
                                if (payload != null) {
                                    handleAssignJob(payload)
                                }
                            }
                            "message" -> {
                                val msg = json.optString("message", "No message")
                                handler.post {
                                    listener.onTextMessage(msg)
                                }
                            }
                            else -> {
                                Log.w("WebSocket", "⚠️ Unknown type: $type")
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e("WebSocket", "❌ JSON parse error: ${e.message}")
                    }
                } else {
                    Log.d("WebSocket", "📡 Non-JSON message: $message")
                    // Handle ping, pong or other signals here if needed
                }
            }


            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d("WebSocket", "🔌 Disconnected: $reason")
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }

            override fun onError(ex: Exception?) {
                Log.e("WebSocket", "❌ Error: ${ex?.message}")
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
        }

        webSocketClient.connect()
    }

    fun disconnect() {
        shouldReconnect = false // ⛔ Stop auto-reconnect
        if (::webSocketClient.isInitialized) {
            webSocketClient.close()
        }
    }

    fun sendMessage(msg: String) {
        if (::webSocketClient.isInitialized && webSocketClient.isOpen) {
            webSocketClient.send(msg)
        } else {
            Log.w("WebSocket", "❗ Tried sending message while socket not open")
        }
    }

    private fun scheduleReconnect() {
        Log.d("WebSocket", "🔁 Attempting to reconnect in ${reconnectDelayMillis / 1000} seconds...")
        handler.postDelayed({
            if (shouldReconnect) {
                Log.d("WebSocket", "🔄 Reconnecting...")
                connect()
            }
        }, reconnectDelayMillis)
    }

    private fun handleAssignJob(payload: JSONObject) {
        val jobId = payload.optString("job_id")
        val modelName = payload.optString("model_name")
        val modelUrl = payload.optString("model_url")
        val modelHash = payload.optString("model_hash")
        val imageUrl = payload.optString("image_url")
        val returnType = payload.optString("return_type")
        val returnUrl = payload.optString("return_url")

        Log.d("WebSocket", "🎯 Assigning job: $jobId with model $modelName")

        handler.post {
            listener.onAssignJob(
                jobId, modelName, modelUrl, modelHash, imageUrl, returnType, returnUrl
            )
        }
    }
}