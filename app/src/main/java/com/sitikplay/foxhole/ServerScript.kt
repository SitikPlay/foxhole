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

    // Экранируем одиночные кавычки для одинарных кавычек shell: локализованная
    // строка попадает в `echo '...'`, и апостроф в будущем переводе сломал бы
    // синтаксис скрипта (сервер бы не стартовал).
    private fun shellSingleQuote(s: String): String = s.replace("'", "'\\''")

    fun content(pkg: String, tokenPath: String, port: Int, noTokenMsg: String): String {
        val invoke = "app_process -Djava.class.path=\"\$CLASSPATH\" " +
            "/system/bin --nice-name=foxhole ${Server::class.java.name} \"\$TOK\" $port " +
            "> /data/local/tmp/foxhole.log 2>&1 < /dev/null &"
        return "#!/system/bin/sh\n" +
            "TOK=$(cat \"$tokenPath\" 2>/dev/null)\n" +
            "if [ -z \"\$TOK\" ]; then\n" +
            "  echo '${shellSingleQuote(noTokenMsg)}'\n" +
            "  exit 1\n" +
            "fi\n" +
            "CLASSPATH=\$(pm path $pkg | head -n 1 | sed 's/^package://' | tr -d '\\r')\n" +
            "if [ -z \"\$CLASSPATH\" ]; then\n" +
            "  echo 'ERR package path not found'\n" +
            "  exit 1\n" +
            "fi\n" +
            "if command -v setsid >/dev/null 2>&1; then\n" +
            "  setsid $invoke\n" +
            "else\n" +
            "  nohup $invoke\n" +
            "fi\n" +
            "rm -f \"$tokenPath\"\n" +
            "sleep 3\n"
    }

    // Синхронизация обязательна: параллельные write() (плитка + Worker) могут
    // дать interleaving, при котором start.token получит устаревший токен, а
    // хранилище — новый: сервер стартует с одним токеном, клиенты шлют другой.
    @Synchronized
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

    // Скрипт сам удаляет start.token (rm -f внутри), но если он так и не был
    // выполнен (спаривание сделано, запуск отменён, перезагрузка), валидный
    // токен остаётся на диске неограниченно долго. Убираем его, когда сервер
    // не работает. Вызов из фонового потока (внутри ping-проверки).
    @Synchronized
    fun pruneStaleToken(ctx: Context) {
        try {
            if (ServerClient.request(ctx, "ping").startsWith("OK pong")) return
            val tf = tokenFile(ctx)
            if (tf.exists() && tf.delete()) {
                AccessStore.log(ctx, "убран не потреблённый start.token (скрипт не выполнялся)")
            }
        } catch (e: Exception) {
            android.util.Log.w("FoxholeScript", "pruneStaleToken: ${e.javaClass.simpleName}")
        }
    }
}
