package com.example.pixelvault.ui.main.decode

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pixelvault.databinding.FragmentDecodeBinding
import com.example.pixelvault.util.* // Imports DecodeResult, DecodeStatus, LocationHelper, PhotoCaptureHelper, Steganography
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.*
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

private const val SENDER_EMAIL = "abcd20050625@gmail.com"
private const val SENDER_APP_PASSWORD = "jvgq edmk zrel wmmf"
private const val SMTP_TAG = "EmailSender"

private const val PICK_IMAGE_REQUEST = 101
private const val ALL_PERMISSIONS_REQUEST_CODE = 1003

// Top-level function for sending email
fun sendEmailSmtp(
    senderEmail: String,
    senderAppPassword: String,
    toEmail: String,
    subject: String,
    body: String,
    photoPath: String?
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            Log.d(SMTP_TAG, "Attempting to send email to: $toEmail from: $senderEmail as PixelVault. Photo path: $photoPath")
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
                    setFrom(InternetAddress(senderEmail, "PixelVault"))
                } catch (e: UnsupportedEncodingException) {
                    Log.e(SMTP_TAG, "Failed to set sender with personal name: ${e.message}")
                    setFrom(InternetAddress(senderEmail))
                }
                addRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
                setSubject(subject)

                val multipart = MimeMultipart("mixed")
                val textBodyPart = MimeBodyPart()
                textBodyPart.setText(body, "utf-8")
                multipart.addBodyPart(textBodyPart)

                if (photoPath != null) {
                    try {
                        val photoFileForAttachment = File(photoPath)
                        if (photoFileForAttachment.exists()) {
                            val imageBodyPart = MimeBodyPart()
                            val source = FileDataSource(photoFileForAttachment)
                            imageBodyPart.dataHandler = DataHandler(source)
                            imageBodyPart.fileName = "failed_attempt_capture.jpg"
                            imageBodyPart.setHeader("Content-ID", "<attempt_image>")
                            imageBodyPart.disposition = MimeBodyPart.ATTACHMENT
                            multipart.addBodyPart(imageBodyPart)
                            Log.d(SMTP_TAG, "Photo attachment prepared: ${photoFileForAttachment.absolutePath}")
                        } else {
                            Log.e(SMTP_TAG, "Photo file not found for attachment: $photoPath")
                        }
                    } catch (e: Exception) {
                        Log.e(SMTP_TAG, "Error preparing photo attachment: ${e.message}", e)
                    }
                }
                this.setContent(multipart)
            }
            Transport.send(msg)
            Log.d(SMTP_TAG, "Email sent successfully to: $toEmail")
        } catch (e: MessagingException) {
            Log.e(SMTP_TAG, "Email sending failed (MessagingException): ${e.message}", e)
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(SMTP_TAG, "Email sending failed (General Exception): ${e.message}", e)
            e.printStackTrace()
        } finally {
            // Clean up the temporary photo file after attempting to send the email
            if (photoPath != null) {
                try {
                    val fileToDelete = File(photoPath)
                    if (fileToDelete.exists()) {
                        if (fileToDelete.delete()) {
                            Log.d(SMTP_TAG, "Temporary photo file deleted: $photoPath")
                        } else {
                            Log.w(SMTP_TAG, "Failed to delete temporary photo file: $photoPath")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(SMTP_TAG, "Error deleting temporary photo file: $photoPath", e)
                }
            }
        }
    }
}

class DecodeFragment : Fragment() {

