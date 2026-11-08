package dev.quanve.phonelab

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HidSession {
    private var proc: Process? = null
    private var writer: BufferedWriter? = null
    @Volatile private var ready = false
    private val lock = Any()

    val isActive: Boolean get() = ready && proc?.isAlive == true


    fun start(timeoutMs: Long = 15000): Boolean {
        stop()
        return try {
            val p = ProcessBuilder(
                RootShell.suPath, "-mm", "-c", "/data/linux/bin/hid-daemon.sh"
            ).redirectErrorStream(true).start()
            proc = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream))
            val latch = CountDownLatch(1)
            Thread {
                try {
                    BufferedReader(p.inputStream.reader()).forEachLine { line ->
                        if (line.trim() == "READY") {
                            ready = true
                            latch.countDown()
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }.apply { isDaemon = true; start() }

            val got = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!got || !ready) {
                stop()
                false
            } else true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        val p = proc ?: return
        try { sendRaw("quit\n") } catch (_: Exception) {}
        try { p.waitFor(3, TimeUnit.SECONDS) } catch (_: Exception) {}
        try { p.destroy() } catch (_: Exception) {}
        synchronized(lock) {
            writer = null
            proc = null
            ready = false
        }
    }

    private fun sendRaw(s: String) {
        synchronized(lock) {
            val w = writer ?: return
            w.write(s)
            w.flush()
        }
    }

    fun keyTap(mod: Int, code: Int) = sendRaw("k $mod $code\n")
    fun keyDown(mod: Int, code: Int) = sendRaw("kd $mod $code\n")
    fun keyUp() = sendRaw("ku\n")
    fun click(mask: Int) = sendRaw("c $mask\n")
    fun mouse(mask: Int, dx: Int, dy: Int, wheel: Int) =
        sendRaw("m $mask $dx $dy $wheel\n")


    fun text(text: String, layout: String = "US") {
        val sb = StringBuilder(text.length * 10)
        for (ch in text) {
            val k = (if (layout == "RU") Keys.ofRu(ch) else Keys.of(ch)) ?: continue
            sb.append("k ").append(k.first).append(' ').append(k.second).append('\n')
        }
        if (sb.isNotEmpty()) sendRaw(sb.toString())
    }
}

object Keys {
    const val CTRL = 1
    const val SHIFT = 2
    const val ALT = 4
    const val WIN = 8

    const val ENTER = 40
    const val ESC = 41
    const val BACKSPACE = 42
    const val TAB = 43
    const val SPACE = 44
    const val DELETE = 76
    const val INSERT = 73
    const val HOME = 74
    const val PAGE_UP = 75
    const val END = 77
    const val PAGE_DOWN = 78
    const val RIGHT = 79
    const val LEFT = 80
    const val DOWN = 81
    const val UP = 82

    fun f(n: Int): Int = if (n <= 12) 57 + n else 91 + n

    const val CAPS = 57
    const val PRINT = 70
    const val SCROLL = 71
    const val PAUSE = 72
    const val MENU = 101
    const val NUM = 83


    fun of(ch: Char): Pair<Int, Int>? = when (ch) {
        in 'a'..'z' -> 0 to (ch - 'a' + 4)
        in 'A'..'Z' -> SHIFT to (ch - 'A' + 4)
        in '1'..'9' -> 0 to (ch - '1' + 30)
        '0' -> 0 to 39
        ' ' -> 0 to SPACE
        '\n' -> 0 to ENTER
        '\t' -> 0 to TAB
        '!' -> SHIFT to 30
        '@' -> SHIFT to 31
        '#' -> SHIFT to 32
        '$' -> SHIFT to 33
        '%' -> SHIFT to 34
        '^' -> SHIFT to 35
        '&' -> SHIFT to 36
        '*' -> SHIFT to 37
        '(' -> SHIFT to 38
        ')' -> SHIFT to 39
        '-' -> 0 to 45
        '_' -> SHIFT to 45
        '=' -> 0 to 46
        '+' -> SHIFT to 46
        '[' -> 0 to 47
        '{' -> SHIFT to 47
        ']' -> 0 to 48
        '}' -> SHIFT to 48
        '\\' -> 0 to 49
        '|' -> SHIFT to 49
        ';' -> 0 to 51
        ':' -> SHIFT to 51
        '\'' -> 0 to 52
        '"' -> SHIFT to 52
        '`' -> 0 to 53
        '~' -> SHIFT to 53
        ',' -> 0 to 54
        '<' -> SHIFT to 54
        '.' -> 0 to 55
        '>' -> SHIFT to 55
        '/' -> 0 to 56
        '?' -> SHIFT to 56
        else -> null
    }


    fun ofRu(ch: Char): Pair<Int, Int>? {
        val lower = ch.lowercaseChar()
        val shift = if (ch.isUpperCase()) SHIFT else 0
        val code = when (lower) {
            'й' -> 20; 'ц' -> 26; 'у' -> 8; 'к' -> 21; 'е' -> 23; 'н' -> 28; 'г' -> 24
            'ш' -> 12; 'щ' -> 18; 'з' -> 19; 'х' -> 47; 'ъ' -> 48
            'ф' -> 4; 'ы' -> 22; 'в' -> 7; 'а' -> 9; 'п' -> 10; 'р' -> 11; 'о' -> 13
            'л' -> 14; 'д' -> 15; 'ж' -> 51; 'э' -> 52
            'я' -> 29; 'ч' -> 27; 'с' -> 6; 'м' -> 25; 'и' -> 5; 'т' -> 17; 'ь' -> 16
            'б' -> 54; 'ю' -> 55; 'ё' -> 53
            else -> return of(ch)
        }
        return shift to code
    }
}
