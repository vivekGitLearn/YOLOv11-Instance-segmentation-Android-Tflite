package com.vivek.yolov11instancesegmentation

import android.content.Context
import okhttp3.*
import okio.ByteString
import java.io.File
import java.util.*

class FileWebSocketClient(private val context: Context) {

    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()

    fun connectAndSendFile(file: File) {
        val clientId = UUID.randomUUID().toString()
        val request = Request.Builder()
            .url("${Constants.WS_URL}/ws/$clientId")  // Replace with your server
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("✅ WebSocket Connected")
                sendFile(file)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("📩 Received: $text")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                println("📦 Binary message received: ${bytes.size} bytes")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("❌ Failure: ${t.message}")
            }
        })
    }

    private fun sendFile(file: File) {
        val fileBytes = file.readBytes()
        val byteString = ByteString.of(*fileBytes)
        webSocket.send(byteString)  // 👈 Sending binary file data
        println("📤 File sent (${file.name}, ${fileBytes.size} bytes)")
    }

    fun close() {
        webSocket.close(1000, "Closing connection")
    }
}
