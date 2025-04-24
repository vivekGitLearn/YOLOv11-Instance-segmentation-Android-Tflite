package com.vivek.yolov11instancesegmentation

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vivek.yolov11instancesegmentation.databinding.ActivityLoginAndSignUpBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginAndSignUp : AppCompatActivity() {

    private lateinit var binding: ActivityLoginAndSignUpBinding
    private var isLoginMode = true
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginAndSignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        binding.switchModeText.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUI()
        }

        binding.authButton.setOnClickListener {
            val username = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val deviceUUID = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            val deviceJson = JSONObject().apply {
                put("device_name", "Android Device")
                put("device_type", "Android")
                put("mac_address", deviceUUID)
            }

            val jsonBody = JSONObject().apply {
                put("username", username)
                put("password", password)
                put("device", deviceJson)
            }

            val url = if (isLoginMode) "http://192.168.1.3:8000/login" else "http://192.168.1.3:8000/signup"

            val request = Request.Builder()
                .url(url)
                .post(
                    jsonBody.toString().toRequestBody("application/json".toMediaType())
                )
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@LoginAndSignUp, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.d("login_signup", "onFailure: ${e.message}")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            if (isLoginMode) {
                                Toast.makeText(this@LoginAndSignUp, "Login successful!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@LoginAndSignUp, MainActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this@LoginAndSignUp, "Signup successful! You can now log in.", Toast.LENGTH_SHORT).show()
                                isLoginMode = true
                                updateUI()
                            }
                        } else {
                            val errorMsg = response.body?.string() ?: "Unknown error"
                            Toast.makeText(this@LoginAndSignUp, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                            Log.d("login_signup", "onResponse: $errorMsg")
                        }
                    }
                }
            })
        }

        updateUI()
    }

    private fun updateUI() {
        if (isLoginMode) {
            binding.authButton.text = "Login"
            binding.switchModeText.text = "Don't have an account? Sign Up"
        } else {
            binding.authButton.text = "Sign Up"
            binding.switchModeText.text = "Already have an account? Login"
        }
    }
}
