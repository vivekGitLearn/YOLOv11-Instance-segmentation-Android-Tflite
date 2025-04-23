package com.vivek.yolov11instancesegmentation

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@SuppressLint("CustomSplashScreen")
class SplashScreen : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Handler to delay the screen for 3 seconds
        Handler(Looper.getMainLooper()).postDelayed(
            {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }, 5000)
        // 3000 is the delayed time in milliseconds.
    }
}