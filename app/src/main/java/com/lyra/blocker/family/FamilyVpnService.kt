// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker.family

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.lyra.blocker.MainActivity
import com.lyra.blocker.ProtectionStore
import com.lyra.blocker.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** Local, split-route UDP DNS filtering. Web traffic is not sent to an M57 server. */
class FamilyVpnService : VpnService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var store: FamilyStore
    private lateinit var protection: ProtectionStore
    private lateinit var repository: FamilyRepository
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = Executors.newFixedThreadPool(2)
    private val slots = Semaphore(64)
    private val pending = ConcurrentHashMap.newKeySet<CancellationSignal>()
    private val writeLock = Any()
    @Volatile private var underlying: Network? = null
    @Volatile private var tunnel: ParcelFileDescriptor? = null
    @Volatile private var output: FileOutputStream? = null
    // Network callbacks run off the service thread. Keep a snapshot instead of
    // switching to whichever Wi-Fi/mobile network happened to report last.
    private val networks = ConcurrentHashMap<Network, NetworkCapabilities>()
    private var networkRegistered = false
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            networks[network] = capabilities
            handler.post { chooseNetwork() }
        }
        override fun onLost(network: Network) {
            networks.remove(network)
            handler.post { chooseNetwork() }
        }
    }
    private val refresh = Runnable { updateNotification() }

    override fun onCreate() {
        super.onCreate()
        store = FamilyStore(this); protection = ProtectionStore(this); repository = FamilyRepository.get(this)
        store.prefs.registerOnSharedPreferenceChangeListener(this)
        protection.prefs.registerOnSharedPreferenceChangeListener(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Family DNS protection", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (!store.vpnRequested || VpnService.prepare(this) != null || repository.index.size() == 0) { stopSelf(); return START_NOT_STICKY }
            // Configured VPNs qualify for systemExempted; recheck consent before foreground startup.
            startForeground(NOTIFICATION, notification("Starting local DNS protection"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            if (tunnel == null) {
                val descriptor = Builder().setSession("M57 family DNS")
                    .addDisallowedApplication(packageName)
                    .addAddress("10.57.0.1", 32).addRoute("10.57.0.2", 32).addDnsServer("10.57.0.2")
                    .addAddress("fd57:57::1", 128).addRoute("fd57:57::2", 128).addDnsServer("fd57:57::2")
                    .setMtu(1500).setBlocking(true)
                    .setConfigureIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    .establish() ?: throw IllegalStateException("VPN approval unavailable")
                tunnel = descriptor
                output = FileOutputStream(descriptor.fileDescriptor)
                chooseNetwork()
                connectivity.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), networkCallback)
                networkRegistered = true
                running = true
                Thread({ readPackets(descriptor) }, "M57-family-DNS").start()
            }
            updateNotification()
            return START_STICKY
        } catch (_: Exception) {
            store.vpnMessage("VPN could not start. Review Android VPN approval and try again.")
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun chooseNetwork() {
        if (tunnel == null) return
        val active = connectivity.activeNetwork
        val candidates = (listOfNotNull(active) + networks.keys).distinct().mapNotNull { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            network to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        val selected = candidates.firstOrNull { it.second }?.first ?: candidates.firstOrNull()?.first
        if (underlying == selected) return
        underlying = selected
        runCatching { setUnderlyingNetworks(selected?.let { arrayOf(it) }) }
        updateNotification()
    }

    private fun readPackets(descriptor: ParcelFileDescriptor) {
        try {
            val input = FileInputStream(descriptor.fileDescriptor)
            val buffer = ByteArray(65_535)
            while (tunnel === descriptor) {
                val length = input.read(buffer)
                if (length < 0) break
                val packet = DnsPacket.parse(buffer, length) ?: continue
                if (!protection.paused && repository.index.match(packet.host) != null) {
                    send(descriptor, packet.denied()); continue
                }
                resolve(descriptor, packet)
            }
            if (tunnel === descriptor) handler.post { stopSelf() }
        } catch (_: Exception) {
            if (tunnel === descriptor) {
                store.vpnMessage("DNS filter stopped unexpectedly; restart from Family browsing")
                handler.post { stopSelf() }
            }
        }
    }

    // rawQuery documents bitwise flag combinations, but SDK 35's IntDef lacks flag=true.
    // https://developer.android.com/reference/android/net/DnsResolver#rawQuery(android.net.Network,byte[],int,java.util.concurrent.Executor,android.os.CancellationSignal,android.net.DnsResolver.Callback)
    @SuppressLint("WrongConstant")
    private fun resolve(descriptor: ParcelFileDescriptor, packet: DnsPacket) {
        val network = underlying
        if (network == null || !slots.tryAcquire()) { send(descriptor, packet.failed()); return }
        val cancellation = CancellationSignal()
        val completed = AtomicBoolean(false)
        pending.add(cancellation)
        fun finish(answer: ByteArray) {
            if (!completed.compareAndSet(false, true)) return
            pending.remove(cancellation); slots.release()
            send(descriptor, answer)
        }
        val timeout = Runnable {
            finish(packet.failed())
            cancellation.cancel()
        }
        handler.postDelayed(timeout, 6_000)
        try {
            DnsResolver.getInstance().rawQuery(network, packet.query,
                DnsResolver.FLAG_NO_CACHE_LOOKUP or DnsResolver.FLAG_NO_CACHE_STORE, callbacks, cancellation,
                object : DnsResolver.Callback<ByteArray> {
                    override fun onAnswer(answer: ByteArray, rcode: Int) {
                        handler.removeCallbacks(timeout); finish(packet.answer(answer))
                    }
                    override fun onError(error: DnsResolver.DnsException) {
                        handler.removeCallbacks(timeout); finish(packet.failed())
                    }
                })
        } catch (_: Exception) { handler.removeCallbacks(timeout); finish(packet.failed()) }
    }

    private fun send(descriptor: ParcelFileDescriptor, bytes: ByteArray) {
        synchronized(writeLock) {
            if (tunnel !== descriptor) return
            runCatching { output?.write(bytes) }
        }
    }
    private fun notification(text: String): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_family_shield).setContentTitle("M57 family protection").setContentText(text).setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()

    private fun updateNotification() {
        if (!running) return
        val message = when {
            protection.paused -> "Paused by the administrator · DNS passes through"
            underlying == null -> "Waiting for a network · DNS requests return unavailable"
            !repository.ready -> "Local DNS active · some enabled lists are still missing"
            else -> "Local DNS active · ${repository.index.size()} domains · browser protection is separate"
        }
        store.vpnMessage(message)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message))
        handler.removeCallbacks(refresh)
        if (protection.paused && !protection.pauseIndefinite) handler.postDelayed(refresh, protection.pauseRemaining.coerceAtMost(86_400_000) + 100)
    }
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == "vpn_message") return
        if (!store.vpnRequested) stopSelf() else updateNotification()
    }
    override fun onRevoke() {
        store.vpnRequested = false
        store.vpnMessage("Android VPN access was revoked or another VPN replaced M57")
        stopSelf()
    }
    override fun onDestroy() {
        running = false
        if (store.vpnMessage().startsWith("Local DNS") || store.vpnMessage().startsWith("Paused by") || store.vpnMessage().startsWith("Waiting for a network"))
            store.vpnMessage(if (store.vpnRequested) "VPN stopped; retry from Family browsing" else "Stopped by administrator")
        val descriptor = tunnel; tunnel = null
        runCatching { descriptor?.close() }
        pending.forEach { it.cancel() }; pending.clear()
        callbacks.shutdown()
        handler.removeCallbacksAndMessages(null)
        if (networkRegistered) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        networks.clear()
        store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        protection.prefs.unregisterOnSharedPreferenceChangeListener(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        @Volatile var running = false; private set
        private const val CHANNEL = "family-dns"
        private const val NOTIFICATION = 5700
    }
}
