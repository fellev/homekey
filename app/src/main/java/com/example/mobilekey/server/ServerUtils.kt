package com.example.mobilekey.server

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.bluetooth.BluetoothAdapter
import androidx.preference.PreferenceManager

object ServerUtils {

    @SuppressLint("MissingPermission")
    private fun setBluetoothAdapterName(context: Context) {
        val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val serverName = sharedPreferences.getString("server_name", "Default Server Name")

        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        bluetoothAdapter?.name = serverName
    }

    fun startServer(context: Context) {
        setBluetoothAdapterName(context)
        val intent = Intent(context, GATTServerSampleService::class.java).apply {
            action = GATTServerSampleService.ACTION_START_ADVERTISING
        }
        context.startService(intent)
    }

    fun stopServer(context: Context) {
        val intent = Intent(context, GATTServerSampleService::class.java)
        context.stopService(intent)
    }
}