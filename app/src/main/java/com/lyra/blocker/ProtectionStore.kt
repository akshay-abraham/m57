// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

class ProtectionStore(context: Context) {
    private val context = context.applicationContext
    val prefs = context.getSharedPreferences("lyra_protection", Context.MODE_PRIVATE)
    var aiEnabled: Boolean
        get() = prefs.getBoolean("ai_enabled", true)
        set(value) { prefs.edit().putBoolean("ai_enabled", value).apply() }
    var disabledApps: Set<String>
        get() = prefs.getStringSet("disabled_apps", emptySet())!!.toSet()
        set(value) { prefs.edit().putStringSet("disabled_apps", value.toSet()).apply() }
    var disabledDomains: Set<String>
        get() = prefs.getStringSet("disabled_ai_domains", emptySet())!!.toSet()
        set(value) { prefs.edit().putStringSet("disabled_ai_domains", value.toSet()).apply() }
    var extraAiDomains: Set<String>
        get() = prefs.getStringSet("extra_ai_domains", emptySet())!!.toSet()
        set(value) { prefs.edit().putStringSet("extra_ai_domains", value.toSet()).apply() }
    var metaEnabled: Boolean
        get() = prefs.getBoolean("meta_enabled", true)
        set(value) { prefs.edit().putBoolean("meta_enabled", value).apply() }
    var metaServices: Set<String>
        get() = prefs.getStringSet("meta_services", setOf("whatsapp", "instagram", "facebook", "messenger", "meta"))!!.toSet()
        set(value) { prefs.edit().putStringSet("meta_services", value.toSet()).apply() }
    var youtubeEnabled: Boolean
        get() = prefs.getBoolean("youtube_enabled", true)
        set(value) { prefs.edit().putBoolean("youtube_enabled", value).apply() }
    var instagramEnabled: Boolean
        get() = prefs.getBoolean("instagram_enabled", true)
        set(value) { prefs.edit().putBoolean("instagram_enabled", value).apply() }
    var extraVideosEnabled: Boolean
        get() = prefs.getBoolean("extra_videos_enabled", false)
        set(value) { prefs.edit().putBoolean("extra_videos_enabled", value).apply() }
    var extraVideoApps: Set<String>
        get() = prefs.getStringSet("extra_video_apps", setOf("snapchat", "facebook", "tiktok", "josh"))!!.toSet()
        set(value) { prefs.edit().putStringSet("extra_video_apps", value.toSet()).apply() }
    var videoMode: String
        get() = prefs.getString("video_mode", "block")!!.let { if (it == "remind") it else "block" }
        set(value) { require(value in setOf("block", "remind")); prefs.edit().putString("video_mode", value).apply() }
    var repoGuard: Boolean
        get() = prefs.getBoolean("repo_guard", true)
        set(value) { prefs.edit().putBoolean("repo_guard", value).apply() }
    var destination: String
        get() = prefs.getString("destination", "https://lyra-edu.vercel.app/chat")!!
        set(value) { require(BlockPolicy.validDestination(value)); prefs.edit().putString("destination", value).apply() }
    var pwaPackage: String
        get() = prefs.getString("pwa_package", "")!!
        set(value) { require(value.isEmpty() || BlockPolicy.validPackage(value)); prefs.edit().putString("pwa_package", value).apply() }
    var countdown: Int
        get() = prefs.getInt("countdown", 5).coerceIn(3, 15)
        set(value) { prefs.edit().putInt("countdown", value.coerceIn(3, 15)).apply() }
    var extraPackages: Set<String>
        get() = prefs.getStringSet("extra_packages", emptySet())!!.toSet()
        set(value) { require(value.all(BlockPolicy::validPackage)); prefs.edit().putStringSet("extra_packages", value.toSet()).apply() }
    val hasPassword: Boolean get() = prefs.contains("password_hash")
    val hasCredential: Boolean get() = hasPassword || prefs.contains("pin_hash")
    val needsPasswordUpgrade: Boolean get() = !hasPassword && prefs.contains("pin_hash")
    val consented: Boolean get() = prefs.getBoolean("consent", false)
    fun consent() { prefs.edit().putBoolean("consent", true).commit() }

    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    val pauseIndefinite: Boolean get() = prefs.getString("protection_pause_mode", "") == "always"
    val pauseRemaining: Long get() {
        if (pauseIndefinite) return Long.MAX_VALUE
        if (prefs.getString("protection_pause_mode", "") == "until") return ProtectionTiming.remaining(
            prefs.getLong("protection_pause_wall", 0), prefs.getLong("protection_pause_elapsed", 0),
            prefs.getInt("protection_pause_boot", -2), bootCount(), System.currentTimeMillis(), SystemClock.elapsedRealtime())
        return if (prefs.getInt("pause_boot", -2) == bootCount())
            (prefs.getLong("pause_until", 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0) else 0
    }
    val paused: Boolean get() = pauseRemaining > 0
    val pauseEndsAt: Long get() = if (paused && !pauseIndefinite) System.currentTimeMillis() + pauseRemaining else 0
    fun pauseFor(minutes: Int) {
        require(minutes in setOf(15, 60, 180))
        pauseUntil(System.currentTimeMillis() + minutes * 60_000L)
    }
    fun pauseUntil(deadline: Long) {
        val duration = deadline - System.currentTimeMillis()
        require(duration > 0)
        prefs.edit().putString("protection_pause_mode", "until")
            .putLong("protection_pause_wall", deadline)
            .putLong("protection_pause_elapsed", SystemClock.elapsedRealtime() + duration)
            .putInt("protection_pause_boot", bootCount()).remove("pause_until").apply()
    }
    fun pauseAlways() { prefs.edit().putString("protection_pause_mode", "always").remove("pause_until").apply() }
    fun resume() { prefs.edit().remove("protection_pause_mode").remove("pause_until").apply() }

    val appInfoAccessRemaining: Long get() = if (prefs.getInt("app_info_boot", -2) == bootCount())
        (prefs.getLong("app_info_until", 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0) else 0
    fun allowAppInfoAccess() {
        prefs.edit().putInt("app_info_boot", bootCount())
            .putLong("app_info_until", SystemClock.elapsedRealtime() + 60_000).apply()
    }

    /** Called on the UI thread only after the authenticated operation is still current. */
    fun savePassword(hash: PasswordCredential.Hash) {
        check(prefs.edit().putString("password_salt", hash.salt).putString("password_hash", hash.digest)
            .putInt("password_rounds", PasswordCredential.ITERATIONS)
            .remove("pin_hash").remove("pin_salt").remove("failures").remove("retry_after").remove("retry_wall").commit()) {
            "Password could not be saved"
        }
    }
    val retrySeconds: Long get() = (ProtectionTiming.remaining(prefs.getLong("retry_wall", 0),
        prefs.getLong("retry_after", 0), prefs.getInt("retry_boot", -2), bootCount(),
        System.currentTimeMillis(), SystemClock.elapsedRealtime()) + 999) / 1000
    fun verifyCredential(value: String): Boolean {
        if (!hasCredential || retrySeconds > 0) return false
        val prefix = if (hasPassword) "password" else "pin"
        val correct = PasswordCredential.verify(value, prefs.getString("${prefix}_salt", null),
            prefs.getString("${prefix}_hash", null), if (hasPassword) prefs.getInt("password_rounds", 0) else 120_000)
        if (correct) prefs.edit().remove("failures").remove("retry_after").remove("retry_wall").commit()
        else {
            val failures = prefs.getInt("failures", 0).coerceIn(0, 1000) + 1
            val delay = ProtectionTiming.retryDelayMillis(failures)
            prefs.edit().putInt("failures", failures)
                .putInt("retry_boot", bootCount())
                .putLong("retry_after", if (delay > 0) SystemClock.elapsedRealtime() + delay else 0)
                .putLong("retry_wall", if (delay > 0) System.currentTimeMillis() + delay else 0).commit()
        }
        return correct
    }
}
