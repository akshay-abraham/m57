// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class ProtectionStore(context: Context) {
    private val context = context.applicationContext
    val prefs = context.getSharedPreferences("lyra_protection", Context.MODE_PRIVATE)
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
    val hasPin: Boolean get() = prefs.contains("pin_hash")
    val consented: Boolean get() = prefs.getBoolean("consent", false)
    fun consent() { prefs.edit().putBoolean("consent", true).commit() }

    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    val pauseRemaining: Long get() = if (prefs.getInt("pause_boot", -2) == bootCount())
        (prefs.getLong("pause_until", 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0) else 0
    val paused: Boolean get() = pauseRemaining > 0
    fun pause() { prefs.edit().putInt("pause_boot", bootCount()).putLong("pause_until", SystemClock.elapsedRealtime() + 300_000).apply() }
    fun resume() { prefs.edit().remove("pause_until").apply() }

    val appInfoAccessRemaining: Long get() = if (prefs.getInt("app_info_boot", -2) == bootCount())
        (prefs.getLong("app_info_until", 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0) else 0
    fun allowAppInfoAccess() {
        prefs.edit().putInt("app_info_boot", bootCount())
            .putLong("app_info_until", SystemClock.elapsedRealtime() + 60_000).apply()
    }

    fun setPin(pin: String) {
        require(pin.matches(Regex("[0-9]{6,12}")))
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("pin_salt", hex(salt)).putString("pin_hash", hex(derive(pin, salt)))
            .remove("failures").remove("retry_after").commit()
    }
    val retrySeconds: Long get() = if (prefs.getInt("retry_boot", -2) == bootCount())
        ((prefs.getLong("retry_after", 0) - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0) else 0
    fun verifyPin(pin: String): Boolean {
        if (!hasPin || retrySeconds > 0) return false
        val salt = unhex(prefs.getString("pin_salt", "")!!)
        val correct = MessageDigest.isEqual(derive(pin, salt), unhex(prefs.getString("pin_hash", "")!!))
        if (correct) prefs.edit().remove("failures").remove("retry_after").commit()
        else {
            val failures = prefs.getInt("failures", 0) + 1
            prefs.edit().putInt("failures", failures)
                .putInt("retry_boot", bootCount())
                .putLong("retry_after", if (failures >= 5) SystemClock.elapsedRealtime() + 30_000 else 0).commit()
        }
        return correct
    }
    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun unhex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
