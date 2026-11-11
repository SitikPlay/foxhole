package com.sitikplay.foxhole

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecretVault {

    private const val KEY_ALIAS = "foxhole_vault"
    private const val PREFS = "foxhole_secure"
    private const val PREFIX = "v1:"

    private fun securePrefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)

                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val ct = cipher.doFinal(plain)

        return PREFIX +
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    fun encryptString(value: String): String = encrypt(value.toByteArray(Charsets.UTF_8))

    fun decrypt(blob: String): ByteArray? {
        if (!isEncrypted(blob)) return null
        return try {
            val parts = blob.removePrefix(PREFIX).split(":", limit = 2)
            if (parts.size < 2 || parts[0].isEmpty()) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ct)
        } catch (e: Exception) {

            null
        }
    }

    fun decryptString(blob: String): String? =
        decrypt(blob)?.toString(Charsets.UTF_8)

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    @Synchronized
    fun putSecureString(ctx: Context, key: String, encryptedBlob: String) {
        securePrefs(ctx).edit().putString(key, encryptedBlob).apply()
    }

    fun getSecureString(ctx: Context, key: String): String? =
        securePrefs(ctx).getString(key, null)

    @Synchronized
    fun putSecureBoolean(ctx: Context, key: String, value: Boolean) {
        securePrefs(ctx).edit().putBoolean(key, value).apply()
    }

    fun getSecureBoolean(ctx: Context, key: String, def: Boolean): Boolean =
        securePrefs(ctx).getBoolean(key, def)

    fun removeSecure(ctx: Context, vararg keys: String) {
        val e = securePrefs(ctx).edit()
        keys.forEach { e.remove(it) }
        e.apply()
    }

    fun clearSecure(ctx: Context) {
        securePrefs(ctx).edit().clear().apply()
    }

    @Synchronized
    fun migrateValue(
        ctx: Context,
        legacyStore: String,
        legacyKey: String,
        targetKey: String
    ): Boolean {
        if (getSecureString(ctx, targetKey) != null) return true
        val legacyAll = ctx.getSharedPreferences(legacyStore, Context.MODE_PRIVATE)
        val raw = legacyAll.getString(legacyKey, null) ?: return false
        val value = if (isEncrypted(raw)) raw else encryptString(raw)
        putSecureString(ctx, targetKey, value)
        legacyAll.edit().remove(legacyKey).apply()
        return true
    }

    fun migrate(ctx: Context) {
        migrateValue(ctx, "foxhole", "token", "server_token")
        migrateValue(ctx, "rb_adb", "key", "adb_key")
        migrateValue(ctx, "rb_adb", "cert", "adb_cert")

        val legacy = ctx.getSharedPreferences("rb_adb", Context.MODE_PRIVATE)
        if (legacy.getBoolean("paired", false) &&
            !getSecureBoolean(ctx, "adb_paired", false)
        ) {
            putSecureBoolean(ctx, "adb_paired", true)
        }
    }
}
