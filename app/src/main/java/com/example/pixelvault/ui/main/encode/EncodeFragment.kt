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
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EncodeFragment : Fragment() {

    private var _binding: FragmentEncodeBinding? = null
    private val binding get() = _binding!!

    private var selectedImage: Bitmap? = null
    private var encodedImageFile: File? = null // For storing the path to the encoded image for sharing
    private val PICK_IMAGE = 100
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEncodeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showInputForm() // Set initial UI state

        binding.btnBackToDashboard.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.cvUploadArea.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE)
        }

        binding.btnEncodeImage.setOnClickListener {
            val messageString = binding.etSecretMessage.text.toString()
            val key = binding.etPassword.text.toString()
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
            if (encoderEmail.contains(":") || key.contains(":")) {
                Toast.makeText(requireContext(), "Email or Key cannot contain the character ':'", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val encodedBitmap = Steganography.encode(selectedImage!!, encoderEmail, key, messageString)
            
            if (encodedBitmap != null) {
                try {
                    // Save bitmap to a temporary file for sharing and preview
                    val tempFile = File(requireContext().cacheDir, "encoded_image.png")
                    FileOutputStream(tempFile).use { out ->
                        encodedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    encodedImageFile = tempFile // Store file for share button
                    showSuccessScreen(encodedBitmap, encodedImageFile!!)
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.e("EncodeFragment", "Error saving temp encoded image: ${e.message}")
                    Toast.makeText(requireContext(), "Error processing encoded image for display.", Toast.LENGTH_LONG).show()
                    // Still show success but maybe without preview or with share disabled if file ops failed critically
                    // For now, if temp file save fails, share might not work.
                    showSuccessScreen(encodedBitmap, File("")) // Pass a dummy file if save failed before assignment

                }
            } else {
                Toast.makeText(requireContext(), "Encoding failed. Message too large, or email/key invalid.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showInputForm() {
        binding.groupEncodeInputForm.visibility = View.VISIBLE
        binding.layoutEncodeSuccess.visibility = View.GONE
        // Reset selected image if needed, or clear preview from success screen
        // binding.ivUploadIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_menu_upload)) // Or your default upload icon
        // binding.tvUploadHint.setText(R.string.click_to_upload) 
    }

    private fun showSuccessScreen(bitmap: Bitmap, imageFile: File) {
        binding.groupEncodeInputForm.visibility = View.GONE
        binding.layoutEncodeSuccess.visibility = View.VISIBLE
        binding.ivEncodedImagePreview.setImageBitmap(bitmap)

        binding.btnDownloadEncoded.setOnClickListener {
            if (savePngToGallery(requireContext(), bitmap, "PixelVault_Encoded_${System.currentTimeMillis()}.png")) {
                Toast.makeText(requireContext(), "Encoded image saved to Gallery", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to save image to Gallery", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnShareEncoded.setOnClickListener {
            if (imageFile.exists()) {
                try {
                    val context = requireContext()
                    val authority = "${context.packageName}.fileprovider"
                    val uri: Uri = FileProvider.getUriForFile(context, authority, imageFile)

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Encoded Image"))
                } catch (e: Exception) {
                    Log.e("EncodeFragment", "Error sharing image: ${e.message}", e)
                    Toast.makeText(requireContext(), "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "Encoded image file not found. Cannot share.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun savePngToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "PixelVault")
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
            Log.e("EncodeFragment", "savePngToGallery failed: ${e.message}", e)
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
                    showInputForm() // Reset to input form when a new image is selected
                    // Update the upload area to show the selected image or a thumbnail
                    // For example, you could change the icon in cvUploadArea or show a small thumbnail.
                    // binding.ivUploadIcon.setImageBitmap(selectedImage) // Example if you have such an ID in cvUploadArea
                    Toast.makeText(requireContext(), "Image selected. Ready to encode.", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Clean up the temporary encoded image file if it exists
        encodedImageFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        encodedImageFile = null
    }
}
