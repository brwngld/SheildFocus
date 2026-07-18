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
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.shieldfocus.android.MainActivity
import com.shieldfocus.android.data.ProtectionStore
import com.shieldfocus.android.model.Decision
import com.shieldfocus.android.model.ProtectionSettings
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
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
    val blockedRequests: Int = 0,
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
    @Volatile private var requestExecutor: ThreadPoolExecutor? = null
    @Volatile private var decisionLogExecutor: ThreadPoolExecutor? = null

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isLockdownEnabled) {
            running.set(false)
            updateStatus(
                VpnConnectionState.Error,
                errorMessage = "Turn off 'Block connections without VPN' in Android VPN settings. ShieldFocus currently filters DNS only and cannot carry all internet traffic."
            )
            stopSelf()
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
        val routeSelection = DnsRoutePolicy.select(
            networkDnsServers = collectDnsServers(),
            fallbackDnsServers = fallbackDnsResolvers(),
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
        routeSelection.routedDnsServers.forEach { server ->
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

        val executor = ThreadPoolExecutor(
            2,
            4,
            30L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(128),
            { task -> Thread(task, "ShieldFocusDnsWorker").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        )
        requestExecutor = executor
        val logExecutor = ThreadPoolExecutor(
            1,
            1,
            30L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(64),
            { task -> Thread(task, "ShieldFocusDecisionLog").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardOldestPolicy()
        )
        decisionLogExecutor = logExecutor
        val activeScheduleIds = store.loadActiveScheduleIds()
        val settingsReference = AtomicReference(settings)
        val policyReference = AtomicReference(store.loadPreparedDomainPolicy(activeScheduleIds, settings.strictMode))
        val outputLock = Any()
        val lastUnsupportedLogMillis = AtomicLong(0L)

        try {
        FileInputStream(descriptor.fileDescriptor).use { input ->
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                val packetBuffer = ByteArray(32767)
                var policyRefreshAt = 0L

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
                val packetType = CapturedPacketClassifier.classify(packet)
                if (packetType != CapturedPacketType.UdpDns) {
                    logUnsupportedPacketRateLimited(packetType, lastUnsupportedLogMillis)
                    continue
                }
                val query = DnsPacketCodec.parse(packet)
                if (query == null) {
                    logUnsupportedPacketRateLimited(CapturedPacketType.Malformed, lastUnsupportedLogMillis)
                    continue
                }
                if (query.hostname.isBlank()) {
                    continue
                }

                val now = System.currentTimeMillis()
                if (now >= policyRefreshAt) {
                    val currentSettings = store.loadSettings()
                    settingsReference.set(currentSettings)
                    policyReference.set(
                        store.loadPreparedDomainPolicy(store.loadActiveScheduleIds(), currentSettings.strictMode)
                    )
                    policyRefreshAt = now + POLICY_REFRESH_MILLIS
                }
                try {
                    executor.execute {
                        processDnsQuery(
                            query = query,
                            policy = policyReference.get(),
                            settings = settingsReference.get(),
                            routeSelection = routeSelection,
                            store = store,
                            output = output,
                            outputLock = outputLock
                        )
                    }
                } catch (_: RejectedExecutionException) {
                    Log.w(TAG, "DNS work queue is full; returning SERVFAIL")
                    writeResponse(output, outputLock, query, DnsFailureResponse.servFail(query))
                }
            }
            executor.shutdown()
            runCatching { executor.awaitTermination(1, TimeUnit.SECONDS) }
            executor.shutdownNow()
        }
        }
        } finally {
            requestExecutor = null
            executor.shutdownNow()
            runCatching { executor.awaitTermination(1, TimeUnit.SECONDS) }
            decisionLogExecutor = null
            logExecutor.shutdown()
            runCatching { logExecutor.awaitTermination(1, TimeUnit.SECONDS) }
            logExecutor.shutdownNow()
        }
    }

    private fun processDnsQuery(
        query: DnsQueryPacket,
        policy: PreparedDomainPolicy,
        settings: ProtectionSettings,
        routeSelection: DnsRouteSelection,
        store: ProtectionStore,
        output: FileOutputStream,
        outputLock: Any
    ) {
        try {
            val decision = policy.decide(query.hostname)
            val safeSearchTarget = SafeSearchPolicy.cnameTarget(query.hostname, settings.safeSearchEnabled)
            val dnsPayload = when {
                !decision.allow -> {
                    recordDnsBlocked(query.ipVersion)
                    enqueueDecisionLog(
                        store,
                        settings,
                        Decision(query.hostname, false, decision.logReason())
                    )
                    DnsPacketCodec.buildBlockedDnsPayload(query)
                }
                safeSearchTarget != null -> {
                    recordDnsSuccess(query.ipVersion)
                    enqueueDecisionLog(
                        store,
                        settings,
                        Decision(query.hostname, true, "allowed:safesearch")
                    )
                    DnsPacketCodec.buildCnameDnsPayload(query, safeSearchTarget)
                }
                else -> when (
                    val result = DnsForwarder.forwardResult(
                        query = query,
                        vpnService = this,
                        timeoutMillis = settings.dnsTimeoutMillis,
                        fallbackServers = routeSelection.networkDnsServers + fallbackDnsResolvers()
                    )
                ) {
                    is DnsForwardResult.Success -> {
                        recordDnsSuccess(query.ipVersion)
                        enqueueDecisionLog(
                            store,
                            settings,
                            Decision(query.hostname, true, decision.logReason())
                        )
                        result.payload
                    }
                    else -> {
                        recordDnsFailure(query.ipVersion)
                        DnsFailureResponse.servFail(query)
                    }
                }
            }
            writeResponse(output, outputLock, query, dnsPayload)
        } catch (error: Exception) {
            Log.e(TAG, "DNS request processing failed", error)
            writeResponse(output, outputLock, query, DnsFailureResponse.servFail(query))
        }
    }

    private fun enqueueDecisionLog(
        store: ProtectionStore,
        settings: ProtectionSettings,
        decision: Decision
    ) {
        if (!settings.loggingEnabled) return
        runCatching {
            decisionLogExecutor?.execute { store.appendDecision(decision) }
        }.onFailure { error ->
            if (error !is RejectedExecutionException) {
                Log.w(TAG, "Could not queue DNS decision log", error)
            }
        }
    }

    private fun writeResponse(
        output: FileOutputStream,
        outputLock: Any,
        query: DnsQueryPacket,
        dnsPayload: ByteArray
    ) {
        val responsePacket = DnsPacketCodec.buildResponsePacket(query, dnsPayload)
        synchronized(outputLock) {
            if (!running.get()) return
            try {
                output.write(responsePacket)
                output.flush()
            } catch (error: Exception) {
                if (running.get()) Log.e(TAG, "VPN response write failed", error)
            }
        }
    }

    private fun logUnsupportedPacketRateLimited(type: CapturedPacketType, lastLogMillis: AtomicLong) {
        val now = System.currentTimeMillis()
        val previous = lastLogMillis.get()
        if (now - previous >= UNSUPPORTED_PACKET_LOG_INTERVAL_MILLIS && lastLogMillis.compareAndSet(previous, now)) {
            Log.w(TAG, "Captured unsupported packet type $type; DNS-only VPN cannot forward this transport")
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
        requestExecutor?.shutdownNow()
        requestExecutor = null
        decisionLogExecutor?.shutdownNow()
        decisionLogExecutor = null
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
        private const val POLICY_REFRESH_MILLIS = 1_000L
        private const val UNSUPPORTED_PACKET_LOG_INTERVAL_MILLIS = 30_000L
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

        private fun recordDnsBlocked(ipVersion: Int) {
            val now = System.currentTimeMillis()
            mutableDnsHealth.value = mutableDnsHealth.value.update(ipVersion) { current ->
                current.copy(
                    processedRequests = current.processedRequests + 1,
                    blockedRequests = current.blockedRequests + 1,
                    lastSuccessMillis = now
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

    private fun recordDnsBlocked(ipVersion: Int) = Companion.recordDnsBlocked(ipVersion)
}
