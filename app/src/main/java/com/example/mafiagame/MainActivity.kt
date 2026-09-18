package com.example.mafiagame

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var nearby: NearbyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestNeededPermissions()

        nearby = NearbyManager(this)
        val nameInput = findViewById<EditText>(R.id.nameInput)

        findViewById<Button>(R.id.hostButton).setOnClickListener {
            startActivity(Intent(this, HostActivity::class.java))
        }

        findViewById<Button>(R.id.joinButton).setOnClickListener {
            val myName = nameInput.text.toString().ifBlank { "Player" }
            Toast.makeText(this, "Searching for host...", Toast.LENGTH_SHORT).show()
            nearby.startDiscovery { endpointId, _ ->
                nearby.stopDiscovery()
                runOnUiThread {
                    val intent = Intent(this, PlayerActivity::class.java)
                    intent.putExtra("PLAYER_NAME", myName)
                    intent.putExtra("HOST_ENDPOINT_ID", endpointId)
                    startActivity(intent)
                }
            }
        }
    }

    /**
     * Nearby Connections needs location + Bluetooth + nearby-Wi-Fi permissions
     * depending on Android version. Request everything up front so both host
     * and player flows work without extra prompts mid-game.
     */
    private fun requestNeededPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)
    }
}
