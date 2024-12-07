package com.example.mobilekey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mobilekey.server.GATTServerSampleService

class BootReceiver : BroadcastReceiver() {

//    companion object {
//        const val ACTION_START_SERVER = "com.example.myapp.action.START_SERVER"
//    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted, starting server")
            val startIntent = Intent(context, GATTServerSampleService::class.java).apply {
                action = GATTServerSampleService.ACTION_START_ADVERTISING
            }
            context.startService(startIntent)
        }
    }
}
