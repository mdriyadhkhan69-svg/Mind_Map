package com.example.mindmap

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class FocusAppConfig(
    val packageName: String,
    val appName: String,
    val durationMinutes: Int,
    val priority: String,
    val isActive: Boolean = false,
    val startAt: Long = 0L,
    val expiresAt: Long = 0L
)

object FocusNavigationState {
    val requestOpen = mutableStateOf(false)
}

object FocusStore {
    const val PRIORITY_LOW = "LOW"
    const val PRIORITY_HIGH = "HIGH"
    private const val PREFS = "focus_prefs"
    private const val ITERATIONS = 12000

    var configs by mutableStateOf<List<FocusAppConfig>>(emptyList())
        private set
    private var loaded = false

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        configs = runCatching { parse(prefs(ctx).getString("configs", "[]") ?: "[]") }
            .getOrDefault(emptyList())
        loaded = true
    }

    private fun parse(raw: String): List<FocusAppConfig> {
        val arr = JSONArray(raw)
        return buildList {
            repeat(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                add(
                    FocusAppConfig(
                        packageName = o.getString("pkg"),
                        appName = o.optString("name"),
                        durationMinutes = o.optInt("minutes"),
                        priority = o.optString("priority"),
                        isActive = o.optBoolean("active", false),
                        startAt = o.optLong("start", 0L),
                        expiresAt = o.optLong("expires", 0L)
                    )
                )
            }
        }
    }

    private fun persist(ctx: Context, list: List<FocusAppConfig>) {
        configs = list
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("pkg", it.packageName)
                    .put("name", it.appName)
                    .put("minutes", it.durationMinutes)
                    .put("priority", it.priority)
                    .put("active", it.isActive)
                    .put("start", it.startAt)
                    .put("expires", it.expiresAt)
            )
        }
        prefs(ctx).edit().putString("configs", arr.toString()).apply()
    }

    fun saveConfigured(ctx: Context, cfg: FocusAppConfig) {
        ensureLoaded(ctx)
        val without = configs.filterNot { it.packageName == cfg.packageName }
        persist(ctx, without + cfg.copy(isActive = false, startAt = 0L, expiresAt = 0L))
    }

    fun activate(ctx: Context, pkg: String) {
        ensureLoaded(ctx)
        val now = System.currentTimeMillis()
        persist(ctx, configs.map {
            if (it.packageName == pkg) it.copy(
                isActive = true,
                startAt = now,
                expiresAt = now + it.durationMinutes * 60_000L
            ) else it
        })
    }

    fun deactivate(ctx: Context, pkg: String) {
        ensureLoaded(ctx)
        persist(ctx, configs.map {
            if (it.packageName == pkg) it.copy(isActive = false, startAt = 0L, expiresAt = 0L) else it
        })
    }

    fun refreshExpired(ctx: Context) {
        ensureLoaded(ctx)
        val now = System.currentTimeMillis()
        if (configs.any { it.isActive && it.expiresAt <= now }) {
            persist(ctx, configs.map {
                if (it.isActive && it.expiresAt <= now) it.copy(isActive = false, startAt = 0L, expiresAt = 0L) else it
            })
        }
    }

    fun activeBlock(ctx: Context, pkg: String): FocusAppConfig? {
        ensureLoaded(ctx)
        refreshExpired(ctx)
        return configs.firstOrNull { it.packageName == pkg && it.isActive && it.expiresAt > System.currentTimeMillis() }
    }

    fun formatRemaining(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    }

    /* ---------- password (salted PBKDF2, never plaintext) ---------- */

    private fun hash(pw: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pw.toCharArray(), salt, ITERATIONS, 256)).encoded

    fun hasPassword(ctx: Context): Boolean = prefs(ctx).contains("pw_hash")

    fun setPassword(ctx: Context, pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(ctx).edit()
            .putString("pw_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pw_hash", Base64.encodeToString(hash(pw, salt), Base64.NO_WRAP))
            .apply()
    }

    fun verifyPassword(ctx: Context, pw: String): Boolean {
        val p = prefs(ctx)
        val salt = p.getString("pw_salt", null) ?: return false
        val stored = p.getString("pw_hash", null) ?: return false
        return MessageDigest.isEqual(
            Base64.decode(stored, Base64.NO_WRAP),
            hash(pw, Base64.decode(salt, Base64.NO_WRAP))
        )
    }

    /* ---------- accessibility service state ---------- */

    fun isServiceEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(ctx, FocusBlockerService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }
}