package com.example.pixelvault.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object PhotoCaptureHelper {

    private const val TAG = "PhotoCaptureHelper"

    fun capturePhoto(context: Context, callback: (File?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted.")
            callback(null)
            return
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null
        var imageReader: ImageReader? = null

        try {
            // Find a front-facing camera if available, otherwise any camera
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id
                    break
                }
            }
            if (cameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                cameraId = cameraManager.cameraIdList[0] // Fallback to the first available camera
            }

            if (cameraId == null) {
                Log.e(TAG, "No camera found on this device.")
                callback(null)
                return
            }

            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSize = map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width * it.height } ?: run {
                Log.e(TAG, "No suitable output size found for JPEG.")
                callback(null)
                return
            }

            imageReader = ImageReader.newInstance(outputSize!!.width, outputSize.height, ImageFormat.JPEG, 1)
            val mainHandler = Handler(Looper.getMainLooper())

            val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
                val image = reader.acquireLatestImage()
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                var outputPhoto: File? = null
                try {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    outputPhoto = File(context.cacheDir, "IMG_FAILED_ATTEMPT_$timeStamp.jpg")
                    FileOutputStream(outputPhoto).use { output ->
                        output.write(bytes)
                    }
                    Log.d(TAG, "Photo saved to ${outputPhoto.absolutePath}")
                    callback(outputPhoto)
                } catch (e: IOException) {
                    Log.e(TAG, "Error saving photo: ${e.message}", e)
                    callback(null)
                } finally {
                    image.close()
                    cameraDevice?.close()
                }
            }
            imageReader.setOnImageAvailableListener(imageAvailableListener, mainHandler)

            val cameraStateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        captureRequestBuilder.addTarget(imageReader!!.surface)
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0)

                        cameraDevice!!.createCaptureSession(listOf(imageReader!!.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                try {
                                    captureSession!!.capture(captureRequestBuilder.build(), null, mainHandler)
                                } catch (e: CameraAccessException) {
                                    Log.e(TAG, "Camera capture failed: ${e.message}", e)
                                    callback(null)
                                    cameraDevice?.close()
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "Camera configuration failed.")
                                callback(null)
                                cameraDevice?.close()
                            }
                        }, mainHandler)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Error creating capture session: ${e.message}", e)
                        callback(null)
                        cameraDevice?.close()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected.")
                    cameraDevice?.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    callback(null)
                    cameraDevice?.close()
                }
            }

            cameraManager.openCamera(cameraId!!, cameraStateCallback, mainHandler)

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access failed: ${e.message}", e)
            callback(null)
        } catch (e: Exception) {
            Log.e(TAG, "General error during photo capture setup: ${e.message}", e)
            callback(null)
        }
    }
}
