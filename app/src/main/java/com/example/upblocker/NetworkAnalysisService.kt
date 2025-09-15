package com.example.upblocker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

class NetworkAnalysisService : VpnService(){
    companion object {
        private const val TAG = "NetworkAnalysisService"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE ="0.0.0.0"
        private const val NOTIFICATION_ID =1001
        private const val CHANNEL_ID = "ad_blocker_channel"
    }
    private var vpnThread:Thread?=null
    private var vpnInterface:ParcelFileDescriptor? =null
    private var isRunning = false
    private var blockedCount =0
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            startVpnService()
        }
        return START_STICKY
    }
    override fun onDestroy() {
        stopVpnService()
        super.onDestroy()
        }
//    @SuppressLint("ForegroundServiceType")
    private fun startVpnService() {
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createServiceNotification())

            vpnInterface = createVpnInterface()
            vpnInterface?.let { Interface->
                vpnThread = thread {
                    runVpnLoop(Interface)
                }
                isRunning = true
                Log.d(TAG, "VPN service started successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN service", e)
        }
    }
    private fun createVpnInterface(): ParcelFileDescriptor? {
        return Builder()
            .setSession("Personal Ad Blocker")
            .addAddress(VPN_ADDRESS, 32)
            .addRoute(VPN_ROUTE, 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")
            .establish()
    }

    private fun runVpnLoop(vpnInterface: ParcelFileDescriptor) {
        try {
            val inputStream = FileInputStream(vpnInterface.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface.fileDescriptor)

            val packet = ByteArray(32767)

            while (isRunning) {
                val length = inputStream.read(packet)
                if (length > 0) {
                    processPacket(packet, length, outputStream)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "VPN loop error", e)
        } catch (e: InterruptedException) {
            Log.d(TAG, "VPN loop interrupted")
        }
    }
    private fun processPacket(packet: ByteArray, length: Int, outputStream: FileOutputStream) {
        try {
            // Basic IP header parsing
            if (length < 20) return // Minimum IP header size

            val version = (packet[0].toInt() shr 4) and 0x0F
            if (version != 4) return // Only IPv4 for now

            val protocol = packet[9].toInt() and 0xFF

            // Focus on UDP packets (DNS is typically UDP)
            if (protocol == 17) { // UDP
                processDnsPacket(packet, length, outputStream)
            } else {
                // Forward other packets as-is
                outputStream.write(packet, 0, length)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error processing packet", e)
        }
    }

    private fun processDnsPacket(packet: ByteArray, length: Int, outputStream: FileOutputStream) {
        try {
            // Extract DNS query information
            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            val udpHeaderStart = ipHeaderLength
            val dnsHeaderStart = udpHeaderStart + 8

            if (length > dnsHeaderStart + 12) { // Minimum DNS header
                val domain = extractDomainFromDnsQuery(packet, dnsHeaderStart, length)

                if (domain != null && shouldBlockDomain(domain)) {
                    Log.d(TAG, "Blocking domain: $domain")
                    blockedCount++
                    updateBlockedCount()
                    // Don't forward blocked requests
                    return
                }
            }

            // Forward allowed DNS requests
            outputStream.write(packet, 0, length)

        } catch (e: IOException) {
            Log.e(TAG, "Error processing DNS packet", e)
        }
    }

    private fun extractDomainFromDnsQuery(packet: ByteArray, dnsStart: Int, length: Int): String? {
        return try {
            // Skip DNS header (12 bytes)
            var pos = dnsStart + 12
            val domain = StringBuilder()

            while (pos < length) {
                val labelLength = packet[pos].toInt() and 0xFF
                if (labelLength == 0) break

                if (domain.isNotEmpty()) {
                    domain.append('.')
                }

                pos++
                repeat(labelLength) {
                    if (pos < length) {
                        domain.append((packet[pos].toInt() and 0xFF).toChar())
                        pos++
                    }
                }
            }

            domain.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting domain", e)
            null
        }
    }

    private fun shouldBlockDomain(domain: String): Boolean {
        val adDomains = listOf(
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "facebook.com/ads",
            "ads.yahoo.com",
            "googletagmanager.com",
            "google-analytics.com",
            "amazon-adsystem.com",
            "adsystem.amazon.com",
            "adnxs.com",
            "adsystem.com",
            "admob.com",
            "ads.twitter.com",
            "analytics.twitter.com",
            "scorecardresearch.com",
            "outbrain.com",
            "taboola.com",
            "quantserve.com"
        )

        return adDomains.any { adDomain ->
            domain.contains(adDomain, ignoreCase = true)
        }
    }

    private fun updateBlockedCount() {
        val prefs = getSharedPreferences("ad_blocker_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("blocked_count", blockedCount)
            .apply()
    }

    private fun stopVpnService() {
        isRunning = false

        vpnThread?.interrupt()
        vpnThread = null

        vpnInterface?.let { Interface ->
            try {
                Interface.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing VPN interface", e)
            }
        }
        vpnInterface = null

        stopForeground(true)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ad Blocker Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Personal Ad Blocker Service"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ad Blocker Active")
            .setContentText("Filtering network traffic - Blocked: $blockedCount")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
