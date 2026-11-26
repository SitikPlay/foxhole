package com.sitikplay.foxhole

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Inspects installed application metadata, requested permissions, and AppOps modes,
 * and applies non-root control actions through Foxhole's UID 2000 server.
 */
object AppInspector {

    val PKG_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    val PERM_REGEX = Regex("^[a-zA-Z0-9_.]+$")

    private val SPECIAL_OP_BY_PERM = mapOf(
        "android.permission.SYSTEM_ALERT_WINDOW" to "SYSTEM_ALERT_WINDOW",
        "android.permission.PACKAGE_USAGE_STATS" to "GET_USAGE_STATS",
        "android.permission.WRITE_SETTINGS" to "WRITE_SETTINGS",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to "MANAGE_EXTERNAL_STORAGE"
    )

    private val SHELL_GRANTABLE_PERMS = setOf(
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.DUMP",
        "android.permission.READ_LOGS"
    )

    data class PermItem(
        val name: String,
        val shortName: String,
        val granted: Boolean,
        val appOpName: String?,
        val toggleable: Boolean
    )

    data class Details(
        val pkg: String,
        val label: String,
        val uid: Int,
        val versionName: String,
        val targetSdk: Int,
        val system: Boolean,
        val disabled: Boolean,
        val apkPath: String,
        val pid: String?,
        val serverOnline: Boolean,
        val bgRestricted: Boolean,
        val permissions: List<PermItem>
    )

    fun isValidPackage(pkg: String): Boolean = PKG_REGEX.matches(pkg)

    fun parsePid(raw: String): String? {
        if (!raw.startsWith("OK")) return null
        val rest = raw.removePrefix("OK").trim()
        if (rest.isEmpty()) return null
        val first = rest.split(Regex("\\s+")).firstOrNull() ?: return null
        return if (first.all { it.isDigit() }) first else null
    }

