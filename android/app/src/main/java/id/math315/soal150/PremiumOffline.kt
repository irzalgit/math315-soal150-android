package id.math315.soal150

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object PremiumOffline {

    private const val PREF = "math315_premium_offline"
    private const val KEY_UID = "uid"
    private const val KEY_UNTIL = "premium_until"
    private const val KEY_LICENSE = "license"

    private const val PUBLIC_KEY = """
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtYu59uGXvuIwknhgvEV4
BZ6YkPjCsPvRP6Yu0ExV2yEFF+Y4ZPsl8AflT1gPiV2EZXiKpXqg+65eMpOZGgHZ
OtA/MoF3ajHiawEcjcvETb9g4R8rB/4PJjeNOq1oXMyZXImz6bSzUZGQxxaeUDl5
z5Vdvhxl5cRNrBXqyVadqDMj6ARn9xaEeX00sBpTIeuYtjEujtQZReJjplTya6EB
XO90mBEsAAU4flZrI+XK++5Pcd+dynbM/GX8P1cjQkCOm8lAI/hzoQ57y4BWHusE
J19me0+WZN2Iyhi6hD/H+keSInceRDYCV1bf0PtSfo4JFubyK+8hwNEYovLjshPZ
DQIDAQAB
-----END PUBLIC KEY-----
"""

    private data class LicenseData(
        val uid: String,
        val expiresAt: Long
    )

    private fun verifyLicense(license: String): LicenseData? {
        return try {
            val parts = license.split(".")
            if (parts.size != 2) return null

            val encodedPayload = parts[0]
            val signatureBytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val keyBody = PUBLIC_KEY
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")

            val keyBytes = Base64.decode(keyBody, Base64.DEFAULT)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))

            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(encodedPayload.toByteArray(Charsets.UTF_8))

            if (!verifier.verify(signatureBytes)) return null

            val payloadBytes = Base64.decode(
                encodedPayload,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val payload = JSONObject(String(payloadBytes, Charsets.UTF_8))

            val version = payload.optInt("version", 0)
            val app = payload.optString("app", "")
            val uid = payload.optString("uid", "")
            val issuedAt = payload.optLong("issuedAt", 0L)
            val expiresAt = payload.optLong("expiresAt", 0L)
            val now = System.currentTimeMillis()

            if (version != 1) return null
            if (app != "math315-soal150") return null
            if (uid.isBlank()) return null
            if (issuedAt <= 0L || issuedAt > now + 5 * 60 * 1000L) return null
            if (expiresAt <= now) return null

            LicenseData(uid, expiresAt)
        } catch (_: Exception) {
            null
        }
    }

    fun save(
        context: Context,
        uid: String,
        until: Long,
        license: String
    ): Boolean {
        val data = verifyLicense(license) ?: return false

        if (data.uid != uid || data.expiresAt != until) return false

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UID, data.uid)
            .putLong(KEY_UNTIL, data.expiresAt)
            .putString(KEY_LICENSE, license)
            .apply()

        return true
    }

    fun isActive(context: Context, uid: String): Boolean {
        if (uid.isBlank()) return false

        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val license = prefs.getString(KEY_LICENSE, "") ?: ""
        val data = verifyLicense(license) ?: return false

        return data.uid == uid &&
            prefs.getString(KEY_UID, "") == uid &&
            prefs.getLong(KEY_UNTIL, 0L) == data.expiresAt
    }

    fun getUid(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_UID, "") ?: ""
    }

    fun getUntil(context: Context): Long {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(KEY_UNTIL, 0L)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
