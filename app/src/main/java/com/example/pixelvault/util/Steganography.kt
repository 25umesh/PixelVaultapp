package com.example.pixelvault.util

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
// Removed java.util.* to avoid conflict if not explicitly used by non-AES part
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class DecodeStatus {
    SUCCESS,
    KEY_MISMATCH,
    DECODING_ERROR,
    MALFORMED_DATA
}

data class DecodeResult(
    val status: DecodeStatus,
    val message: String? = null,
    val encoderEmail: String? = null
)

object Steganography {

    private const val LENGTH_BITS = 32
    private const val TAG = "Steganography_Util"
    private const val DELIMITER = ":"

    // Encode method for encoderEmail:key:message format (Non-AES for this path)
    fun encode(image: Bitmap, encoderEmail: String, key: String, message: String): Bitmap? {
        Log.d(TAG, "Encode (Email/Key/Msg): Image dimensions: ${image.width}x${image.height}")
        if (encoderEmail.contains(DELIMITER) || key.contains(DELIMITER)) {
            Log.e(TAG, "Encode Error: Encoder email or key cannot contain the delimiter '$DELIMITER'")
            return null
        }

        val encodedImage = image.copy(Bitmap.Config.ARGB_8888, true)
        val dataToEmbedString = "$encoderEmail$DELIMITER$key$DELIMITER$message"
        val dataToEmbed = dataToEmbedString.toByteArray(StandardCharsets.UTF_8)
        val actualDataLengthInBits = dataToEmbed.size * 8

        val totalBitsToEmbedInImage = LENGTH_BITS + actualDataLengthInBits
        val imageCapacityBits = encodedImage.width * encodedImage.height

        if (totalBitsToEmbedInImage > imageCapacityBits) {
            Log.e(TAG, "Encode Error: Data (total ${totalBitsToEmbedInImage} bits) too long for image capacity (${imageCapacityBits} bits).")
            return null
        }

        Log.d(TAG, "Encoding: Data to embed: '${dataToEmbedString.take(70)}...'")
        Log.d(TAG, "Encoding: Actual data length in bits: $actualDataLengthInBits")

        var bitIndex = 0

        // 1. Embed the length
        for (i in 0 until LENGTH_BITS) {
            val x = bitIndex / encodedImage.height
            val y = bitIndex % encodedImage.height
            if (x >= encodedImage.width) {
                 Log.e(TAG, "Encode Error: Image width exceeded while writing length at bitIndex $bitIndex (x=$x, y=$y).")
                 return null
            }
            val lengthBitToEmbed = (actualDataLengthInBits shr (LENGTH_BITS - 1 - i)) and 1
            val originalPixel = encodedImage.getPixel(x, y)
            val originalBlue = originalPixel and 0xFF
            val newBlue = (originalBlue and 0xFE) or lengthBitToEmbed
            encodedImage.setPixel(x, y, (originalPixel and 0xFFFFFF00.toInt()) or newBlue)
            bitIndex++
        }

        // 2. Embed the actual data
        for (i in 0 until actualDataLengthInBits) {
            val x = bitIndex / encodedImage.height
            val y = bitIndex % encodedImage.height
             if (x >= encodedImage.width) {
                 Log.e(TAG, "Encode Error: Image width exceeded while writing data at bitIndex $bitIndex (x=$x, y=$y).")
                 return null
            }
            val byteIndex = i / 8
            val bitInByteIndex = i % 8
            val dataBitToEmbed = (dataToEmbed[byteIndex].toInt() shr (7 - bitInByteIndex)) and 1
            val originalPixel = encodedImage.getPixel(x, y)
            val originalBlue = originalPixel and 0xFF
            val newBlue = (originalBlue and 0xFE) or dataBitToEmbed
            encodedImage.setPixel(x, y, (originalPixel and 0xFFFFFF00.toInt()) or newBlue)
            bitIndex++
        }
        Log.d(TAG, "Encoding: Successfully embedded ${LENGTH_BITS} length bits and ${actualDataLengthInBits} data bits. Total: $bitIndex")
        return encodedImage
    }

