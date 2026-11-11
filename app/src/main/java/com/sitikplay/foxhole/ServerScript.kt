package com.sitikplay.foxhole

import android.content.Context
import java.io.File

object ServerScript {

    fun file(ctx: Context, name: String = "start.sh"): File {
        val dir = ctx.getExternalFilesDir(null)
            ?: File("/storage/emulated/0/Android/data/${ctx.packageName}/files")
        return File(dir, name)
    }

    fun tokenFile(ctx: Context): File = File(file(ctx).parentFile, "start.token")

    fun content(pkg: String, tokenPath: String, port: Int, noTokenMsg: String): String {
        return "#!/system/bin/sh\n" +
            "TOK=$(cat \"$tokenPath\" 2>/dev/null)\n" +
            "if [ -z \"\$TOK\" ]; then\n" +
            "  echo '$noTokenMsg'\n" +
            "  exit 1\n" +
            "fi\n" +
            "CLASSPATH=\$(pm path $pkg | head -n 1 | sed 's/^package://')\n" +
            "setsid app_process -Djava.class.path=\"\$CLASSPATH\" " +
            "/system/bin --nice-name=foxhole ${Server::class.java.name} \"\$TOK\" $port " +
            "> /data/local/tmp/foxhole.log 2>&1 < /dev/null &\n" +

            "rm -f \"$tokenPath\"\n" +
            "sleep 3\n"
    }

    fun write(ctx: Context): String? {
        return try {
            val serverAlreadyRunning = ServerClient.request(ctx, "ping").startsWith("OK pong")
            if (!serverAlreadyRunning) ServerClient.newToken(ctx)
            val f = file(ctx)
            f.writeText(
                content(
                    ctx.packageName,
                    tokenFile(ctx).absolutePath,
                    Settings.port(ctx),

                    ctx.getString(R.string.t363)
                )
            )
            tokenFile(ctx).writeText(ServerClient.token(ctx))
            f.absolutePath
        } catch (e: Exception) {

            android.util.Log.w("FoxholeScript", "write failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
