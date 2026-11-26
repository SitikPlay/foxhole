package com.sitikplay.foxhole

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings as SysSettings

/**
 * Audits Foxhole security posture, system debugging/privacy settings, third-party
 * Accessibility/Notification listeners, and AppOps clipboard/installer grants, and
 * provides one-tap remediation plus Panic Lockdown.
 */
object SecurityShield {

    enum class RiskKind { CAMERA, MIC, GPS, SMS }

    data class RiskyApp(
        val pkg: String,
        val label: String,
        val risks: List<RiskKind>
    )

    data class Report(
        val serverOnline: Boolean,
        val confirmDeviceOn: Boolean,
        val flagSecureOn: Boolean,
        val allowedTotal: Int,
        val allowedFull: Int,
        val usbDebugOn: Boolean,
        val wifiDebugOn: Boolean,
        val adbVerifierOn: Boolean,
        val privateDnsProtected: Boolean,
        val thirdPartyAccessibility: List<String>,
        val thirdPartyNotifListeners: List<String>,
        val thirdPartyClipboardPkgs: List<String>,
        val thirdPartyInstallerPkgs: List<String>,
        val riskyHardwareApps: List<RiskyApp>,
        val score: Int
    )

    private val TRACKED_DANGEROUS_PERMS = listOf(
        "android.permission.CAMERA" to RiskKind.CAMERA,
        "android.permission.RECORD_AUDIO" to RiskKind.MIC,
        "android.permission.ACCESS_FINE_LOCATION" to RiskKind.GPS,
        "android.permission.READ_SMS" to RiskKind.SMS
    )

    fun parseComponentPackageList(raw: String): List<String> {
        val cleaned = if (raw.startsWith("OK")) raw.removePrefix("OK").trim() else raw.trim()
        if (cleaned.isEmpty() || cleaned.equals("null", ignoreCase = true)) return emptyList()
        return cleaned.split(':')
            .asSequence()
            .map { it.trim().substringBefore('/') }
            .filter { AppInspector.isValidPackage(it) }
            .distinct()
            .toList()
    }

    fun parseQueryOpPackages(raw: String): List<String> {
        if (!raw.startsWith("OK")) return emptyList()
        val body = raw.removePrefix("OK").trim()
        return body.lineSequence()
            .map { it.trim() }
            .filter { AppInspector.isValidPackage(it) }
            .distinct()
            .toList()
    }

    fun computeScore(
        confirmDeviceOn: Boolean,
        flagSecureOn: Boolean,
        allowedFullCount: Int,
        usbDebugOn: Boolean,
        wifiDebugOn: Boolean,
        adbVerifierOn: Boolean,
        privateDnsProtected: Boolean,
        a11yCount: Int,
        notifListenerCount: Int,
        clipboardCount: Int,
        installerCount: Int
    ): Int {
        var score = 100
        if (!confirmDeviceOn) score -= 20
        if (!flagSecureOn) score -= 10
        if (allowedFullCount > 0) score -= minOf(15, allowedFullCount * 5)
        if (usbDebugOn) score -= 5
        if (wifiDebugOn) score -= 10
        if (!adbVerifierOn) score -= 8
        if (!privateDnsProtected) score -= 7
        if (a11yCount > 0) score -= minOf(15, a11yCount * 7)
        if (notifListenerCount > 0) score -= minOf(10, notifListenerCount * 5)
        if (clipboardCount > 2) score -= minOf(10, (clipboardCount - 2) * 3)
        if (installerCount > 2) score -= minOf(10, (installerCount - 2) * 3)
        return score.coerceIn(0, 100)
    }

    fun scan(ctx: Context): Report {
        val pm = ctx.packageManager
        val installed = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }.getOrDefault(emptyList())

