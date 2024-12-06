package com.example.mobilekey

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mobilekey.databinding.ActivityMainBinding
import com.example.mobilekey.server.GATTServerSampleService
import com.example.mobilekey.server.GATTServerSampleService.Companion.serverLogsState

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var serverStatusTextView: TextView
    private lateinit var startServerButton: Button
    private lateinit var stopServerButton: Button
    private lateinit var serverLogsTextView: TextView

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

//        binding.appBarMain.fab.setOnClickListener { view ->
//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                .setAction("Action", null)
//                .setAnchorView(R.id.fab).show()
//        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        serverStatusTextView = findViewById(R.id.server_status_text_view)
        startServerButton = findViewById(R.id.start_server_button)
        stopServerButton = findViewById(R.id.stop_server_button)
        serverLogsTextView = findViewById(R.id.server_logs_text_view)

        startServerButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED
                ) {
                ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ), REQUEST_BLUETOOTH_CONNECT)
            } else {
                startServer()
            }
        }

        stopServerButton.setOnClickListener {
            stopService(Intent(this, GATTServerSampleService::class.java))
            serverStatusTextView.text = "Server status: Stopped"
        }

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Initial update of server logs
        updateServerLogs()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Handle the Settings menu item click
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startServer() {
        val intent = Intent(this, GATTServerSampleService::class.java).apply {
            action = GATTServerSampleService.ACTION_START_ADVERTISING
        }
        startService(intent)
        serverStatusTextView.text = "Server status: Running"
        updateServerLogs()
    }

    private fun stopServer() {
        stopService(Intent(this, GATTServerSampleService::class.java))
        serverStatusTextView.text = "Server status: Stopped"
        updateServerLogs()
    }

    private fun updateServerLogs() {
        // Replace this with actual implementation to get logs from serverLogsState
        serverLogsTextView.text = serverLogsState.toString()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if ((grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[2] == PackageManager.PERMISSION_GRANTED
                    )) {
                startServer()
            } else {
                // Permission denied
                serverStatusTextView.text = "Permission denied: Cannot start server"
            }
        }
    }
}