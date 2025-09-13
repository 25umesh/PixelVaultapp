package com.example.pixelvault.ui.main // Corrected

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pixelvault.databinding.ActivityDashboardBinding // Corrected
// EncodeFragment and DecodeFragment are no longer directly launched from here
// but ChooserFragment will launch them.
import com.example.pixelvault.ui.main.ChooserFragment // Added import for ChooserFragment

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load ChooserFragment by default if the activity is newly created
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.container.id, ChooserFragment()) // Use binding.container.id as per existing code
                .commit()
        }

        // Removed the old btnEncode and btnDecode listeners
        // Navigation is now handled by ChooserFragment
    }
}
