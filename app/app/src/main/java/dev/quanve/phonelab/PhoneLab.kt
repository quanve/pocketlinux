package dev.quanve.phonelab

data class Status(
    val chroot: Boolean = false,
    val pc: Boolean = false,
    val debian: String = "?",
    val zapretRunning: Boolean = false,
    val zapretMode: String = "off",
    val vpn: String = "?",
    val udc: String = "?",
    val model: String = "?",
    val android: String = "?",
    val magisk: String = "?",
    val ip: String = "?",
    val uptime: String = "?",
    val battery: String = "?",
    val dataFree: String = "?",
    val agent: String = "?",
    val bridge: String = "?",
    val share: String = "?",
    val raw: String = ""
)

object PhoneLab {
    const val BIN = "/data/linux/bin"

    fun status(): Status {
        val r = RootShell.run("$BIN/status.sh", 25)
        val lines = r.text.lines().map { it.trim() }
        fun field(name: String): String = lines.firstOrNull { it.startsWith("$name:") }
            ?.removePrefix("$name:")?.trim() ?: "?"
        val zapretLine = lines.firstOrNull { it.startsWith("zapret:") } ?: ""
        val mode = Regex("mode=([a-z]+)").find(zapretLine)?.groupValues?.get(1) ?: "off"
        return Status(
            chroot = lines.any { it == "proc: mounted" },
            pc = lines.any { it == "mnt/pc: mounted" },
            debian = field("debian"),
            zapretRunning = zapretLine.contains("running"),
            zapretMode = mode,
            vpn = if (lines.any { it == "vpn: tun0 up" }) "up" else "down",
            udc = field("udc"),
            model = field("model"),
            android = field("android"),
            magisk = field("magisk"),
            ip = field("ip"),
            uptime = field("uptime"),
            battery = field("battery"),
            dataFree = field("data"),
            agent = field("opencode"),
            bridge = field("bridge"),
            share = field("state"),
            raw = r.text
        )
    }

    fun zapret(action: String): RootShell.Out =
        RootShell.run("$BIN/zapret $action", 45)

    fun mount(host: String, port: String, remote: String): RootShell.Out {
        val args = listOf(host, port, remote)
            .filter { it.isNotBlank() }
            .joinToString(" ") { shq(it) }
        return RootShell.run("$BIN/mount-pc-net.sh $args", 60)
    }

    fun unmount(): RootShell.Out =
        RootShell.run("$BIN/umount-pc.sh", 45)

    fun hid(text: String, delay: Int): RootShell.Out =
        RootShell.run("$BIN/usb-hid.sh ${shq(text)} $delay", 90)
}
