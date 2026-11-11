package com.sitikplay.foxhole

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

object PkgSignature {

    fun hashOf(ctx: Context, pkgs: List<String>): String? {
        val pm = ctx.packageManager
        val certs = pkgs.firstNotNullOfOrNull { pkg ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                    info.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
                }
            } catch (e: Exception) {
                null
            }
        } ?: return null
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(certs[0].toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
