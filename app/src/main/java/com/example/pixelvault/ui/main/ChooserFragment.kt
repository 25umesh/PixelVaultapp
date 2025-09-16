package com.example.pixelvault.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.pixelvault.R
import com.example.pixelvault.databinding.FragmentChooserBinding
import com.example.pixelvault.ui.auth.LoginActivity
import com.example.pixelvault.ui.main.decode.DecodeFragment
import com.example.pixelvault.ui.main.encode.EncodeFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class ChooserFragment : Fragment() {

    private var _binding: FragmentChooserBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChooserBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Display User Name
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val displayName = currentUser.displayName
            val email = currentUser.email
            if (!displayName.isNullOrEmpty()) {
                binding.tvUserName.text = displayName
            } else if (!email.isNullOrEmpty()) {
                binding.tvUserName.text = email.split("@")[0] // Show part before @ as a simple name
            } else {
                binding.tvUserName.text = "PixelVault User"
            }
        } else {
            binding.tvUserName.text = "Welcome"
            binding.tvWelcomeMessage.text = "Please sign in"
        }

        binding.cardEncodeImage.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, EncodeFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.cardDecodeImage.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, DecodeFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.ivNotificationIcon.setOnClickListener {
            Toast.makeText(requireContext(), "Notifications clicked", Toast.LENGTH_SHORT).show()
        }

        binding.ivSettingsIcon.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Logout") { dialog, _ ->
                auth.signOut()
                val intent = Intent(requireActivity(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Hide the activity's toolbar if this fragment is visible
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
    }

    override fun onPause() {
        super.onPause()
        // Optionally, show the activity's toolbar again when this fragment is paused
        // (activity as? AppCompatActivity)?.supportActionBar?.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Make sure to show toolbar again if it was hidden and fragment is destroyed
        (activity as? AppCompatActivity)?.supportActionBar?.show()
    }
}
