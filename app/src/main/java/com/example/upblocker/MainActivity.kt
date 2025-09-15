package com.example.upblocker
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var serviceSwitch: Switch
    private lateinit var statusText: TextView
    private lateinit var blockedCountText: TextView
    private lateinit var dataSavedText: TextView
    private lateinit var configureButton: Button
    private lateinit var preferences: SharedPreferences

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startService(Intent(this, NetworkAnalysisService::class.java))
            updateServiceStatus(true)
        } else {
            serviceSwitch.isChecked = false
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = getSharedPreferences("ad_blocker_prefs", Context.MODE_PRIVATE)

        initializeViews()
        setupListeners()
        updateUI()
    }

    private fun initializeViews() {
        serviceSwitch = findViewById(R.id.service_switch)
        statusText = findViewById(R.id.status_text)
        blockedCountText = findViewById(R.id.blocked_count_text)
        dataSavedText = findViewById(R.id.data_saved_text)
        configureButton = findViewById(R.id.configure_button)
    }

    private fun setupListeners() {
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startVpnService()
            } else {
                stopVpnService()
            }
        }

        configureButton.setOnClickListener {
            showConfigurationDialog()
        }
    }

    private fun startVpnService() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // VPN permission required
            vpnPermissionLauncher.launch(intent)
        } else {
            // Permission already granted
            startService(Intent(this, NetworkAnalysisService::class.java))
            updateServiceStatus(true)
        }
    }

    private fun stopVpnService() {
        stopService(Intent(this, NetworkAnalysisService::class.java))
        updateServiceStatus(false)
    }

    private fun updateServiceStatus(isRunning: Boolean) {
        if (isRunning) {
            statusText.text = "Ad Blocker Active"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.green))
        } else {
            statusText.text = "Ad Blocker Inactive"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.red))
        }

        // Save state
        preferences.edit()
            .putBoolean("service_enabled", isRunning)
            .apply()
    }

    private fun updateUI() {
        // Check if service should be running
        val serviceEnabled = preferences.getBoolean("service_enabled", false)
        serviceSwitch.isChecked = serviceEnabled

        // Update statistics
        updateStatistics()

        updateServiceStatus(serviceEnabled)
    }

    private fun updateStatistics() {
        val blockedCount = preferences.getInt("blocked_count", 0)
        val dataSaved = calculateDataSaved(blockedCount)

        blockedCountText.text = "Blocked: $blockedCount requests"
        dataSavedText.text = "Data Saved: ${String.format("%.1f", dataSaved)} MB"
    }

    private fun calculateDataSaved(blockedCount: Int): Double {
        // Estimate: average ad request saves ~50KB
        return (blockedCount * 50.0) / 1024.0 // Convert to MB
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("VPN permission is required for the ad blocker to function. " +
                    "Please grant the permission to continue.")
            .setPositiveButton("Try Again") { _, _ -> startVpnService() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConfigurationDialog() {
        val options = arrayOf(
            "View Blocked Domains",
            "Add Custom Domain",
            "Whitelist Domain",
            "Export Settings",
            "Reset Statistics"
        )

        AlertDialog.Builder(this)
            .setTitle("Configuration Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showBlockedDomains()
                    1 -> showAddCustomDomain()
                    2 -> showWhitelistDomain()
                    3 -> exportSettings()
                    4 -> resetStatistics()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBlockedDomains() {
        val blockedDomains = listOf(
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "facebook.com/ads",
            "ads.yahoo.com",
            "googletagmanager.com",
            "google-analytics.com",
            "amazon-adsystem.com"
        )

        AlertDialog.Builder(this)
            .setTitle("Currently Blocked Domains")
            .setItems(blockedDomains.toTypedArray(), null)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAddCustomDomain() {
        AlertDialog.Builder(this)
            .setTitle("Add Custom Domain")
            .setMessage("Custom domain blocking will be available in Phase 2.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showWhitelistDomain() {
        AlertDialog.Builder(this)
            .setTitle("Whitelist Domain")
            .setMessage("Domain whitelisting will be available in Phase 2.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun exportSettings() {
        AlertDialog.Builder(this)
            .setTitle("Export Settings")
            .setMessage("Settings export will be available in Phase 2.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun resetStatistics() {
        AlertDialog.Builder(this)
            .setTitle("Reset Statistics")
            .setMessage("Are you sure you want to reset all statistics?")
            .setPositiveButton("Reset") { _, _ ->
                preferences.edit()
                    .putInt("blocked_count", 0)
                    .apply()
                updateStatistics()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}