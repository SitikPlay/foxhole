package com.sitikplay.foxhole

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SysSettings

object OemCompat {

    enum class RomFamily(val label: String) {
        HYPEROS("Xiaomi HyperOS"),
        MIUI("Xiaomi MIUI"),
        ONEUI("Samsung One UI"),
        COLOROS("ColorOS / Realme UI / OxygenOS"),
        MAGIC("Honor MagicOS / Huawei EMUI"),
        FUNTOUCH("Vivo Funtouch / OriginOS"),
        NOTHING("Nothing OS"),
        AOSP("Android / Pixel / AOSP")
    }

    private val CUSTOM_AOSP_MARKERS = listOf(
        "lineage", "crdroid", "pixelos", "pixelexperience", "evolution", "derpfest",
        "paranoid", "calyx", "graphene", "matrixx", "alpha", "rising", "clover", "custom_"
    )

    private val AOSP_BUILD_PREFIX = Regex("^[abc]p\\d[a-z]\\.")

    fun classifyRom(
        manufacturer: String,
        brand: String,
        display: String,
        miuiVersion: String?,
        hyperOsVersion: String?,
        sepVersion: String?,
        oplusVersion: String? = null,
        emuiVersion: String? = null,
        vivoVersion: String? = null,
        nothingVersion: String? = null
    ): RomFamily {
        val man = manufacturer.trim().lowercase()
        val br = brand.trim().lowercase()
        val disp = display.trim().lowercase()
        val isCustomAosp = CUSTOM_AOSP_MARKERS.any { disp.contains(it) } ||
            AOSP_BUILD_PREFIX.containsMatchIn(disp)

        val xiaomiHardware = man == "xiaomi" || br in setOf("xiaomi", "redmi", "poco")
        val hasHyper = !hyperOsVersion.isNullOrBlank() ||
            disp.contains("hyperos") ||
            (xiaomiHardware && (disp.startsWith("os1.") || disp.startsWith("os2.") || disp.startsWith("os3.")))

        if (hasHyper) return RomFamily.HYPEROS
        if (!miuiVersion.isNullOrBlank() || (xiaomiHardware && disp.contains("miui"))) return RomFamily.MIUI
        if (!sepVersion.isNullOrBlank()) return RomFamily.ONEUI
        if (!oplusVersion.isNullOrBlank() ||
            (!isCustomAosp && (man in setOf("oppo", "realme", "oneplus") || br in setOf("oppo", "realme", "oneplus")))
        ) {
            return RomFamily.COLOROS
        }
        if (!vivoVersion.isNullOrBlank() ||
            (!isCustomAosp && (man in setOf("vivo", "iqoo") || br in setOf("vivo", "iqoo")))
        ) {
            return RomFamily.FUNTOUCH
        }
        if (!nothingVersion.isNullOrBlank() ||
            (!isCustomAosp && (man == "nothing" || br == "nothing"))
        ) {
            return RomFamily.NOTHING
        }
        if (!emuiVersion.isNullOrBlank() ||
            (!isCustomAosp && (man in setOf("huawei", "honor") || br in setOf("huawei", "honor")))
        ) {
            return RomFamily.MAGIC
        }
        return RomFamily.AOSP
    }

    fun detectRom(): RomFamily = classifyRom(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        display = "${Build.DISPLAY.orEmpty()} ${Build.PRODUCT.orEmpty()}",
        miuiVersion = sysProp("ro.miui.ui.version.name"),
        hyperOsVersion = sysProp("ro.mi.os.version.name"),
        sepVersion = sysProp("ro.build.version.sep"),
        oplusVersion = sysProp("ro.build.version.oplusrom") ?: sysProp("ro.build.version.realmeui"),
        emuiVersion = sysProp("ro.build.version.emui") ?: sysProp("ro.build.version.magic"),
        vivoVersion = sysProp("ro.vivo.os.version") ?: sysProp("ro.vivo.product.version"),
        nothingVersion = sysProp("ro.nothing.version.id")
    )

    private fun sysProp(key: String): String? = try {
        val cls = Class.forName("android.os.SystemProperties")
        val get = cls.getMethod("get", String::class.java, String::class.java)
        (get.invoke(null, key, "") as? String)?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(ctx.packageName) }.getOrDefault(false)
    }

    fun isWifiDebuggingOn(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            SysSettings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun hasWriteSecureSettings(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun requestIgnoreBatteryOptimizationsIntent(ctx: Context): Intent {
        val direct = Intent(
            SysSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}")
        )
        return if (ctx.packageManager.resolveActivity(direct, 0) != null) {
            direct
        } else {
            Intent(SysSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    private fun autostartIntents(): List<Intent> = listOf(
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        ),
        Intent().setComponent(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
        ),
        Intent().setComponent(
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity")
        ),
        Intent().setComponent(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        ),
        Intent().setComponent(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        ),
        Intent().setComponent(
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
        )
    )

    fun openOemAutostart(ctx: Context): Boolean {
        for (intent in autostartIntents()) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (ctx.packageManager.resolveActivity(intent, 0) != null) {
                    ctx.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun hasAutostartActivity(ctx: Context): Boolean =
        autostartIntents().any { runCatching { ctx.packageManager.resolveActivity(it, 0) != null }.getOrDefault(false) }

    fun adviceRes(rom: RomFamily): Int = when (rom) {
        RomFamily.HYPEROS, RomFamily.MIUI -> R.string.t483
        RomFamily.ONEUI -> R.string.t484
        RomFamily.FUNTOUCH -> R.string.t571
        RomFamily.NOTHING -> R.string.t572
        RomFamily.COLOROS, RomFamily.MAGIC, RomFamily.AOSP -> R.string.t485
    }
}
