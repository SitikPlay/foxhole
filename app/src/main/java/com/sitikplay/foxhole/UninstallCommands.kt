package com.sitikplay.foxhole

import java.io.File

object UninstallCommands {

    const val RETENTION_DAYS = 25
    const val RETENTION_MILLIS: Long = RETENTION_DAYS * 24L * 60L * 60L * 1000L

    const val STAGING_ROOT = "/data/local/tmp/foxhole_restore"

    fun uninstallUserCmd(pkg: String): String = "pm uninstall ${quote(pkg)}"

    fun uninstallSystemCmd(pkg: String): String = "pm uninstall -k --user 0 ${quote(pkg)}"

    fun restoreSystemCmd(pkg: String): String = "pm install-existing --user 0 ${quote(pkg)}"

    fun stagingDir(pkg: String): String =
        "$STAGING_ROOT/" + pkg.replace(Regex("[^A-Za-z0-9_.]"), "_")

    fun stagingPath(dir: String, fileName: String): String = "$dir/$fileName"

    fun restoreCopyCmd(files: List<String>, dir: String): String {
        if (files.isEmpty()) return "ERR нет сохранённых APK"
        val cmd = StringBuilder("mkdir -p ").append(quote(dir))
        files.forEach { src ->
            cmd.append(" && cp -f ")
                .append(quote(src))
                .append(" ")
                .append(quote(stagingPath(dir, File(src).name)))
        }
        return cmd.toString()
    }

    fun restoreUserCmd(paths: List<String>): String {
        if (paths.isEmpty()) return "ERR нет сохранённых APK"
        val quoted = paths.joinToString(" ") { quote(it) }
        return "pm install -r --user 0 $quoted"
    }

    private fun quote(s: String): String = "\"${s.replace("\"", "")}\""

    fun quoteShellArg(s: String): String = quote(s)

    fun removeDirCmd(dir: String): String = "rm -rf ${quote(dir)}"

    fun parsePmPath(output: String): List<String> =
        output.lineSequence()
            .map { it.trim() }
            .filter { startsWithPackagePrefix(it) }
            .map { it.substring("package:".length).trim() }
            .filter { it.isNotEmpty() }
            .toList()

    private fun startsWithPackagePrefix(line: String): Boolean =
        line.startsWith("package:")

    fun isExpired(removedAt: Long, now: Long): Boolean = now - removedAt >= RETENTION_MILLIS

    fun daysLeft(removedAt: Long, now: Long): Int {
        val left = RETENTION_MILLIS - (now - removedAt)
        if (left <= 0) return 0
        val day = 24L * 60L * 60L * 1000L
        return ((left + day - 1) / day).toInt()
    }

    fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
