package com.example.pixelvault.ui.auth // Corrected case

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// Corrected case for databinding import
import com.example.pixelvault.databinding.ActivityLoginBinding
// Added import for DashboardActivity
import com.example.pixelvault.ui.main.DashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.pixelvault.ui.auth.RegisterActivity // Added explicit import

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnSignIn.setOnClickListener { // Changed from btnLogin
            // The layout uses etUsername, but Firebase auth needs email.
            // Assuming user enters email in the etUsername field.
            val email = binding.etUsername.text.toString().trim() // Changed from etEmail
            val pass = binding.etPassword.text.toString()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener {
                    if (it.isSuccessful) {
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, it.exception?.message ?: "Login failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Please enter username/email and password", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvRegisterLink.setOnClickListener { // Changed from tvRegister
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Listener for the new Forgot Password text view
        binding.tvForgotPassword.setOnClickListener { // Changed from btnForgotPassword
            // The layout uses etUsername, but Firebase auth needs email.
            // Assuming user enters email in the etUsername field for password reset.
            val email = binding.etUsername.text.toString().trim() // Changed from etEmail
            if (email.isNotEmpty()) {
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Failed to send reset email: ${task.exception?.message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter your email address to reset password.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
