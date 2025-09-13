package com.example.pixelvault.ui.main.encode

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.pixelvault.databinding.FragmentEncodeBinding
import com.example.pixelvault.util.Steganography
import com.google.firebase.auth.FirebaseAuth // Added Firebase Auth import
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EncodeFragment : Fragment() {

    private lateinit var binding: FragmentEncodeBinding
    private var selectedImage: Bitmap? = null
    private val PICK_IMAGE = 100
    private lateinit var auth: FirebaseAuth // Added Firebase Auth instance

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentEncodeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance() // Initialize Firebase Auth

        binding.btnDownload.visibility = View.GONE
        binding.btnShare.visibility = View.GONE

        // Upload image
        binding.btnUpload.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE)
        }

        // Encode message
        binding.btnEncode.setOnClickListener {
            val messageString = binding.etMessage.text.toString() // Renamed to avoid conflict with parameter name
            val key = binding.etKey.text.toString()
            val currentUser = auth.currentUser

            if (currentUser == null) {
                Toast.makeText(requireContext(), "User not logged in. Cannot encode.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val encoderEmail = currentUser.email
            if (encoderEmail.isNullOrEmpty()){
                Toast.makeText(requireContext(), "User email not found. Cannot encode.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedImage == null) {
                Toast.makeText(requireContext(), "Please upload an image first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (messageString.isEmpty() || key.isEmpty()) {
                Toast.makeText(requireContext(), "Enter both message and key.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
             // Check for delimiter in email or key as per Steganography.kt logic
            if (encoderEmail.contains(":") || key.contains(":")) {
                Toast.makeText(requireContext(), "Email or Key cannot contain the character ':'", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Corrected call to Steganography.encode with encoderEmail
            val encodedBitmap = Steganography.encode(selectedImage!!, encoderEmail, key, messageString)
            
            if (encodedBitmap != null) {
                val file = File(requireContext().cacheDir, "encoded.png")
                try {
                    FileOutputStream(file).use { out ->
                        encodedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    // Changed Toast message to reflect non-AES for this path
                    Toast.makeText(requireContext(), "Image Encoded Successfully!", Toast.LENGTH_SHORT).show()
                    binding.btnDownload.visibility = View.VISIBLE
                    binding.btnShare.visibility = View.VISIBLE

                    binding.btnDownload.setOnClickListener {
                        if (savePngToGallery(requireContext(), encodedBitmap, "PixelVault_${System.currentTimeMillis()}.png")) {
                            Toast.makeText(requireContext(), "Saved to Gallery", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Save failed", Toast.LENGTH_SHORT).show()
                        }
                    }

                    binding.btnShare.setOnClickListener {
                        try {
                            val context = requireContext()
                            val authority = "${context.packageName}.fileprovider"
                            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share Encoded Image"))
                        } catch (e: Exception) {
                            val errorMessage = e.message ?: "Unknown error"
                            Toast.makeText(requireContext(), "Share failed: $errorMessage", Toast.LENGTH_LONG).show()
                            e.printStackTrace()
                        }
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Error saving encoded image.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Encoding failed. Message too large, or email/key invalid.", Toast.LENGTH_LONG).show()
            }
        }
        return binding.root
    }

    private fun savePngToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelVault")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        var imageUri: Uri? = null
        return try {
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri == null) return false
            resolver.openOutputStream(imageUri).use { out ->
                if (out == null) return false
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            imageUri?.let { resolver.delete(it, null, null) }
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            data.data?.let { uri ->
                try {
                    selectedImage = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                    binding.ivPreview.setImageBitmap(selectedImage)
                    binding.btnDownload.visibility = View.GONE
                    binding.btnShare.visibility = View.GONE
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
