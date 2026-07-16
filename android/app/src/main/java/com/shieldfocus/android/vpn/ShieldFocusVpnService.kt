package com.shieldfocus.android.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.shieldfocus.android.MainActivity
import com.shieldfocus.android.data.ProtectionStore
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class ShieldFocusVpnService : VpnService() {
    private val running = AtomicBoolean(false)
    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTunnel()
            ACTION_STOP -> stopTunnel()
        }

        return START_STICKY
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun startTunnel() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        worker = Thread {
            runTunnel()
        }.also { thread ->
            thread.name = "ShieldFocusVpn"
            thread.start()
        }
    }

    private fun runTunnel() {
        val store = ProtectionStore(applicationContext)
        val settings = store.loadSettings()
        val blockedDomains = store.loadBlockedDomains()
        val allowedDomains = store.loadAllowedDomains()
        val dnsServers = collectDnsServers().ifEmpty {
            listOf(
                InetAddress.getByName("1.1.1.1") as Inet4Address,
                InetAddress.getByName("8.8.8.8") as Inet4Address
            )
        }

        val builder = Builder()
            .setSession("ShieldFocus DNS Filter")
            .setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .setBlocking(true)

        dnsServers.forEach { server ->
            builder.addRoute(server, 32)
            builder.addDnsServer(server)
        }

        val descriptor = try {
            builder.establish()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to establish VPN", error)
            null
        }

        if (descriptor == null) {
            running.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        tunnel = descriptor

        FileInputStream(descriptor.fileDescriptor).use { input ->
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                val packetBuffer = ByteArray(32767)

                while (running.get()) {
                    val length = try {
                        input.read(packetBuffer)
                    } catch (error: Exception) {
                        Log.e(TAG, "VPN read failed", error)
                        break
                    }

                    if (length <= 0) {
                        continue
                    }

                    val packet = packetBuffer.copyOf(length)
                    val query = DnsPacketCodec.parse(packet) ?: continue
                    if (query.hostname.isBlank()) {
                        continue
                    }

                    val decision = DomainPolicy.decide(
                        hostname = query.hostname,
                        blockedDomains = blockedDomains,
                        allowedDomains = allowedDomains,
                        strictMode = settings.strictMode
                    )

                    val dnsPayload = if (decision.allow) {
                        DnsForwarder.forward(query, this) ?: continue
                    } else {
                        DnsPacketCodec.buildBlockedDnsPayload(query)
                    }

                    val responsePacket = DnsPacketCodec.buildResponsePacket(query, dnsPayload)

                    try {
                        output.write(responsePacket)
                        output.flush()
                    } catch (error: Exception) {
                        Log.e(TAG, "VPN write failed", error)
                        break
                    }
                }
            }
        }

        stopTunnel()
    }

    private fun stopTunnel() {
        if (!running.compareAndSet(true, false)) {
            tunnel?.close()
            tunnel = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        try {
            tunnel?.close()
        } catch (_: Exception) {
        } finally {
            tunnel = null
        }

        worker?.interrupt()
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun collectDnsServers(): List<Inet4Address> {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val activeNetwork: Network = connectivityManager.activeNetwork ?: return emptyList()
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return emptyList()

        return linkProperties.dnsServers.filterIsInstance<Inet4Address>()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ShieldFocus protection",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("ShieldFocus is active")
            .setContentText("Filtering DNS requests on-device")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "com.shieldfocus.android.vpn.action.START"
        const val ACTION_STOP = "com.shieldfocus.android.vpn.action.STOP"

        private const val CHANNEL_ID = "shieldfocus_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "ShieldFocusVpn"
    }
}
