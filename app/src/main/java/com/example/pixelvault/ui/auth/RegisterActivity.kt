package com.example.pixelvault.ui.auth // Corrected package

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pixelvault.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnSignUp.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString()
            val termsAccepted = binding.cbTerms.isChecked

            if (username.isNotEmpty() && email.isNotEmpty() && pass.isNotEmpty()) {
                if (!termsAccepted) {
                    Toast.makeText(this, "Please accept the terms and conditions", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Add any other specific validation for username, email, password if needed here
                // For example, password length
                if (pass.length < 6) { // Firebase default minimum password length
                    Toast.makeText(this, "Password should be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Optionally, update Firebase user profile with username
                        val user = auth.currentUser
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(username)
                            .build()
                        user?.updateProfile(profileUpdates)?.addOnCompleteListener { profileTask ->
                            if (profileTask.isSuccessful) {
                                Toast.makeText(this, "Registration successful! Profile updated.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Registration successful! Profile update failed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, task.exception?.message ?: "Registration failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish() // Optional: finish RegisterActivity when navigating to Login
        }
    }
}
