package dev.quanve.phonelab

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PcPanel(
    prefs: SharedPreferences,
    onToast: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hid = remember { HidSession() }

    var status by remember { mutableStateOf<PcBridge.Status?>(null) }
    var linuxLine by remember { mutableStateOf("") }
    var windowsLine by remember { mutableStateOf("") }
    var output by remember {
        mutableStateOf(prefs.getString("a_pc_output", "") ?: "")
    }
    var command by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var hidLine by remember { mutableStateOf("HID is off") }
    var linuxOpen by remember { mutableStateOf(false) }
    var windowsOpen by remember { mutableStateOf(false) }

    suspend fun loadStatus() {
        val st = withContext(Dispatchers.IO) { PcBridge.status() }
        status = st
        if (st.running && st.ip.isNotBlank() && st.token.isNotBlank()) {
            val pair = withContext(Dispatchers.IO) {
                PcBootstrap.linux(ctx, st.ip, st.pcPort, st.token) to
                    PcBootstrap.windows(ctx, st.ip, st.pcPort, st.token)
            }
            linuxLine = pair.first
            windowsLine = pair.second
        }
    }

    fun startBridge() {
        scope.launch {
            busy = true
            val text = withContext(Dispatchers.IO) { PcBridge.start() }
            loadStatus()
            busy = false
            onToast(text.lineSequence().firstOrNull().orEmpty())
        }
    }

    fun stopBridge() {
        scope.launch {
            busy = true
            withContext(Dispatchers.IO) { PcBridge.stop() }
            loadStatus()
            busy = false
        }
    }

    fun runCommand() {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        if (busy) return
        scope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) { PcBridge.exec(cmd, 120) }
            val text = if (r.ok) {
                buildString {
                    append("$ ").append(cmd).append('\n')
                    append(r.out)
                    if (r.out.isNotEmpty() && !r.out.endsWith("\n")) append('\n')
                    append("[rc=").append(r.rc).append(", ").append(r.ms).append(" ms]\n")
                }
            } else {
                "$ $cmd\n! ${r.error}\n"
            }
            output = (text + "\n" + output).take(200_000)
            prefs.edit().putString("a_pc_output", output).apply()
            busy = false
        }
    }

    fun typeLine(line: String, windows: Boolean, openWindow: Boolean) {
        if (line.isBlank()) {
            onToast("bridge is not ready")
            return
        }
        if (busy) return
        scope.launch {
            busy = true
            hidLine = "enabling HID…"
            val ok = withContext(Dispatchers.IO) { hid.start() }
            if (!ok) {
                hidLine = "HID did not start"
                busy = false
                return@launch
            }
            hidLine = "HID active, typing…"
            if (openWindow) {
                if (windows) {
                    withContext(Dispatchers.IO) {
                        hid.keyTap(Keys.WIN, 21)
                    }
                    delay(900)
                    withContext(Dispatchers.IO) {
                        hid.text("powershell", "US")
                        hid.keyTap(0, Keys.ENTER)
                    }
                    delay(3_500)
                } else {
                    withContext(Dispatchers.IO) {
                        hid.keyTap(Keys.CTRL or Keys.ALT, 23)
                    }
                    delay(2_000)
                }
            }
            withContext(Dispatchers.IO) {
                hid.text(line, "US")
                hid.keyTap(0, Keys.ENTER)
            }
            delay(2_500)
            withContext(Dispatchers.IO) { hid.stop() }
            hidLine = "HID is off, adb is returning"
            busy = false
            onToast(
                if (!openWindow) "command sent to the active window"
                else if (windows) "command sent to Win+R"
                else "command sent to the terminal"
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { hid.stop() } }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { PcBridge.start() }
        while (true) {
            loadStatus()
            delay(2_000)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionCard("PC bridge") {
            val st = status
            Text(
                when {
                    st == null -> "checking…"
                    !st.running -> "bridge is not running" +
                        if (st.error.isNotBlank()) " · ${st.error}" else ""
                    else -> "bridge running · ${st.ip}:${st.pcPort}"
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (st?.running == true) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Text(
                if (st?.pc?.connected == true) {
                    "PC: ${st.pc.os} · ${st.pc.host} (${st.pc.peer})"
                } else {
                    "PC is not connected"
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("REFRESH", {
                    scope.launch { loadStatus() }
                }, Modifier.weight(1f))
                if (st?.running == true) {
                    SecondaryButton("STOP", { stopBridge() }, Modifier.weight(1f))
                } else {
                    PrimaryButton("START", { startBridge() }, Modifier.weight(1f))
                }
            }
            Text(hidLine, fontSize = 11.sp)
            InfoText(
                "The bridge listens on the phone at port ${st?.pcPort ?: 4444}. " +
                    "The PC connects to the phone over the shared network (Wi-Fi/Ethernet)."
            )
        }

        BootstrapCard(
            title = "Linux",
            line = linuxLine,
            open = linuxOpen,
            onToggle = { linuxOpen = !linuxOpen },
            onCopy = {
                if (linuxLine.isNotBlank()) {
                    copyClipboard(ctx, linuxLine)
                    onToast("copied")
                }
            },
            primaryLabel = "TERMINAL + TYPE",
            onPrimary = { typeLine(linuxLine, windows = false, openWindow = true) },
            secondaryLabel = "TYPE INTO ACTIVE WINDOW",
            onSecondary = { typeLine(linuxLine, windows = false, openWindow = false) },
            info = "The first button presses Ctrl+Alt+T, waits for the terminal, and types the command. " +
                "The second types into an already open terminal. Requires bash, a shared network with " +
                "the phone, and a US PC layout."
        )

        BootstrapCard(
            title = "Windows",
            line = windowsLine,
            open = windowsOpen,
            onToggle = { windowsOpen = !windowsOpen },
            onCopy = {
                if (windowsLine.isNotBlank()) {
                    copyClipboard(ctx, windowsLine)
                    onToast("copied")
                }
            },
            primaryLabel = "WIN+R + TYPE",
            onPrimary = { typeLine(windowsLine, windows = true, openWindow = true) },
            secondaryLabel = "TYPE INTO ACTIVE WINDOW",
            onSecondary = { typeLine(windowsLine, windows = true, openWindow = false) },
            info = "Works without administrator rights and without downloads. " +
                "The first button opens PowerShell through Win+R and types the command, " +
                "the second types into an already open PowerShell window. PC layout: US."
        )

        SectionCard("Console") {
            OutlinedTextField(
                command, { command = it },
                label = { Text("command on the PC") },
                placeholder = { Text("multi-line command") },
                minLines = 2,
                maxLines = 6,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("RUN", { runCommand() }, Modifier.weight(1f))
                SecondaryButton("COPY", {
                    if (output.isNotBlank()) {
                        copyClipboard(ctx, output)
                        onToast("copied")
                    }
                }, Modifier.weight(1f))
                SecondaryButton("CLEAR", {
                    output = ""
                    prefs.edit().putString("a_pc_output", "").apply()
                }, Modifier.weight(1f))
            }
            if (busy) {
                Text("running…", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            SelectionContainer {
                Text(
                    output.ifBlank { "command output appears here" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
            InfoText(
                "Output is captured as text. Interactive TUI programs (btop, htop, vim) need a real " +
                    "terminal: use TERMINAL + TYPE or WIN+R + TYPE above."
            )
        }
    }
}

@Composable
private fun BootstrapCard(
    title: String,
    line: String,
    open: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    info: String
) {
    SectionCard(title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (line.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (line.isBlank()) "bridge is not ready" else "bootstrap ready",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (line.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggle) {
                Text(if (open) "HIDE" else "DETAILS", fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("COPY", onCopy, Modifier.weight(1f))
            PrimaryButton(primaryLabel, onPrimary, Modifier.weight(2f))
        }
        if (open) {
            SelectionContainer {
                Text(
                    line.ifBlank { "bridge is not ready" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
            SecondaryButton(secondaryLabel, onSecondary, Modifier.fillMaxWidth())
            InfoText(info)
        }
    }
}
