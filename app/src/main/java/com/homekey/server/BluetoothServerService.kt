/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.homekey.server

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.homekey.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


/**
 * A FGS that runs the GATTServer by advertising and allowing devices to connect
 *
 */
class BluetoothServerService : Service() {

    private val binder = LocalBinder()
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var serverSocket: BluetoothServerSocket? = null

    companion object {
        private const val TAG = "BluetoothServerService"
        private const val CHANNEL_ID = "BluetoothServerChannel"
        private const val NOTIFICATION_ID = 1
        private const val SERVICE_NAME = "HomeKeyService"
        const val ACTION_DEVICE_CONNECTED =
            "com.example.com.com.com.homekey.server.ACTION_DEVICE_CONNECTED"

        // Random UUID for our service known between the client and server to allow communication
        val SERVICE_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // ESP32 SPP UUID
//            UUID.fromString("02031405-0607-1809-8A0B-0C80DF9E34FB"); // SPP UUID
        const val ACTION_START_SERVER = "start_srv"
        const val ACTION_STOP_SERVER = "stop_srv"
        const val EXTRA_DEVICE_NAME = "device_name"

        // Important: this is just for simplicity, there are better ways to communicate between
        // a service and an activity/view
        val serverLogsState: MutableStateFlow<String> = MutableStateFlow("")
        val _isServerRunning = MutableStateFlow(false)
        val isServerRunning: StateFlow<Boolean> get() = _isServerRunning
        val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> get() = _isConnected
        val _lastConnectionTime = MutableStateFlow<String>("Never")
        val lastConnectionTime: StateFlow<String> get() = _lastConnectionTime


    }

    override fun onCreate() {
        super.onCreate()
        // If we are missing permission stop the service
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            PackageManager.PERMISSION_GRANTED
        }

        if (permission == PackageManager.PERMISSION_GRANTED) {
//            startInForeground()

            serverLogsState.value = "Opening BT server...\n"
//            startServer()
//            _isServerRunning.value = true
        } else {
            serverLogsState.value = "Missing connect permission\n"
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || !hasAdvertisingPermission()) {
            return START_NOT_STICKY
        }

        // Always start foreground first
        startInForeground()

        when (intent.action) {
            ACTION_START_SERVER -> {
                serverLogsState.value += "Start bluetooth server\n"
                startServer()
                _isServerRunning.value = true
            }

            ACTION_STOP_SERVER -> {
                serverLogsState.value += "Start bluetooth server\n"
                stopServer()
                _isServerRunning.value = false
                stopSelf()
            }

            else -> throw IllegalArgumentException("Unknown action")
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        _isServerRunning.value = false
        stopServer()
        serverLogsState.value += "Server stopped\n"
    }

    private fun startInForeground() {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("Bluetooth Server")
            .setContentText("Running...")
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                100,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(100, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_HIGH,
        )
            .setName("Bluetooth Server channel")
            .setDescription("Channel for the Bluetooth server")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun startServer() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    serverSocket =
                        bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                            SERVICE_NAME,
                            SERVICE_UUID
                        )

                    Log.i(TAG, "Server started, waiting for connections...")

                    val socket: BluetoothSocket? = serverSocket?.accept() // Blocking call
                    Log.i(TAG, "Closing the socket")
                    serverSocket?.close() // Close after accepting one client
                    socket?.let {
                        updateConnectionStatus(true)
                        val deviceName = it.remoteDevice.name ?: "Unknown Device"
                        Log.i(TAG, "Device connected: $deviceName")
                        broadcastDeviceConnected(deviceName)
                        showConnectionNotification(deviceName)
                        handleConnection(it)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error in server socket", e)
                    updateConnectionStatus(false)
                    delay(1) // Wait a bit before retrying
                }
            }
        }
    }

    private fun stopServer() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing server socket", e)
        }
    }

    private fun handleConnection(socket: BluetoothSocket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                socket.use {  // Automatically closes the socket after use
                    socket.use {  // Automatically closes the socket after use
                        val inputStream = socket.inputStream
                        val buffer = ByteArray(1024) // Buffer for reading data
                        var bytes: Int

                        while (true) {
                            try {
//                                socket.outputStream.write(0)
                                // Read data from the input stream
                                bytes = inputStream.read(buffer)
                                if (bytes > 0) {
                                    val receivedData = String(buffer, 0, bytes)
                                    Log.i(TAG, "Received data: $receivedData")

                                    // Optionally: Process or forward the received data
                                    processReceivedData(receivedData)
                                }
                            } catch (e: IOException) {
                                Log.e(TAG, "Error reading from input stream", e)
                                updateConnectionStatus(false)
                                break // Exit the loop if there's an error
                            }
                        }
                    }
                    Log.i(TAG, "Connection handled successfully.")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error handling connection", e)
            } finally {
                updateConnectionStatus(false)
                Log.i(TAG, "Disconnected. Restarting server...")
                delay(1000) // Short pause before restarting
//                startServer()
            }
        }
    }

    private fun processReceivedData(data: String) {
        Log.i(TAG, "Processing data: $data")
        // Add logic to process or handle the received data here
    }

    private fun hasAdvertisingPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        ) == PackageManager.PERMISSION_GRANTED)

    private fun broadcastDeviceConnected(deviceName: String) {
        val intent = Intent(ACTION_DEVICE_CONNECTED)
        intent.putExtra(EXTRA_DEVICE_NAME, deviceName)
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothServerService = this@BluetoothServerService
    }


    internal fun Int.toConnectionStateString() = when (this) {
        BluetoothProfile.STATE_CONNECTED -> "Connected"
        BluetoothProfile.STATE_CONNECTING -> "Connecting"
        BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
        BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting"
        else -> "N/A"
    }

    private fun showConnectionNotification(deviceName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("Bluetooth device connected")
            .setContentText("Connected to $deviceName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID + 1, // Make sure it's different from the foreground ID
            notification
        )
    }

    private fun updateConnectionStatus(connected: Boolean) {
        _isConnected.value = connected
        if (connected) {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            _lastConnectionTime.value = time
        }
    }
}
