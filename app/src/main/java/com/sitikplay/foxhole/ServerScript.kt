package com.sitikplay.foxhole

import android.content.Context
import java.io.File

object ServerScript {

    fun file(ctx: Context, name: String = "start.sh"): File {
        val dir = ctx.getExternalFilesDir(null)
            ?: File("/storage/emulated/0/Android/data/${ctx.packageName}/files")
        return File(dir, name)
    }

    fun content(pkg: String, token: String, port: Int): String {
        return "#!/system/bin/sh\n" +
            "CLASSPATH=\$(pm path $pkg | head -n 1 | sed 's/^package://')\n" +
            "setsid app_process -Djava.class.path=\"\$CLASSPATH\" " +
            "/system/bin --nice-name=foxhole ${Server::class.java.name} $token $port " +
            "> /data/local/tmp/foxhole.log 2>&1 < /dev/null &\n" +
            "sleep 3\n"
    }

    fun write(ctx: Context): String? {
        return try {
            val f = file(ctx)
            f.writeText(content(ctx.packageName, ServerClient.token(ctx), Settings.port(ctx)))
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

