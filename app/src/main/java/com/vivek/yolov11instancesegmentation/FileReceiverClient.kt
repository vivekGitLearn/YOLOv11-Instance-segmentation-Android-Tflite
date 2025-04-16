package com.vivek.yolov11instancesegmentation
import android.content.Context
import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.io.File
import java.util.*
class FileReceiverClient(private val context: Context) {

    fun connectAndReceiveFile(clientId: String) {
        val request = Request.Builder()
            .url("ws://192.168.1.5:8000/ws_send_file/$clientId") // Use your server IP
            .build()

        val client = OkHttpClient()
        var filename: String = "received_file.bin" // default fallback

        client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected to server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("filename")) {
                        filename = json.getString("filename")
                        Log.d("WebSocket", "Receiving file: $filename")
                    }
                } catch (e: Exception) {
                    Log.d("WebSocket", "Text: $text")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val file = saveFileToInternalStorage(filename, bytes.toByteArray())
                Log.d("WebSocket", "File saved at: ${file.absolutePath}")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error: ${t.message}")
            }
        })
    }

    private fun saveFileToInternalStorage(filename: String, data: ByteArray): File {
        val file = File(context.filesDir, filename)
        file.writeBytes(data)
        return file
    }
}
