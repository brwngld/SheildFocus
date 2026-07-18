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
import java.net.UnknownHostException
import java.io.IOException
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

data class DnsFamilyHealth(
    val processedRequests: Int = 0,
    val forwardingFailures: Int = 0,
    val lastSuccessMillis: Long? = null,
    val lastFailureMillis: Long? = null
)

data class VpnDnsHealth(
    val ipv4: DnsFamilyHealth = DnsFamilyHealth(),
    val ipv6: DnsFamilyHealth = DnsFamilyHealth()
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
            null -> {
                val settings = ProtectionStore(applicationContext).loadSettings()
                if (settings.enabled && settings.restartAfterInterruption) {
                    startTunnel()
                }
            }
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
        resetDnsHealth()

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
            updateStatus(VpnConnectionState.Error, errorMessage = describeVpnError(error, "start protection"))
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
                updateStatus(VpnConnectionState.Error, errorMessage = describeVpnError(error, "run protection"))
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
        val routeSelection = DnsRoutePolicy.select(
            networkDnsServers = collectDnsServers(),
            fallbackDnsServers = fallbackDnsResolvers(),
            strictDnsResolvers = strictDnsResolvers(),
            strictMode = settings.strictMode,
            ipv4Enabled = settings.ipv4DnsEnabled,
            ipv6Enabled = settings.ipv6DnsEnabled
        )

        val builder = Builder()
            .setSession("ShieldFocus DNS Filter")
            .setMtu(1500)
            .setBlocking(true)

        if (settings.ipv4DnsEnabled) {
            builder.addAddress("10.10.0.2", 32)
        }
        if (settings.ipv6DnsEnabled) {
            builder.addAddress("fd00:1:fd00:1::1", 128)
        }

        routeSelection.routedDnsServers.forEach { server ->
            val routeAddress = server.hostAddress ?: return@forEach
            builder.addRoute(routeAddress, if (server.address.size == 4) 32 else 128)
        }
        routeSelection.networkDnsServers.forEach { server ->
            builder.addDnsServer(server)
        }

        val descriptor = try {
            builder.establish()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to establish VPN", error)
            updateStatus(VpnConnectionState.Error, errorMessage = describeVpnError(error, "create the VPN tunnel"))
            null
        }

        if (descriptor == null) {
            if (connectionStatus.value.state != VpnConnectionState.Error) {
                updateStatus(VpnConnectionState.Error, errorMessage = "Android could not create the VPN tunnel. Check that another VPN is not active, then try again.")
            }
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
                        if (running.get()) {
                            updateStatus(VpnConnectionState.Error, errorMessage = describeVpnError(error, "read VPN traffic"))
                        }
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
                        val forwarded = DnsForwarder.forward(query, this, settings.dnsTimeoutMillis)
                        if (forwarded == null) {
                            recordDnsFailure(query.ipVersion)
                            continue
                        }
                        recordDnsSuccess(query.ipVersion)
                        forwarded
                    } else {
                        recordDnsSuccess(query.ipVersion)
                        DnsPacketCodec.buildBlockedDnsPayload(query)
                    }

                    val responsePacket = DnsPacketCodec.buildResponsePacket(query, dnsPayload)

                    try {
                        output.write(responsePacket)
                        output.flush()
                    } catch (error: Exception) {
                        Log.e(TAG, "VPN write failed", error)
                        if (running.get()) {
                            updateStatus(VpnConnectionState.Error, errorMessage = describeVpnError(error, "write VPN traffic"))
                        }
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

    private fun collectDnsServers(): List<InetAddress> {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val activeNetwork: Network = connectivityManager.activeNetwork ?: return emptyList()
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return emptyList()

        return linkProperties.dnsServers
    }

    private fun strictDnsResolvers(): List<InetAddress> {
        return STRICT_DNS_RESOLVER_ADDRESSES.mapNotNull { address ->
            runCatching { InetAddress.getByName(address) }.getOrNull()
        }
    }

    private fun fallbackDnsResolvers(): List<InetAddress> {
        return listOf(
            InetAddress.getByName("1.1.1.1") as Inet4Address,
            InetAddress.getByName("8.8.8.8") as Inet4Address,
            InetAddress.getByName("2606:4700:4700::1111"),
            InetAddress.getByName("2001:4860:4860::8888")
        )
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

    private fun describeVpnError(error: Throwable, action: String): String = when (error) {
        is SecurityException -> "Android blocked permission to $action. Allow VPN and notification access, then try again."
        is UnknownHostException -> "No DNS server could be reached. Check your internet connection, then try again."
        is IOException -> "The VPN connection was interrupted while trying to $action. Check your network and try again."
        is IllegalStateException -> when {
            error.message?.contains("multiple DataStores", ignoreCase = true) == true ->
                "ShieldFocus could not open its settings safely. Close and reopen the app, then try again."
            else -> "Android was not ready to $action. Turn off any other VPN and try again."
        }
        else -> "ShieldFocus could not $action (${error.javaClass.simpleName}). Please try again."
    }

    companion object {
        const val ACTION_START = "com.shieldfocus.android.vpn.action.START"
        const val ACTION_STOP = "com.shieldfocus.android.vpn.action.STOP"

        private val mutableConnectionStatus = MutableStateFlow(VpnConnectionStatus())
        val connectionStatus: StateFlow<VpnConnectionStatus> = mutableConnectionStatus.asStateFlow()
        private val mutableDnsHealth = MutableStateFlow(VpnDnsHealth())
        val dnsHealth: StateFlow<VpnDnsHealth> = mutableDnsHealth.asStateFlow()

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

        private fun resetDnsHealth() {
            mutableDnsHealth.value = VpnDnsHealth()
        }

        private fun recordDnsSuccess(ipVersion: Int) {
            val now = System.currentTimeMillis()
            mutableDnsHealth.value = mutableDnsHealth.value.update(ipVersion) { current ->
                current.copy(
                    processedRequests = current.processedRequests + 1,
                    lastSuccessMillis = now
                )
            }
        }

        private fun recordDnsFailure(ipVersion: Int) {
            val now = System.currentTimeMillis()
            mutableDnsHealth.value = mutableDnsHealth.value.update(ipVersion) { current ->
                current.copy(
                    forwardingFailures = current.forwardingFailures + 1,
                    lastFailureMillis = now
                )
            }
        }

        private fun VpnDnsHealth.update(
            ipVersion: Int,
            transform: (DnsFamilyHealth) -> DnsFamilyHealth
        ): VpnDnsHealth {
            return if (ipVersion == 6) copy(ipv6 = transform(ipv6)) else copy(ipv4 = transform(ipv4))
        }

        private const val CHANNEL_ID = "shieldfocus_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "ShieldFocusVpn"
        private val STRICT_DNS_RESOLVER_ADDRESSES = listOf(
            "1.1.1.1",
            "1.0.0.1",
            "8.8.8.8",
            "8.8.4.4",
            "9.9.9.9",
            "149.112.112.112",
            "94.140.14.14",
            "94.140.15.15",
            "208.67.222.222",
            "208.67.220.220",
            "2606:4700:4700::1111",
            "2606:4700:4700::1001",
            "2001:4860:4860::8888",
            "2001:4860:4860::8844",
            "2620:fe::fe",
            "2620:fe::9",
            "2a10:50c0::ad1:ff",
            "2a10:50c0::ad2:ff",
            "2620:119:35::35",
            "2620:119:53::53"
        )
    }

    private fun updateStatus(
        state: VpnConnectionState,
        connectedAtMillis: Long? = null,
        errorMessage: String? = null
    ) {
        Companion.updateStatus(state, connectedAtMillis, errorMessage)
    }

    private fun resetDnsHealth() = Companion.resetDnsHealth()

    private fun recordDnsSuccess(ipVersion: Int) = Companion.recordDnsSuccess(ipVersion)

    private fun recordDnsFailure(ipVersion: Int) = Companion.recordDnsFailure(ipVersion)
}
