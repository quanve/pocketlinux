package dev.quanve.phonelab

import java.io.File
import java.util.concurrent.TimeUnit

object RootShell {
    data class Out(val code: Int, val text: String)

    private val su: String =
        listOf("/system/xbin/su", "/system/bin/su", "/debug_ramdisk/su")
            .firstOrNull { File(it).canExecute() } ?: "su"

    val suPath: String get() = su

    fun run(command: String, timeoutSec: Long = 30): Out = runWithInput(command, null, timeoutSec)

    fun runWithInput(command: String, input: String?, timeoutSec: Long = 30): Out {
        return try {
            val p = ProcessBuilder(su, "-mm", "-c", command).redirectErrorStream(true).start()
            if (input != null) {
                p.outputStream.bufferedWriter().use { w -> w.write(input); w.flush() }
            } else {
                p.outputStream.close()
            }
            val sb = StringBuilder()
            val reader = Thread {
                p.inputStream.bufferedReader().use { r -> sb.append(r.readText()) }
            }.apply { isDaemon = true; start() }

            val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) p.destroyForcibly()
            reader.join(2000)
            val code = if (finished) p.exitValue() else -1
            Out(if (finished) code else -1, sb.toString().trimEnd() + if (!finished) "\n[timeout ${timeoutSec}s]" else "")
        } catch (e: Exception) {
            Out(-1, "root error: ${e.message}")
        }
    }
}

fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"
