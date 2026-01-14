package com.zebra.felicarftest

import android.content.Context
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.zebra.felicarftest.ui.theme.FelicaRFTestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG = "MyUsbAccessoryKotlin"
    }

    private lateinit var usbManager: UsbManager
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var accessory: UsbAccessory? = null
    private var communicationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Get the UsbManager system service using Kotlin's extension function
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        enableEdgeToEdge()
        setContent {
            FelicaRFTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if an accessory was attached while the app was paused
        // and try to open it.
        val accessories = usbManager.accessoryList
        val accessoryToOpen = accessories?.get(0)
        accessoryToOpen?.let { openAccessory(it) }
    }

    override fun onPause() {
        super.onPause()
        closeAccessory()
    }

    private fun openAccessory(acc: UsbAccessory) {
        // Attempt to open the accessory. This may fail if permission is not granted.
        fileDescriptor = usbManager.openAccessory(acc)
        fileDescriptor?.let {
            this.accessory = acc
            val fd = it.fileDescriptor

            val inputStream = FileInputStream(fd)
            val outputStream = FileOutputStream(fd)

            // Cancel any previous communication job
            communicationJob?.cancel()

            // Launch a new coroutine on the IO dispatcher for communication
            communicationJob = lifecycleScope.launch(Dispatchers.IO) {
                Log.d(TAG, "Communication coroutine started.")
                handleCommunication(inputStream, outputStream)
            }
            Log.d(TAG, "Accessory opened successfully.")
        } ?: run {
            Log.e(TAG, "Failed to open accessory.")
        }
    }

    /**
     * This is the core communication loop.
     * It runs in a background coroutine.
     */
    private suspend fun handleCommunication(inputStream: FileInputStream, outputStream: FileOutputStream) = withContext(
        Dispatchers.IO) {
        val buffer = ByteArray(16384)
        var bytesRead: Int

        while (isActive) { // Loop until the coroutine is cancelled
            try {
                // 1. READ: Wait for a message from the Windows PC
                bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    val receivedMessage = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                    Log.d(TAG, "Received $bytesRead bytes: $receivedMessage")

                    // --- THIS IS THE RESPONSE PART ---
                    // 2. WRITE: Send a response back to the Windows PC
                    val responseMessage = "Hello from Kotlin! You said: '$receivedMessage'"
                    outputStream.write(responseMessage.toByteArray(StandardCharsets.UTF_8))
                    Log.d(TAG, "Responded: $responseMessage")
                    // --- END OF RESPONSE PART ---
                }
            } catch (e: IOException) {
                Log.e(TAG, "Communication error: ", e)
                break // Exit the loop on error
            }
        }
        Log.d(TAG, "Communication coroutine ended.")
    }

    private fun closeAccessory() {
        Log.d(TAG, "Closing accessory.")
        // Cancel the running communication job
        communicationJob?.cancel()
        communicationJob = null

        try {
            // Close the file descriptor, which also closes the streams
            fileDescriptor?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing file descriptor", e)
        } finally {
            fileDescriptor = null
            accessory = null
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
