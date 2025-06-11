package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)  // Ensure this layout exists

        supportActionBar?.title = null
        supportActionBar?.hide()

        val loginButton = findViewById<Button>(R.id.loginButton)
        val signupLink = findViewById<TextView>(R.id.signupLink)

        // Check for null references to avoid app crash
        loginButton?.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        signupLink?.setOnClickListener {
            startActivity(Intent(this, CreateAccountActivity::class.java))
        }
    }
}
