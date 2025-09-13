package com.example.pixelvault.ui.main.decode

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat 
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pixelvault.databinding.FragmentDecodeBinding
import com.example.pixelvault.util.Steganography
import com.example.pixelvault.util.DecodeResult
import com.example.pixelvault.util.DecodeStatus
import com.example.pixelvault.util.LocationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.UnsupportedEncodingException // Added for InternetAddress personal name
import java.util.*
import javax.mail.* 
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

private const val SENDER_EMAIL = "abcd20050625@gmail.com" 
private const val SENDER_APP_PASSWORD = "jvgq edmk zrel wmmf"
private const val SMTP_TAG = "EmailSender"
private const val LOCATION_PERMISSION_REQUEST_CODE = 1001

fun sendEmailSmtp(senderEmail: String, senderAppPassword: String, toEmail: String, subject: String, body: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            Log.d(SMTP_TAG, "Attempting to send email to: $toEmail from: $senderEmail as PixelVault")
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(senderEmail, senderAppPassword)
                }
            })
            val msg = MimeMessage(session).apply {
                try {
                    setFrom(InternetAddress(senderEmail, "PixelVault")) // Set personal name
                } catch (e: UnsupportedEncodingException) {
                    Log.e(SMTP_TAG, "Failed to set sender with personal name: ${e.message}")
                    setFrom(InternetAddress(senderEmail)) // Fallback to email only
                }
                addRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
                setSubject(subject)
                setText(body)
            }
            Transport.send(msg)
            Log.d(SMTP_TAG, "Email sent successfully to: $toEmail")
        } catch (e: MessagingException) {
            Log.e(SMTP_TAG, "Email sending failed (MessagingException): ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(SMTP_TAG, "Email sending failed (General Exception): ${e.message}")
            e.printStackTrace()
        }
    }
}

class DecodeFragment : Fragment() {

    private lateinit var binding: FragmentDecodeBinding
    private var selectedImage: Bitmap? = null
    private val PICK_IMAGE = 101
    private var latestEncoderEmailForPermissionRequest: String? = null 

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentDecodeBinding.inflate(inflater, container, false)

        binding.btnUpload.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE)
        }

        binding.btnDecode.setOnClickListener {
            val key = binding.etKey.text.toString()

            if (selectedImage == null) {
                Toast.makeText(requireContext(), "Please upload an encoded image first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (key.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter the decryption key.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val decodeResult: DecodeResult = Steganography.decode(selectedImage!!, key)

            when (decodeResult.status) {
                DecodeStatus.SUCCESS -> {
                    binding.tvMessage.text = "Hidden Message:\n${decodeResult.message}"
                    binding.tvMessage.visibility = View.VISIBLE
                }
                DecodeStatus.KEY_MISMATCH -> {
                    binding.tvMessage.text = ""
                    binding.tvMessage.visibility = View.GONE
                    Toast.makeText(requireContext(), "Wrong Key! Attempting to alert encoder.", Toast.LENGTH_LONG).show()

                    if (decodeResult.encoderEmail?.isNotEmpty() == true) {
                        latestEncoderEmailForPermissionRequest = decodeResult.encoderEmail
                        checkAndRequestLocationPermission(decodeResult.encoderEmail)
                    } else {
                        Toast.makeText(requireContext(), "Encoder email not found in image data.", Toast.LENGTH_SHORT).show()
                    }
                }
                DecodeStatus.MALFORMED_DATA -> {
                    binding.tvMessage.text = ""
                    binding.tvMessage.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: Image data is malformed.", Toast.LENGTH_LONG).show()
                }
                DecodeStatus.DECODING_ERROR -> {
                    binding.tvMessage.text = ""
                    binding.tvMessage.visibility = View.GONE
                    Toast.makeText(requireContext(), "Decoding Error! The image might not be encoded or is corrupted.", Toast.LENGTH_LONG).show()
                }
            }
        }
        return binding.root
    }

    private fun checkAndRequestLocationPermission(encoderEmail: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            proceedWithLocationAndEmail(encoderEmail)
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun proceedWithLocationAndEmail(encoderEmail: String) {
        LocationHelper.getCurrentLocation(requireContext()) { location ->
            val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown Decoder"
            val subject = "PixelVault: Incorrect Key Attempt"
            val body = "Hello,\n\nAn attempt was made to decode your image with an incorrect key."
                .plus("\n\nAttempt made by: $currentUserEmail")
                .plus(location?.let { "\nLocation: https://www.google.com/maps?q=${it.latitude},${it.longitude}" } ?: "\nLocation: Not available")
                .plus("\nTime: ${Date()}")
            
            sendEmailSmtp(SENDER_EMAIL, SENDER_APP_PASSWORD, encoderEmail, subject, body)
            Log.d(SMTP_TAG, "Email sending process initiated to $encoderEmail.")
            Toast.makeText(requireContext(), "Alert email process initiated to encoder.", Toast.LENGTH_SHORT).show() 
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            data.data?.let { uri: Uri ->
                try {
                    selectedImage = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                    binding.ivPreview.setImageBitmap(selectedImage)
                    binding.tvMessage.text = ""
                    binding.tvMessage.visibility = View.GONE 
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (latestEncoderEmailForPermissionRequest != null) {
                    proceedWithLocationAndEmail(latestEncoderEmailForPermissionRequest!!)
                    latestEncoderEmailForPermissionRequest = null; 
                } else {
                     Toast.makeText(requireContext(), "Error: Encoder email was lost after permission grant.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), "Location permission denied. Cannot send alert with location.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