        val systemPkgs = HashSet<String>()
        val labelByPkg = HashMap<String, String>()
        val riskyApps = ArrayList<RiskyApp>()
        for (pi in installed) {
            val ai = pi.applicationInfo ?: continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) systemPkgs.add(ai.packageName)
            if (!isSystem && ai.packageName != ctx.packageName) {
                val appLabel = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName)
                labelByPkg[ai.packageName] = appLabel
                val risks = collectHardwareRisks(pi)
                if (risks.isNotEmpty()) riskyApps.add(RiskyApp(ai.packageName, appLabel, risks))
            }
        }
        riskyApps.sortWith(compareByDescending<RiskyApp> { it.risks.size }.thenBy { it.label.lowercase() })

        val confirmOn = Settings.confirmDevice(ctx) && BiometricGate.hasDeviceLock(ctx)
        val secureOn = Settings.flagSecureScreens(ctx)
        val allowed = AccessStore.allowed(ctx)
        val fullCount = allowed.count { it.level == AclLevel.FULL }

        val serverOnline = runCatching { ServerClient.request(ctx, "ping").startsWith("OK pong") }
            .getOrDefault(false)

        val usbDebugOn = readIntSetting(ctx, "global", "adb_enabled", serverOnline) == 1
        val wifiDebugOn = readIntSetting(ctx, "global", "adb_wifi_enabled", serverOnline) == 1
        val adbVerifierOn = readIntSetting(ctx, "global", "verifier_verify_adb_installs", serverOnline) != 0
        val dnsMode = readStrSetting(ctx, "global", "private_dns_mode", serverOnline)
        val dnsProtected = dnsMode.equals("hostname", ignoreCase = true)

        val rawA11y = if (serverOnline) ServerClient.request(ctx, "getsetting secure enabled_accessibility_services") else readLocalSecure(ctx, "enabled_accessibility_services")
        val rawNotif = if (serverOnline) ServerClient.request(ctx, "getsetting secure enabled_notification_listeners") else readLocalSecure(ctx, "enabled_notification_listeners")

        val a11yPkgs = parseComponentPackageList(rawA11y).filter { it !in systemPkgs && it != ctx.packageName }
        val notifPkgs = parseComponentPackageList(rawNotif).filter { it !in systemPkgs && it != ctx.packageName }

        val clipPkgs = if (serverOnline) {
            parseQueryOpPackages(ServerClient.request(ctx, "cmd appops query-op --user 0 READ_CLIPBOARD allow"))
                .filter { it !in systemPkgs && it != ctx.packageName }
        } else {
            emptyList()
        }
        val installPkgs = if (serverOnline) {
            parseQueryOpPackages(ServerClient.request(ctx, "cmd appops query-op --user 0 REQUEST_INSTALL_PACKAGES allow"))
                .filter { it !in systemPkgs && it != ctx.packageName }
        } else {
            emptyList()
        }

        val score = computeScore(
            confirmDeviceOn = confirmOn,
            flagSecureOn = secureOn,
            allowedFullCount = fullCount,
            usbDebugOn = usbDebugOn,
            wifiDebugOn = wifiDebugOn,
            adbVerifierOn = adbVerifierOn,
            privateDnsProtected = dnsProtected,
            a11yCount = a11yPkgs.size,
            notifListenerCount = notifPkgs.size,
            clipboardCount = clipPkgs.size,
            installerCount = installPkgs.size
        )

        return Report(
            serverOnline = serverOnline,
            confirmDeviceOn = confirmOn,
            flagSecureOn = secureOn,
            allowedTotal = allowed.size,
            allowedFull = fullCount,
            usbDebugOn = usbDebugOn,
            wifiDebugOn = wifiDebugOn,
            adbVerifierOn = adbVerifierOn,
            privateDnsProtected = dnsProtected,
            thirdPartyAccessibility = a11yPkgs,
            thirdPartyNotifListeners = notifPkgs,
            thirdPartyClipboardPkgs = clipPkgs,
            thirdPartyInstallerPkgs = installPkgs,
            riskyHardwareApps = riskyApps.take(12),
            score = score
        )
    }

    private fun collectHardwareRisks(pi: PackageInfo): List<RiskKind> {
        val perms = pi.requestedPermissions ?: return emptyList()
        val flags = pi.requestedPermissionsFlags ?: return emptyList()
        val granted = HashSet<String>()
        for (i in perms.indices) {
            if (i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                granted.add(perms[i])
            }
        }
        return TRACKED_DANGEROUS_PERMS.mapNotNull { (perm, kind) ->
            if (perm in granted) kind else null
        }
    }

    private fun readLocalSecure(ctx: Context, key: String): String =
        runCatching { SysSettings.Secure.getString(ctx.contentResolver, key).orEmpty() }.getOrDefault("")

    private fun readStrSetting(ctx: Context, ns: String, key: String, online: Boolean): String {
        if (online) return SystemTweaks.parseSettingValue(ServerClient.request(ctx, "getsetting $ns $key"))
        return runCatching {
            if (ns == "global") SysSettings.Global.getString(ctx.contentResolver, key).orEmpty()
            else SysSettings.Secure.getString(ctx.contentResolver, key).orEmpty()
        }.getOrDefault("")
    }

    private fun readIntSetting(ctx: Context, ns: String, key: String, online: Boolean): Int {
        val s = readStrSetting(ctx, ns, key, online)
        return s.toIntOrNull() ?: if (key == "verifier_verify_adb_installs") 1 else 0
    }

    fun denyClipboard(ctx: Context, pkg: String): Boolean {
        if (!AppInspector.isValidPackage(pkg)) return false
        return ServerClient.request(ctx, "appop $pkg READ_CLIPBOARD ignore").startsWith("OK")
    }

    fun revokeInstallPackages(ctx: Context, pkg: String): Boolean {
        if (!AppInspector.isValidPackage(pkg)) return false
        return ServerClient.request(ctx, "appop $pkg REQUEST_INSTALL_PACKAGES default").startsWith("OK")
    }

    fun disableWifiDebug(ctx: Context): Boolean =
        ServerClient.request(ctx, "putsetting global adb_wifi_enabled 0").startsWith("OK")

    fun enableAdbVerifier(ctx: Context): Boolean =
        ServerClient.request(ctx, "putsetting global verifier_verify_adb_installs 1").startsWith("OK")

    /**
     * Emergency lockdown: revokes every allowed/pending bridge app, clears pending danger requests,
     * requests server stop, rotates the server token, and inhibits background auto-restart for this boot.
     */
    fun panicLockdown(ctx: Context): Int {
        val revoked = AccessStore.revokeAll(ctx)
        DangerApprovals.clearAll(ctx)
        val boot = runCatching {
            SysSettings.Global.getInt(ctx.contentResolver, SysSettings.Global.BOOT_COUNT, -1)
        }.getOrDefault(-1)
        if (boot >= 0) Settings.setStoppedBootCount(ctx, boot)
        ServerClient.request(ctx, "stop")
        ServerClient.resetToken(ctx)
        ServerScript.tokenFile(ctx).let { if (it.exists()) runCatching { it.delete() } }
        return revoked
    }
}
