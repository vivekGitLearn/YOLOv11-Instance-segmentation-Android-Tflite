package com.vivek.yolov11instancesegmentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vivek.yolov11instancesegmentation.databinding.ActivityLoginAndSignUpBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class LoginAndSignUp : AppCompatActivity() {

    private lateinit var binding: ActivityLoginAndSignUpBinding
    private var isLoginMode = true
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginAndSignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        updateUI()

        binding.switchModeText.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUI()
        }

        binding.authButton.setOnClickListener {
            val username = binding.usernameEditText.text.toString()
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if ((isLoginMode && (email.isEmpty() || password.isEmpty())) ||
                (!isLoginMode && (username.isEmpty() || email.isEmpty() || password.isEmpty()))
            ) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val deviceUUID = getOrCreateClientId(this)

            val jsonBody = JSONObject().apply {
                if (isLoginMode) {
                    put("email", email)
                    put("password", password)

                    val deviceJson = JSONObject().apply {
                        put("device_name", "Android Device")
                        put("device_type", "Android")
                        put("device_uuid", deviceUUID)
                        put("status",1)
                    }
                    put("device", deviceJson)
                } else {
                    put("username", username)
                    put("email", email)
                    put("password", password)
                }
            }

            val url = if (isLoginMode)
                "${Constants.BASE_URL}/users/login"
            else
                "${Constants.BASE_URL}/users/signup"

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
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
                                // Save login state and deviceUUID
                                val sharedPref = getSharedPreferences("MyPrefs", MODE_PRIVATE)
                                with(sharedPref.edit()) {
                                    putBoolean("isLoggedIn", true)
                                    putString("deviceUUID", deviceUUID)
                                    putString("email", email)
                                    apply()
                                }

                                Toast.makeText(this@LoginAndSignUp, "Login successful!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@LoginAndSignUp, MainActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this@LoginAndSignUp, "Signup successful! You can now log in.", Toast.LENGTH_SHORT).show()
                                isLoginMode = true
                                updateUI()
                            }
                        }
                        else {
                            val errorMsg = response.body?.string() ?: "Unknown error"
                            Toast.makeText(this@LoginAndSignUp, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                            Log.d("login_signup", "onResponse: $errorMsg")
                        }
                    }
                }
            })
        }
    }

    private fun updateUI() {
        if (isLoginMode) {
            binding.authButton.text = "Login"
            binding.switchModeText.text = "Don't have an account? Sign Up"
            binding.usernameEditText.visibility = View.GONE
        } else {
            binding.authButton.text = "Sign Up"
            binding.switchModeText.text = "Already have an account? Login"
            binding.usernameEditText.visibility = View.VISIBLE
        }
    }
    fun getOrCreateClientId(context: Context): String {
        val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var clientId = sharedPref.getString("client_id", null)

        if (clientId == null) {
            clientId = UUID.randomUUID().toString() // Generate unique ID
            sharedPref.edit().putString("client_id", clientId).apply()
        }

        return clientId
    }
}
