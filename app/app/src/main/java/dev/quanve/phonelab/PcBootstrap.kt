package dev.quanve.phonelab

import android.content.Context
import android.util.Base64

object PcBootstrap {
    fun linux(ctx: Context, ip: String, port: Int, token: String): String {
        val body = fill(read(ctx, "linux.sh"), ip, port, token)
        val payload = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "echo $payload | base64 -d | bash"
    }

    fun windows(ctx: Context, ip: String, port: Int, token: String): String {
        val body = fill(read(ctx, "windows.ps1"), ip, port, token)
        val payload = Base64.encodeToString(body.toByteArray(Charsets.UTF_16LE), Base64.NO_WRAP)
        return "powershell -NoP -EncodedCommand $payload"
    }

    private fun fill(body: String, ip: String, port: Int, token: String): String =
        body.replace("__IP__", ip)
            .replace("__PORT__", port.toString())
            .replace("__TOKEN__", token)

    private fun read(ctx: Context, name: String): String =
        ctx.assets.open(name).bufferedReader().use { it.readText() }
}