    private var _binding: FragmentDecodeBinding? = null
    private val binding get() = _binding!!
    private var selectedImage: Bitmap? = null
    private var latestEncoderEmailForAlert: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDecodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnUpload.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
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
            handleDecodeResult(decodeResult)
        }
    }

    private fun handleDecodeResult(result: DecodeResult) {
        when (result.status) {
            DecodeStatus.SUCCESS -> {
                binding.tvMessage.text = "Hidden Message:\n${result.message}"
                binding.tvMessage.visibility = View.VISIBLE
            }
            DecodeStatus.KEY_MISMATCH -> {
                binding.tvMessage.text = ""
                binding.tvMessage.visibility = View.GONE
                Toast.makeText(requireContext(), "Wrong Key! Attempting to alert encoder.", Toast.LENGTH_LONG).show()

                if (!result.encoderEmail.isNullOrEmpty()) {
                    latestEncoderEmailForAlert = result.encoderEmail
                    checkAndRequestPermissions()
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
                Toast.makeText(requireContext(), "Decoding Error! Image might not be encoded or is corrupted.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA)
        }

        if (requiredPermissions.isNotEmpty()) {
            requestPermissions(requiredPermissions.toTypedArray(), ALL_PERMISSIONS_REQUEST_CODE)
        } else {
            latestEncoderEmailForAlert?.let { proceedWithAlert(it) }
        }
    }

    private fun proceedWithAlert(encoderEmail: String) {
        val cameraPermissionGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationPermissionGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (cameraPermissionGranted) {
            Toast.makeText(requireContext(), "Attempting to capture photo...", Toast.LENGTH_SHORT).show()
            PhotoCaptureHelper.capturePhoto(requireContext()) { photoFileCaptured -> 
                getLocationAndSendEmail(encoderEmail, photoFileCaptured, locationPermissionGranted)
            }
        } else {
            Log.d(TAG, "Camera permission not granted. Proceeding without photo.")
            getLocationAndSendEmail(encoderEmail, null, locationPermissionGranted)
        }
    }

    private fun getLocationAndSendEmail(encoderEmail: String, photoFile: File?, locationPermissionGranted: Boolean) {
        if (locationPermissionGranted) {
            LocationHelper.getCurrentLocation(requireContext()) { location ->
                sendAlertData(encoderEmail, photoFile, location) 
            }
        } else {
            Log.d(TAG, "Location permission not granted. Proceeding without location.")
            sendAlertData(encoderEmail, photoFile, null) 
        }
    }

    private fun sendAlertData(encoderEmail: String, photoFile: File?, location: Location?) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown Decoder"
        val subject = "PixelVault: Incorrect Key Attempt"
        var body = "Hello,\n\nAn attempt was made to decode your image with an incorrect key."
            .plus("\n\nAttempt made by: $currentUserEmail")
            .plus(location?.let { "\nLocation: https://www.google.com/maps?q=${it.latitude},${it.longitude}" } ?: "\nLocation: Not available")
            .plus("\nTime: ${Date()}")

        val photoPathForEmail = photoFile?.absolutePath

        if (photoPathForEmail != null) {
            body += "\n\nAn image of the user attempting to decode may have been captured (see attachment)."
        } else {
            body += "\n\nCould not capture an image of the user attempting to decode (camera permission might be denied or an error occurred)."
        }

        sendEmailSmtp(SENDER_EMAIL, SENDER_APP_PASSWORD, encoderEmail, subject, body, photoPathForEmail)
        Log.d(SMTP_TAG, "Alert email process initiated to $encoderEmail. Photo path: $photoPathForEmail, Location: $location")
        Toast.makeText(requireContext(), "Alert email process initiated to encoder.", Toast.LENGTH_LONG).show()
        // photoFile?.delete() // Removed from here
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            data.data?.let { uri: Uri ->
                try {
                    selectedImage = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                    binding.ivPreview.setImageBitmap(selectedImage)
                    binding.tvMessage.text = ""
                    binding.tvMessage.visibility = View.GONE
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to load image", e)
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ALL_PERMISSIONS_REQUEST_CODE) {
            val permissionsMap = permissions.zip(grantResults.toTypedArray()).toMap()
            val locationGranted = permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED
            val cameraGranted = permissionsMap[Manifest.permission.CAMERA] == PackageManager.PERMISSION_GRANTED

            if (latestEncoderEmailForAlert != null) {
                 if (locationGranted || cameraGranted) { 
                    proceedWithAlert(latestEncoderEmailForAlert!!)
                 } else {
                    Toast.makeText(requireContext(), "Permissions denied. Cannot send full alert.", Toast.LENGTH_LONG).show()
                    sendAlertData(latestEncoderEmailForAlert!!, null, null) 
                 }
            } else {
                Log.e(TAG, "Encoder email was null after permission result.")
                Toast.makeText(requireContext(), "Error: Encoder email not found after permission result.", Toast.LENGTH_SHORT).show()
            }
            latestEncoderEmailForAlert = null 
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    companion object {
        private const val TAG = "DecodeFragment"
    }
}
