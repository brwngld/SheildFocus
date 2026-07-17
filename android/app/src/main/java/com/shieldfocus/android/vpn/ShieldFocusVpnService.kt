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
import com.shieldfocus.android.model.Decision
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
    Error
}

data class VpnConnectionStatus(
    val state: VpnConnectionState = VpnConnectionState.Disconnected,
    val connectedAtMillis: Long? = null,
    val errorMessage: String? = null
)

class ShieldFocusVpnService : VpnService() {
    private val running = AtomicBoolean(false)
    private val tunnelGeneration = AtomicInteger(0)
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
        if (running.get()) {
            stopTunnel()
        }
        super.onDestroy()
    }

    @Synchronized
    private fun startTunnel() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        updateStatus(VpnConnectionState.Connecting)

        val generation = tunnelGeneration.incrementAndGet()

        try {
            createNotificationChannel()
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (error: RuntimeException) {
            Log.e(TAG, "Failed to start VPN foreground service", error)
            updateStatus(VpnConnectionState.Error, errorMessage = "Unable to start protection")
            running.set(false)
            tunnelGeneration.incrementAndGet()
            stopSelf()
            return
        }

        worker = Thread {
            try {
                runTunnel(generation)
            } catch (error: Exception) {
                Log.e(TAG, "VPN worker failed", error)
                updateStatus(VpnConnectionState.Error, errorMessage = "Protection stopped unexpectedly")
            } finally {
                finishTunnel(generation)
            }
        }.also { thread ->
            thread.name = "ShieldFocusVpn"
            thread.start()
        }
    }

    private fun runTunnel(generation: Int) {
        val store = ProtectionStore(applicationContext)
        val settings = store.loadSettings()
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
            updateStatus(VpnConnectionState.Error, errorMessage = "Unable to establish the VPN connection")
            return
        }

        if (!attachTunnel(generation, descriptor)) {
            descriptor.close()
            return
        }

        updateStatus(VpnConnectionState.Connected, connectedAtMillis = System.currentTimeMillis())

        FileInputStream(descriptor.fileDescriptor).use { input ->
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                val packetBuffer = ByteArray(32767)

                while (running.get() && tunnelGeneration.get() == generation) {
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

                val activeScheduleIds = store.loadActiveScheduleIds()
                val blockedDomains = store.loadEffectiveBlockedDomains(activeScheduleIds)

                val decision = DomainPolicy.decide(
                    hostname = query.hostname,
                    blockedDomains = blockedDomains,
                        allowedDomains = allowedDomains,
                        strictMode = settings.strictMode
                    )

                    if (settings.loggingEnabled) {
                        store.appendDecision(
                            Decision(
                                domain = query.hostname,
                                allow = decision.allow,
                                reason = decision.reason
                            )
                        )
                    }

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

    }

    @Synchronized
    private fun attachTunnel(generation: Int, descriptor: ParcelFileDescriptor): Boolean {
        if (!running.get() || tunnelGeneration.get() != generation) {
            return false
        }
        tunnel = descriptor
        return true
    }

    @Synchronized
    private fun finishTunnel(generation: Int) {
        if (tunnelGeneration.get() != generation) {
            return
        }

        val wasDisconnecting = connectionStatus.value.state == VpnConnectionState.Disconnecting
        running.set(false)
        tunnel = null
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (wasDisconnecting || connectionStatus.value.state == VpnConnectionState.Connected) {
            updateStatus(VpnConnectionState.Disconnected)
        }
    }

    @Synchronized
    private fun stopTunnel() {
        updateStatus(VpnConnectionState.Disconnecting)
        tunnelGeneration.incrementAndGet()
        if (!running.compareAndSet(true, false)) {
            try {
                tunnel?.close()
            } catch (_: Exception) {
            }
            tunnel = null
            worker = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            updateStatus(VpnConnectionState.Disconnected)
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
        updateStatus(VpnConnectionState.Disconnected)
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

        private val mutableConnectionStatus = MutableStateFlow(VpnConnectionStatus())
        val connectionStatus: StateFlow<VpnConnectionStatus> = mutableConnectionStatus.asStateFlow()

        fun reportConnecting() {
            updateStatus(VpnConnectionState.Connecting)
        }

        fun reportError(message: String) {
            updateStatus(VpnConnectionState.Error, errorMessage = message)
        }

        fun reportDisconnecting() {
            updateStatus(VpnConnectionState.Disconnecting)
        }

        private fun updateStatus(
            state: VpnConnectionState,
            connectedAtMillis: Long? = null,
            errorMessage: String? = null
        ) {
            mutableConnectionStatus.value = VpnConnectionStatus(state, connectedAtMillis, errorMessage)
        }

        private const val CHANNEL_ID = "shieldfocus_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "ShieldFocusVpn"
    }

    private fun updateStatus(
        state: VpnConnectionState,
        connectedAtMillis: Long? = null,
        errorMessage: String? = null
    ) {
        Companion.updateStatus(state, connectedAtMillis, errorMessage)
    }
}