    fun parseAppOpMode(raw: String, op: String): String {
        if (!raw.startsWith("OK")) return "unknown"
        val body = raw.removePrefix("OK").trim()
        var fallback = "default"
        for (line in body.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("$op:", ignoreCase = true)) {
                val mode = t.substringAfter(':').trim()
                    .substringBefore(';')
                    .substringBefore(' ')
                    .trim()
                    .lowercase()
                if (mode.isNotEmpty()) return mode
            } else if (t.startsWith("Default mode:", ignoreCase = true)) {
                fallback = t.substringAfter(':').trim().lowercase().ifEmpty { "default" }
            }
        }
        return fallback
    }

    fun parseApkPaths(raw: String): List<String> {
        if (!raw.startsWith("OK")) return emptyList()
        return raw.removePrefix("OK").trim()
            .lineSequence()
            .map { it.trim().removePrefix("package:") }
            .filter { it.isNotEmpty() }
            .toList()
    }

    fun inspect(ctx: Context, pkg: String): Details? {
        if (!isValidPackage(pkg)) return null
        val pm = ctx.packageManager
        val pi = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrNull() ?: return null
        val ai = pi.applicationInfo ?: return null
        val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pkg)
        val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val disabled = !ai.enabled
        val apkPath = ai.sourceDir ?: ""

        val online = runCatching { ServerClient.request(ctx, "ping").startsWith("OK pong") }
            .getOrDefault(false)
        val pid = if (online) parsePid(ServerClient.request(ctx, "pidof $pkg")) else null
        val bgMode = if (online) {
            parseAppOpMode(
                ServerClient.request(ctx, "cmd appops get $pkg RUN_ANY_IN_BACKGROUND"),
                "RUN_ANY_IN_BACKGROUND"
            )
        } else {
            "allow"
        }

        val perms = buildPermissionItems(ctx, pm, pi, pkg, online)
        return Details(
            pkg = pkg,
            label = label,
            uid = ai.uid,
            versionName = pi.versionName ?: "?",
            targetSdk = ai.targetSdkVersion,
            system = system,
            disabled = disabled,
            apkPath = apkPath,
            pid = pid,
            serverOnline = online,
            bgRestricted = bgMode == "ignore" || bgMode == "deny",
            permissions = perms
        )
    }

    private fun buildPermissionItems(
        ctx: Context,
        pm: PackageManager,
        pi: PackageInfo,
        pkg: String,
        online: Boolean
    ): List<PermItem> {
        val reqPerms = pi.requestedPermissions ?: return emptyList()
        val reqFlags = pi.requestedPermissionsFlags
        val items = ArrayList<PermItem>()
        for (i in reqPerms.indices) {
            val perm = reqPerms[i]
            val specialOp = SPECIAL_OP_BY_PERM[perm]
            val isRuntime = isRuntimePermission(pm, perm)
            val isShellGrantable = perm in SHELL_GRANTABLE_PERMS
            if (specialOp == null && !isRuntime && !isShellGrantable) continue

            val grantedFlag = reqFlags != null && i < reqFlags.size &&
                (reqFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            val granted = if (specialOp != null && online) {
                val mode = parseAppOpMode(ServerClient.request(ctx, "cmd appops get $pkg $specialOp"), specialOp)
                mode == "allow" || mode == "foreground" || (mode == "default" && grantedFlag)
            } else {
                grantedFlag
            }
            items.add(
                PermItem(
                    name = perm,
                    shortName = perm.removePrefix("android.permission."),
                    granted = granted,
                    appOpName = specialOp,
                    toggleable = online && (specialOp != null || isRuntime || isShellGrantable)
                )
            )
        }
        return items.sortedWith(
            compareByDescending<PermItem> { it.name in SHELL_GRANTABLE_PERMS || it.appOpName != null }
                .thenByDescending { it.granted }
                .thenBy { it.shortName }
        ).take(24)
    }

    private fun isRuntimePermission(pm: PackageManager, perm: String): Boolean = try {
        @Suppress("DEPRECATION")
        val info = pm.getPermissionInfo(perm, 0)
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.protection
        } else {
            @Suppress("DEPRECATION")
            info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE
        }
        base == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
    } catch (_: Exception) {
        false
    }

    fun setBackgroundRestricted(ctx: Context, pkg: String, restrict: Boolean): Boolean {
        if (!isValidPackage(pkg)) return false
        val mode = if (restrict) "ignore" else "allow"
        return ServerClient.request(ctx, "appop $pkg RUN_ANY_IN_BACKGROUND $mode").startsWith("OK")
    }

    fun setPermissionOrOp(ctx: Context, pkg: String, item: PermItem, enable: Boolean): Boolean {
        if (!isValidPackage(pkg) || !PERM_REGEX.matches(item.name)) return false
        return if (item.appOpName != null) {
            val mode = if (enable) "allow" else "default"
            ServerClient.request(ctx, "appop $pkg ${item.appOpName} $mode").startsWith("OK")
        } else {
            val action = if (enable) "grant" else "revoke"
            ServerClient.request(ctx, "$action $pkg ${item.name}").startsWith("OK")
        }
    }

    fun forceStop(ctx: Context, pkg: String): Boolean {
        if (!isValidPackage(pkg)) return false
        return ServerClient.request(ctx, "am force-stop $pkg").startsWith("OK")
    }

    fun setFrozen(ctx: Context, pkg: String, freeze: Boolean): Boolean {
        if (!isValidPackage(pkg)) return false
        val cmd = if (freeze) UninstallCommands.freezeCmd(pkg) else UninstallCommands.unfreezeCmd(pkg)
        return ServerClient.request(ctx, cmd).startsWith("OK")
    }

    internal fun buildLogcatCmd(pkg: String, pid: String?): String {
        val cleanPid = pid?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
        return if (cleanPid != null) {
            "logcat -d -t 80 --pid=$cleanPid"
        } else {
            "logcat -d -t 500 | grep -F $pkg | tail -n 80 || true"
        }
    }

    internal fun parseLogcatResult(res: String): String {
        val trimmed = res.trim()
        return when {
            trimmed.startsWith("OK") -> trimmed.removePrefix("OK").trim()
            trimmed == "ERR code=1" -> ""
            else -> res
        }
    }

    fun readLogcat(ctx: Context, pkg: String, pid: String?): String {
        if (!isValidPackage(pkg)) return ""
        return parseLogcatResult(ServerClient.request(ctx, buildLogcatCmd(pkg, pid)))
    }
}
