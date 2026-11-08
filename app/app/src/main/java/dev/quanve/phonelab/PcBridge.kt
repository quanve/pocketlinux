package dev.quanve.phonelab

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

object PcBridge {
    private const val HOST = "127.0.0.1"
    private const val CTL_PORT = 4445
    private const val SCRIPT = "/data/linux/bin/pc-bridge.sh"

    data class PcInfo(
        val connected: Boolean = false,
        val os: String = "",
        val host: String = "",
        val peer: String = ""
    )

    data class Status(
        val running: Boolean = false,
        val ip: String = "",
        val token: String = "",
        val pcPort: Int = 4444,
        val pc: PcInfo = PcInfo(),
        val error: String = ""
    )

    data class Result(
        val ok: Boolean,
        val rc: Int = 0,
        val out: String = "",
        val error: String = "",
        val ms: Int = 0
    )

    fun start(): String {
        val out = RootShell.run("sh $SCRIPT start", 30)
        return out.text.ifBlank { "exit ${out.code}" }
    }

    fun stop(): String {
        val out = RootShell.run("sh $SCRIPT stop", 30)
        return out.text.ifBlank { "exit ${out.code}" }
    }

    fun status(): Status {
        val resp = request(JSONObject().put("op", "status"), 3_000)
            ?: return Status(error = "bridge is not responding")
        if (!resp.optBoolean("ok", false)) {
            return Status(error = resp.optString("error", "bridge error"))
        }
        val pcJson = resp.optJSONObject("pc")
        return Status(
            running = resp.optBoolean("running", false),
            ip = resp.optString("ip", ""),
            token = resp.optString("token", ""),
            pcPort = resp.optInt("pc_port", 4444),
            pc = PcInfo(
                connected = pcJson?.optBoolean("connected", false) ?: false,
                os = pcJson?.optString("os", "") ?: "",
                host = pcJson?.optString("host", "") ?: "",
                peer = pcJson?.optString("peer", "") ?: ""
            )
        )
    }

    fun exec(command: String, timeoutSec: Int = 60): Result {
        val req = JSONObject()
            .put("op", "exec")
            .put("cmd", command)
            .put("timeout", timeoutSec)
        val resp = request(req, (timeoutSec + 10) * 1_000L)
            ?: return Result(false, error = "bridge is not responding")
        if (!resp.optBoolean("ok", false)) {
            return Result(false, error = resp.optString("error", "bridge error"))
        }
        return Result(
            ok = true,
            rc = resp.optInt("rc", 0),
            out = resp.optString("out", ""),
            ms = resp.optInt("ms", 0)
        )
    }

    private fun request(req: JSONObject, timeoutMs: Long): JSONObject? {
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(HOST, CTL_PORT), 1_500)
                sock.soTimeout = timeoutMs.toInt()
                val writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream()))
                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                writer.write(req.toString())
                writer.newLine()
                writer.flush()
                val line = reader.readLine() ?: return null
                JSONObject(line)
            }
        } catch (_: Exception) {
            null
        }
    }
}
