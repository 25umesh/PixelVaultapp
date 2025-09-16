package com.example.pixelvault.ui.main.decode

import android.Manifest
import android.content.Context // Added for ContentResolver
import android.content.Intent // Keep for sharing/other intents if any, not for image picking directly
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder // Added
import android.location.Location
import android.net.Uri
import android.os.Build // Added
import android.os.Bundle
import android.provider.MediaStore // Keep for MediaStore constants if used elsewhere, but not getBitmap
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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

// Top-level function for sending email (remains unchanged)
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

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    selectedImage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(requireContext().contentResolver, it)
                        ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true)
                    } else {
                        @Suppress("DEPRECATION") // Suppress for older SDKs if minSdk < 28
                        MediaStore.Images.Media.getBitmap(requireContext().contentResolver, it).copy(Bitmap.Config.ARGB_8888, true)
                    }
                    Toast.makeText(requireContext(), "Image selected. Ready to decode.", Toast.LENGTH_SHORT).show()
                    binding.cardDecodedMessageDisplay.visibility = View.GONE // Hide on new image selection
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to load image: ${e.message}", e)
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                     Log.e(TAG, "Security exception loading image: ${e.message}", e)
                    Toast.makeText(requireContext(), "Failed to load image due to security restrictions.", Toast.LENGTH_LONG).show()
                }
            }
        }

        requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

            if (latestEncoderEmailForAlert != null) {
                if (fineLocationGranted || cameraGranted) { // Proceed if at least one critical permission is granted
                    proceedWithAlert(latestEncoderEmailForAlert!!)
                } else {
                    Toast.makeText(requireContext(), "Permissions denied. Cannot send full alert.", Toast.LENGTH_LONG).show()
                    sendAlertData(latestEncoderEmailForAlert!!, null, null)
                }
            } else {
                Log.e(TAG, "Encoder email was null after permission result.")
                Toast.makeText(requireContext(), "Error: Encoder email not found after permission result.", Toast.LENGTH_SHORT).show()
            }
            latestEncoderEmailForAlert = null // Reset
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDecodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardDecodedMessageDisplay.visibility = View.GONE // Ensure it's hidden initially

        binding.imageUploadArea.setOnClickListener {
            pickImageLauncher.launch("image/png") // Specific for PNGs, good with current steganography
        }

        binding.btnDecodeImage.setOnClickListener { 
            val key = binding.etPassword.text.toString()
            if (selectedImage == null) {
                Toast.makeText(requireContext(), "Please upload an encoded image first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (key.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter the decryption key.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Hide previous result before attempting new decode
            binding.cardDecodedMessageDisplay.visibility = View.GONE 
            val decodeResult: DecodeResult = Steganography.decode(selectedImage!!, key)
            handleDecodeResult(decodeResult)
        }

        binding.btnBackToDashboard.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun handleDecodeResult(result: DecodeResult) {
        when (result.status) {
            DecodeStatus.SUCCESS -> {
                binding.tvDecodedMessageResult.text = result.message
                binding.cardDecodedMessageDisplay.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Message decoded successfully!", Toast.LENGTH_LONG).show() // Adjusted toast message
            }
            DecodeStatus.KEY_MISMATCH -> {
                binding.cardDecodedMessageDisplay.visibility = View.GONE
                Toast.makeText(requireContext(), "Wrong Key! Attempting to alert encoder.", Toast.LENGTH_LONG).show()
                if (!result.encoderEmail.isNullOrEmpty()) {
                    latestEncoderEmailForAlert = result.encoderEmail
                    checkAndRequestPermissions()
                } else {
                    Toast.makeText(requireContext(), "Encoder email not found in image data.", Toast.LENGTH_SHORT).show()
                }
            }
            DecodeStatus.MALFORMED_DATA -> {
                binding.cardDecodedMessageDisplay.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: Image data is malformed.", Toast.LENGTH_LONG).show()
            }
            DecodeStatus.DECODING_ERROR -> {
                binding.cardDecodedMessageDisplay.visibility = View.GONE
                Toast.makeText(requireContext(), "Decoding Error! Image might not be encoded or is corrupted.", Toast.LENGTH_LONG).show()
            }
            DecodeStatus.INVALID_SIGNATURE -> {
                binding.cardDecodedMessageDisplay.visibility = View.GONE
                Toast.makeText(requireContext(), "This image does not appear to be encoded by PixelVault or is corrupted.", Toast.LENGTH_LONG).show()
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
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            // All permissions already granted
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
        val actualUserIdentifier = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown Decoder"
        val subject = "PixelVault: Incorrect Key Attempt"
        var body = "Hello,\n\nAn attempt was made to decode your image with an incorrect key."
            .plus("\n\nAttempt made by: $actualUserIdentifier")
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "DecodeFragment"
    }
}
