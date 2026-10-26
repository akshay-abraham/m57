// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker.family

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

class FamilyRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val store = FamilyStore(app)
    private val directory = File(app.filesDir, "family-lists").apply { mkdirs() }
    private val work = Executors.newSingleThreadExecutor()
    private val syncing = AtomicInteger(0)
    @Volatile var index = HostRules.Index(emptyMap(), emptySet(), emptySet()); private set
    @Volatile var ready = false; private set
    @Volatile var loading = true; private set
    val busy get() = syncing.get() > 0

    init { reload() }
    private fun file(category: FamilyCategory) = AtomicFile(File(directory, "${category.key}.domains"))
    fun sample(category: FamilyCategory, finished: (List<String>) -> Unit) {
        work.execute {
            val names = runCatching { file(category).openRead().bufferedReader().useLines { it.take(12).toList() } }.getOrDefault(emptyList())
            finished(names)
        }
    }

    fun reload(after: (() -> Unit)? = null) { work.execute { loadSnapshot(); after?.invoke() } }
    private fun loadSnapshot() {
        loading = true
        val categories = linkedMapOf<String, Set<String>>()
        var complete = true
        for (category in FamilyCategory.values().filter { it.key in store.enabled }) {
            try {
                val domains = file(category).openRead().bufferedReader().useLines { lines -> lines.mapNotNull(HostRules::domain).toSet() }
                if (domains.isEmpty()) complete = false else categories[category.key] = domains
            } catch (_: IOException) { complete = false }
        }
        index = HostRules.Index(categories, store.allow, store.block)
        ready = complete && (categories.isNotEmpty() || store.block.isNotEmpty())
        loading = false
        store.prefs.edit().putLong("revision", android.os.SystemClock.elapsedRealtimeNanos()).apply()
    }

    fun sync(force: Boolean, cancelled: () -> Boolean = { false }, finished: () -> Unit = {}) {
        syncing.incrementAndGet()
        work.execute {
            try {
                for (category in FamilyCategory.values().filter { it.key in store.enabled }) {
                    if (cancelled()) return@execute
                    if (!force && System.currentTimeMillis() - store.lastSync(category) < 86_400_000 && file(category).baseFile.exists()) continue
                    var success = false
                    for (source in category.urls) {
                        if (cancelled()) return@execute
                        try {
                            val data = download(source)
                            val domains = HostRules.validatedDownload(data, store.count(category))
                            val atomic = file(category)
                            val output = atomic.startWrite()
                            try {
                                val bytes = (domains.sorted().joinToString("\n") + "\n").toByteArray(StandardCharsets.UTF_8)
                                output.write(bytes); atomic.finishWrite(output)
                            } catch (error: Exception) { atomic.failWrite(output); throw error }
                            store.prefs.edit().putLong("sync_${category.key}", System.currentTimeMillis())
                                .putInt("count_${category.key}", domains.size).putString("source_${category.key}", source)
                                .remove("error_${category.key}").apply()
                            success = true
                            break
                        } catch (_: Exception) { /* Try the next HTTPS source; never erase the last good file. */ }
                    }
                    if (!success) store.prefs.edit().putString("error_${category.key}", "All HTTPS sources failed validation or download. Keeping the last working list.").apply()
                }
                loadSnapshot()
            } finally {
                syncing.decrementAndGet()
                finished()
            }
        }
    }

    private fun download(address: String): String {
        var current = URL(address)
        val deadline = android.os.SystemClock.elapsedRealtime() + 45_000
        repeat(5) {
            if (current.protocol != "https") throw IOException("HTTPS required")
            val connection = current.openConnection() as HttpsURLConnection
            connection.connectTimeout = 8_000; connection.readTimeout = 12_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "text/plain")
            connection.setRequestProperty("User-Agent", "M57-Hosts/0.5")
            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val target = URL(current, connection.getHeaderField("Location") ?: throw IOException("Missing redirect"))
                    if (target.protocol != "https" || target.host !in setOf("raw.githubusercontent.com", "cdn.jsdelivr.net", "sbc.io", "github.com")) throw IOException("Untrusted redirect")
                    current = target
                } else {
                    if (status != 200 || connection.contentType.orEmpty().contains("html", true) || connection.contentLengthLong > 16_000_000) throw IOException("Invalid response")
                    val bytes = ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(16_384)
                        while (true) {
                            if (android.os.SystemClock.elapsedRealtime() > deadline) throw IOException("Download deadline exceeded")
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (bytes.size() + count > 16_000_000) throw IOException("List exceeds size limit")
                            bytes.write(buffer, 0, count)
                        }
                    }
                    return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString()
                }
            } finally { connection.disconnect() }
        }
        throw IOException("Too many redirects")
    }

    companion object {
        @Volatile private var instance: FamilyRepository? = null
        fun get(context: Context): FamilyRepository = instance ?: synchronized(this) {
            instance ?: FamilyRepository(context).also { instance = it }
        }
    }
}
