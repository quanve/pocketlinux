package dev.quanve.phonelab

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OpencodeApi {
    const val BASE = "http://127.0.0.1:4096"
    const val CH = "/data/linux/debian"
    const val PW_FILE = "$CH/root/.config/phone-lab/server-password"
    const val START_SCRIPT = "/data/linux/bin/ocserve-start.sh"
    const val STOP_SCRIPT = "/data/linux/bin/ocserve-stop.sh"
    const val DEFAULT_MODEL = "opencode/deepseek-v4.1-flash-free"

    class ApiException(message: String) : Exception(message)

    data class Info(val version: String, val pid: Int, val url: String)
    data class ServerState(val ok: Boolean, val text: String)

    data class Session(
        val id: String,
        val title: String,
        val directory: String,
        val created: Long,
        val updated: Long,
        val busy: Boolean,
        val agent: String,
        val model: String,
        val cost: Double
    ) {
        val modelShort: String get() = model.substringAfter('/')
    }

    data class Part(
        val type: String,
        val text: String = "",
        val name: String = "",
        val status: String = "",
        val detail: String = "",
        val error: String = ""
    )

    data class Message(
        val id: String,
        val type: String,
        val created: Long,
        val completed: Boolean = false,
        val text: String = "",
        val parts: List<Part> = emptyList(),
        val outcome: String = "",
        val error: String = "",
        val finish: String = "",
        val agent: String = "",
        val model: String = "",
        val command: String = "",
        val shellStatus: String = "",
        val shellOutput: String = ""
    )

    data class Model(
        val providerID: String,
        val modelID: String,
        val name: String,
        val enabled: Boolean,
        val status: String
    ) {
        val ref: String get() = "$providerID/$modelID"
    }



    @Volatile private var cachedPassword: String? = null

    private fun password(): String {
        cachedPassword?.let { if (it.isNotBlank()) return it }
        val r = RootShell.run("cat ${shq(PW_FILE)} 2>/dev/null", 15)
        var pass = r.text.trim()
        if (pass.isBlank()) {
            RootShell.run("${shq(START_SCRIPT)}", 30)
            pass = RootShell.run("cat ${shq(PW_FILE)} 2>/dev/null", 15).text.trim()
        }
        if (pass.isBlank()) throw ApiException("no server password (${PW_FILE})")
        cachedPassword = pass
        return pass
    }

    private fun authHeader(): String {
        val raw = "opencode:${password()}".toByteArray()
        return "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP)
    }



    private data class Res(val code: Int, val text: String)

    private fun http(
        method: String,
        path: String,
        body: JSONObject? = null,
        timeoutMs: Int = 30_000
    ): Res {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 4_000
            readTimeout = timeoutMs
            setRequestProperty("Authorization", authHeader())
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Res(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        timeoutMs: Int = 30_000
    ): JSONObject? {
        fun once(): Res = try {
            http(method, path, body, timeoutMs)
        } catch (e: Exception) {
            throw ApiException("no connection to opencode: ${e.message}")
        }
        var r = once()
        if (r.code == 401) {

            cachedPassword = null
            r = once()
        }
        if (r.code !in 200..299) {
            val detail = r.text.take(400).ifBlank { "HTTP ${r.code}" }
            throw ApiException("HTTP ${r.code}: $detail")
        }
        if (r.text.isBlank()) return null
        return try {
            JSONObject(r.text)
        } catch (e: Exception) {
            throw ApiException("invalid server response: ${r.text.take(200)}")
        }
    }

    private fun JSONObject.str(key: String): String {
        val v = opt(key)
        return if (v == null || v == JSONObject.NULL) "" else v.toString()
    }

    private fun JSONObject.long(key: String): Long = optLong(key, 0L)



    fun info(): Info? = try {
        val o = call("GET", "/api/info", timeoutMs = 5_000) ?: return null
        Info(
            version = o.str("version"),
            pid = o.optInt("pid", 0),
            url = o.optJSONArray("urls")?.optString(0).orEmpty()
        )
    } catch (_: Exception) {
        null
    }

    fun ensureServer(): ServerState {
        info()?.let { return ServerState(true, "opencode ${it.version} · pid ${it.pid}") }
        val start = RootShell.run(shq(START_SCRIPT), 30)
        repeat(20) {
            Thread.sleep(700)
            info()?.let { return ServerState(true, "opencode ${it.version} · pid ${it.pid}") }
        }
        val why = start.text.takeLast(400).ifBlank { "server did not respond at $BASE" }
        return ServerState(false, "failed to start: $why")
    }

    fun startServer(): ServerState {
        info()?.let { return ServerState(true, "opencode ${it.version} · pid ${it.pid}") }
        RootShell.run(shq(START_SCRIPT), 30)
        repeat(20) {
            Thread.sleep(700)
            info()?.let { return ServerState(true, "opencode ${it.version} · pid ${it.pid}") }
        }
        return ServerState(false, "server did not respond at $BASE")
    }

    fun stopServer(): ServerState {
        val r = RootShell.run(shq(STOP_SCRIPT), 30)
        Thread.sleep(700)
        val ok = info() == null
        return ServerState(ok, if (ok) "server stopped" else r.text.takeLast(300))
    }



    fun sessions(limit: Int = 50): List<Session> {
        val o = call("GET", "/api/session?limit=$limit") ?: return emptyList()
        val arr = o.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseSession) }
    }

    fun session(id: String): Session? {
        val o = call("GET", "/api/session/$id", timeoutMs = 10_000) ?: return null
        return o.optJSONObject("data")?.let(::parseSession)
    }

    private fun parseSession(o: JSONObject): Session {
        val time = o.optJSONObject("time")
        val created = time?.long("created") ?: 0L
        val updated = time?.long("updated") ?: created
        val busyAt = time?.long("busy") ?: 0L
        val idleAt = time?.long("idle") ?: 0L
        val model = o.optJSONObject("model")
        val modelRef = if (model != null) {
            val p = model.str("providerID")
            val m = model.str("id")
            if (p.isNotBlank() && m.isNotBlank()) "$p/$m" else m
        } else ""
        return Session(
            id = o.str("id"),
            title = o.str("title").ifBlank { "(untitled)" },
            directory = o.optJSONObject("location")?.str("directory").orEmpty(),
            created = created,
            updated = updated,
            busy = busyAt > 0 && busyAt >= idleAt,
            agent = o.str("agent"),
            model = modelRef,
            cost = o.optDouble("cost", 0.0)
        )
    }

    fun createSession(
        title: String,
        directory: String,
        model: String,
        auto: Boolean,
        agent: String = "build"
    ): Session {
        val body = JSONObject().apply {
            put("title", title)
            put("agent", agent)
            val (provider, id) = splitModel(model)
            put("model", JSONObject().put("id", id).put("providerID", provider))
            put("location", JSONObject().put("directory", directory))
            if (auto) {
                put(
                    "permissions",
                    JSONArray().put(
                        JSONObject()
                            .put("action", "*")
                            .put("resource", "*")
                            .put("effect", "allow")
                    )
                )
            }
        }
        val o = call("POST", "/api/session", body, timeoutMs = 30_000)
            ?: throw ApiException("empty response while creating a session")
        val data = o.optJSONObject("data") ?: throw ApiException("response has no data")
        return parseSession(data)
    }

    fun deleteSession(id: String) {
        call("DELETE", "/api/session/$id", timeoutMs = 15_000)
    }

    fun renameSession(id: String, title: String) {
        val body = JSONObject().put("title", title)
        call("PATCH", "/api/session/$id", body, timeoutMs = 15_000)
    }



    fun messages(id: String): List<Message> {
        val o = call("GET", "/api/session/$id/message?order=asc&limit=200", timeoutMs = 30_000)
            ?: return emptyList()
        val arr = o.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseMessage) }
    }

    private fun parseMessage(o: JSONObject): Message {
        val type = o.str("type")
        val time = o.optJSONObject("time")
        val created = time?.long("created") ?: 0L
        val completed = time?.has("completed") == true
        val base = Message(
            id = o.str("id"),
            type = type,
            created = created,
            completed = completed,
            finish = o.str("finish"),
            agent = o.str("agent")
        )
        return when (type) {
            "user", "synthetic", "system" -> base.copy(text = o.str("text"))
            "assistant" -> {
                val model = o.optJSONObject("model")
                val modelRef = if (model != null) {
                    val p = model.str("providerID")
                    val m = model.str("id")
                    if (p.isNotBlank() && m.isNotBlank()) "$p/$m" else m
                } else ""
                base.copy(
                    parts = parseContent(o.optJSONArray("content")),
                    error = errorText(o.optJSONObject("error")),
                    model = modelRef
                )
            }
            "idle" -> base.copy(
                outcome = o.str("outcome"),
                error = errorText(o.optJSONObject("error"))
            )
            "shell" -> base.copy(
                command = o.str("command"),
                shellStatus = o.str("status"),
                shellOutput = o.optJSONObject("output")?.str("output").orEmpty()
            )
            else -> base.copy(text = o.str("text"))
        }
    }

    private fun parseContent(arr: JSONArray?): List<Part> {
        if (arr == null) return emptyList()
        val out = ArrayList<Part>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            when (p.str("type")) {
                "text" -> out.add(Part("text", text = p.str("text")))
                "reasoning" -> out.add(Part("reasoning", text = p.str("text")))
                "tool" -> {
                    val state = p.optJSONObject("state")
                    val status = state?.str("status").orEmpty()
                    val input = state?.opt("input")
                    val inputText = when (input) {
                        is JSONObject -> summarizeInput(input)
                        is String -> input
                        else -> ""
                    }
                    val error = errorText(state?.optJSONObject("error"))
                    val content = state?.optJSONArray("content")
                    val output = buildString {
                        if (content != null) {
                            for (j in 0 until content.length()) {
                                val c = content.optJSONObject(j) ?: continue
                                val t = c.str("text")
                                if (t.isNotBlank()) append(t)
                                val path = c.str("filePath")
                                if (path.isNotBlank()) append(path)
                            }
                        }
                    }
                    out.add(
                        Part(
                            type = "tool",
                            name = p.str("name"),
                            status = status,
                            detail = inputText,
                            text = output.take(4000),
                            error = error
                        )
                    )
                }
            }
        }
        return out
    }

    private fun summarizeInput(input: JSONObject): String {
        for (key in listOf(
            "command", "filePath", "path", "pattern", "query", "description",
            "url", "prompt", "text", "content"
        )) {
            val v = input.opt(key)
            if (v != null && v != JSONObject.NULL) {
                val s = v.toString()
                if (s.isNotBlank()) return s.take(300)
            }
        }
        return input.toString().take(300)
    }

    private fun errorText(o: JSONObject?): String {
        if (o == null) return ""
        val msg = o.str("message")
        val type = o.str("type")
        return when {
            msg.isNotBlank() -> msg
            type.isNotBlank() -> type
            else -> o.toString().take(300)
        }
    }



    fun prompt(id: String, text: String) {
        call(
            "POST",
            "/api/session/$id/prompt",
            JSONObject().put("text", text),
            timeoutMs = 60_000
        )
    }

    fun interrupt(id: String) {
        call("POST", "/api/session/$id/interrupt", timeoutMs = 15_000)
    }



    fun models(directory: String): List<Model> {
        val dir = directory.ifBlank { "/root" }
        val o = call("GET", "/api/model?directory=${enc(dir)}", timeoutMs = 30_000)
            ?: return emptyList()
        val arr = o.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            Model(
                providerID = m.str("providerID"),
                modelID = m.str("modelID"),
                name = m.str("name"),
                enabled = m.optBoolean("enabled", true),
                status = m.str("status")
            )
        }
    }




    fun ensureWorkspace(directory: String): String {
        val dir = directory.trim()
        if (dir.isBlank()) throw ApiException("empty workspace")
        if (!dir.startsWith("/")) throw ApiException("path must be absolute: $dir")
        val inner = "mkdir -p ${shq(dir)} && test -d ${shq(dir)}"
        val r = RootShell.run(
            "chroot $CH /usr/bin/env -i HOME=/root " +
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin " +
                "/bin/bash -c ${shq(inner)}",
            20
        )
        if (r.code != 0) throw ApiException("could not create $dir: ${r.text.take(200)}")
        return dir
    }



    private fun splitModel(model: String): Pair<String, String> {
        val m = model.trim().ifBlank { DEFAULT_MODEL }
        val i = m.indexOf('/')
        return if (i <= 0) Pair("opencode", m) else Pair(m.substring(0, i), m.substring(i + 1))
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    fun formatTime(ms: Long): String {
        if (ms <= 0) return "—"
        val f = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        return f.format(Date(ms))
    }
}