    // Decode method for encoderEmail:key:message format (Non-AES for this path)
    fun decode(image: Bitmap, inputKey: String): DecodeResult {
        Log.d(TAG, "Decode (Email/Key/Msg): Image dimensions: ${image.width}x${image.height}")
        Log.d(TAG, "Decoding attempt with inputKey: '$inputKey'")
        var bitIndex = 0
        var actualDataLengthInBits = 0

        if (LENGTH_BITS > image.width * image.height) {
            Log.e(TAG, "Decode Error: Image too small to contain length header.")
            return DecodeResult(DecodeStatus.DECODING_ERROR)
        }

        for (i in 0 until LENGTH_BITS) {
            val x = bitIndex / image.height
            val y = bitIndex % image.height
            val pixel = image.getPixel(x, y)
            val lsb = (pixel and 0xFF) and 1
            actualDataLengthInBits = (actualDataLengthInBits shl 1) or lsb
            bitIndex++
        }
        Log.d(TAG, "Decoding: Decoded actual data length in bits: $actualDataLengthInBits")

        if (actualDataLengthInBits <= 0 || actualDataLengthInBits > (image.width * image.height - LENGTH_BITS)) {
            Log.e(TAG, "Decode Error: Invalid decoded data length: $actualDataLengthInBits.")
            return DecodeResult(DecodeStatus.DECODING_ERROR)
        }
        if (actualDataLengthInBits % 8 != 0) {
             Log.w(TAG, "Decode Warning: Data length $actualDataLengthInBits not multiple of 8.")
        }

        val numberOfBytesToRead = actualDataLengthInBits / 8
        if (numberOfBytesToRead == 0 && actualDataLengthInBits > 0) {
            Log.e(TAG, "Decode Error: Data length ($actualDataLengthInBits bits) < 8 bits.")
            return DecodeResult(DecodeStatus.DECODING_ERROR)
        }
        val dataBytes = ByteArray(numberOfBytesToRead)
        var currentByte = 0
        var bitsInCurrentByteCount = 0
        var byteArrayIndex = 0

        for (i in 0 until actualDataLengthInBits) {
            val x = bitIndex / image.height
            val y = bitIndex % image.height
            if (x >= image.width) {
                 Log.e(TAG, "Decode Error: Image read out of bounds for data bit $i.")
                 return DecodeResult(DecodeStatus.DECODING_ERROR)
            }
            val pixel = image.getPixel(x, y)
            val lsb = (pixel and 0xFF) and 1
            currentByte = (currentByte shl 1) or lsb
            bitsInCurrentByteCount++
            if (bitsInCurrentByteCount == 8) {
                if (byteArrayIndex < dataBytes.size) {
                    dataBytes[byteArrayIndex] = currentByte.toByte()
                    byteArrayIndex++
                } else {
                     Log.w(TAG, "Decode Warning: Byte array index out of bounds during data read.")
                }
                currentByte = 0
                bitsInCurrentByteCount = 0
            }
            bitIndex++
        }
        
        val fullDecodedString = String(dataBytes, StandardCharsets.UTF_8)
        Log.d(TAG, "Decoding: Full decoded string (first 70 chars): '${fullDecodedString.take(70)}...'")

        val parts = fullDecodedString.split(DELIMITER, limit = 3)
        if (parts.size < 3) {
            Log.e(TAG, "Decode Error: Malformed data. Expected 3 parts (email:key:message), got ${parts.size}")
            // If data is malformed, we can't reliably get an encoder email.
            return DecodeResult(DecodeStatus.MALFORMED_DATA)
        }

        val encoderEmail = parts[0]
        val embeddedKey = parts[1]
        val actualMessage = parts[2]

        Log.d(TAG, "Decoding: Parsed Encoder Email: '$encoderEmail'")
        Log.d(TAG, "Decoding: Parsed Embedded Key: '$embeddedKey'")
        Log.d(TAG, "Decoding: Parsed Actual Message (first 50): '${actualMessage.take(50)}...'")

        return if (inputKey == embeddedKey) {
            Log.d(TAG, "Decoding: SUCCESS - Input key matches embedded key.")
            DecodeResult(DecodeStatus.SUCCESS, actualMessage, encoderEmail)
        } else {
            Log.w(TAG, "Decoding: FAILED - Key mismatch. Input key: '$inputKey', Embedded key: '$embeddedKey'")
            DecodeResult(DecodeStatus.KEY_MISMATCH, null, encoderEmail)
        }
    }

    // --- AES Encryption/Decryption Helper Functions (kept for potential other uses) ---
    private const val AES_TAG = "Steganography_AES"
    private const val ITERATIONS_AES = 65536
    private const val KEY_LENGTH_AES = 256

    private fun deriveKeyAES(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS_AES, KEY_LENGTH_AES)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encryptAES(password: String, message: String): ByteArray? { // Made public if needed elsewhere
        return try {
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            val key = deriveKeyAES(password, salt)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
            val ciphertext = cipher.doFinal(message.toByteArray(StandardCharsets.UTF_8))
            val buffer = ByteBuffer.allocate(16 + 16 + ciphertext.size)
            buffer.put(salt)
            buffer.put(iv)
            buffer.put(ciphertext)
            buffer.array()
        } catch (e: Exception) {
            Log.e(AES_TAG, "AES Encryption failed: ${e.message}")
            null
        }
    }

    fun decryptAES(password: String, encrypted: ByteArray): String? { // Made public if needed elsewhere
        if (encrypted.size < 32) {
            Log.e(AES_TAG, "AES Decryption failed: encrypted data too short")
            return null
        }
        return try {
            val salt = encrypted.copyOfRange(0, 16)
            val iv = encrypted.copyOfRange(16, 32)
            val ciphertext = encrypted.copyOfRange(32, encrypted.size)
            val key = deriveKeyAES(password, salt)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            val plainBytes = cipher.doFinal(ciphertext)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(AES_TAG, "AES Decryption failed: ${e.message}")
            null
        }
    }
}
