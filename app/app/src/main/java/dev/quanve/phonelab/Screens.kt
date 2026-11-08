package dev.quanve.phonelab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias Act = (String, () -> RootShell.Out) -> Unit

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title.uppercase(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) { Text(text) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}

@Composable
fun InfoText(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
    )
}

@Composable
fun StatusScreen(s: Status, onRefresh: () -> Unit) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("Device") {
                StatusLine("Model", s.model)
                StatusLine("Android", s.android)
                StatusLine("Magisk", s.magisk, s.magisk != "?" && s.magisk != "none")
                StatusLine("IP", s.ip)
                StatusLine("Uptime", s.uptime)
                StatusLine("Battery", s.battery)
                StatusLine("Data", s.dataFree)
            }
        }
        item {
            SectionCard("Environment") {
                StatusLine("Debian", s.debian.replace("Debian GNU/Linux ", ""), s.debian.startsWith("Debian"))
                StatusLine("Chroot", if (s.chroot) "mounted" else "not mounted", s.chroot)
                StatusLine("opencode", s.agent, s.agent == "running")
                StatusLine("PC bridge", s.bridge, s.bridge == "running")
                StatusLine("PC files", if (s.pc) "/mnt/pc" else "not mounted", s.pc)
            }
        }
        item {
            SectionCard("Network and USB") {
                StatusLine("Wi-Fi", s.ip, s.ip != "?")
                StatusLine("VPN", s.vpn, s.vpn == "up")
                StatusLine(
                    "Zapret",
                    if (s.zapretRunning) "running · ${s.zapretMode}" else "stopped · ${s.zapretMode}",
                    s.zapretRunning
                )
                StatusLine("USB", s.udc, s.udc.isNotBlank() && s.udc != "?")
                StatusLine(
                    "Share",
                    s.share,
                    when (s.share) {
                        "local", "exported" -> true
                        "none", "pending" -> false
                        else -> null
                    }
                )
            }
        }
        item { InfoText("Use the refresh button in the top bar to update.") }
    }
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    when (ok) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
        )
        Text(
            label,
            modifier = Modifier.width(84.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = when (ok) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ZapretScreen(s: Status, act: Act) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("Zapret") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (s.zapretRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (s.zapretRunning) "running · ${s.zapretMode}" else "stopped · ${s.zapretMode}",
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("ON", { act("zapret on") { PhoneLab.zapret("on") } }, Modifier.weight(1f))
                    SecondaryButton("OFF", { act("zapret off") { PhoneLab.zapret("off") } }, Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = { act("zapret toggle") { PhoneLab.zapret("toggle") } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("TOGGLE") }
                InfoText("ON = vpn when tun0 is up, otherwise direct. Mode and state refresh automatically.")
            }
        }
    }
}

@Composable
fun PcScreen(
    prefs: android.content.SharedPreferences,
    s: Status,
    act: Act,
    onToast: (String) -> Unit
) {
    var host by remember { mutableStateOf(prefs.getString("host", "quanve@192.168.0.94") ?: "") }
    var port by remember { mutableStateOf(prefs.getString("port", "22") ?: "22") }
    var remote by remember { mutableStateOf(prefs.getString("remote", "") ?: "") }

    fun save() = prefs.edit().putString("host", host).putString("port", port)
        .putString("remote", remote).apply()

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("PC channel") {
                Text(
                    if (s.pc) "Mounted: /mnt/pc" else "Not mounted",
                    color = if (s.pc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace
                )
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text("user@host") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port, onValueChange = { port = it },
                        label = { Text("port") }, singleLine = true,
                        modifier = Modifier.width(110.dp)
                    )
                    OutlinedTextField(
                        value = remote, onValueChange = { remote = it },
                        label = { Text("remote path") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("MOUNT", {
                        save()
                        act("pcmount") { PhoneLab.mount(host, port, remote) }
                    }, Modifier.weight(1f))
                    SecondaryButton("UNMOUNT", {
                        act("pcumount") { PhoneLab.unmount() }
                    }, Modifier.weight(1f))
                }
                InfoText("The PC must run an SSH server and trust the phone key. Windows: flash/pc/windows/setup-sshd.ps1. adb is optional.")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HidScreen(act: Act, onToast: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val session = remember { HidSession() }
    var active by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var mods by remember { mutableIntStateOf(0) }
    var sens by remember { mutableFloatStateOf(1.3f) }
    var statusLine by remember { mutableStateOf("HID is off") }

    fun stopSession() {
        scope.launch {
            withContext(Dispatchers.IO) { session.stop() }
            active = false
            statusLine = "HID is off, adb restored"
        }
    }

    DisposableEffect(Unit) { onDispose { session.stop() } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP && session.isActive) {
                session.stop(); active = false; statusLine = "HID is off (app in background)"
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("USB HID") {
                Text(statusLine, fontFamily = FontFamily.Monospace,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if (starting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("enabling USB HID…")
                    }
                } else if (!active) {
                    PrimaryButton("ENABLE HID", {
                        starting = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { session.start() }
                            starting = false
                            active = ok
                            statusLine = if (ok) "HID active: adb disconnected" else "could not enable HID"
                            onToast(if (ok) "HID enabled" else "HID: error")
                        }
                    }, Modifier.fillMaxWidth())
                    InfoText("USB adb is disabled while HID is active (5–10 s after disabling). Enable HID after the phone is connected to the PC.")
                } else {
                    SecondaryButton("DISABLE HID", { stopSession() }, Modifier.fillMaxWidth())
                    InfoText("adb is disconnected. Disable HID to restore adb.")
                }
            }
        }
        if (active) {
            item {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("KEYBOARD") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("MOUSE") })
                }
            }
            if (tab == 0) {
                item { KeyboardPanel(session, text, { text = it }, mods, { mods = it }, onToast) }
            } else {
                item { MousePanel(session, sens, { sens = it }) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyboardPanel(
    session: HidSession, text: String, onText: (String) -> Unit,
    mods: Int, onMods: (Int) -> Unit, onToast: (String) -> Unit
) {
    var layout by remember { mutableStateOf("US") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("PC layout") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = layout == "US", onClick = { layout = "US" }, label = { Text("US") })
                FilterChip(selected = layout == "RU", onClick = { layout = "RU" }, label = { Text("RU") })
                SecondaryButton("ALT+SHIFT", { session.keyTap(Keys.ALT or Keys.SHIFT, 0); onToast("ALT+SHIFT sent") })
                SecondaryButton("WIN+SPACE", { session.keyTap(Keys.WIN, Keys.SPACE); onToast("WIN+SPACE sent") })
            }
            InfoText("HID sends scan codes: with a Russian PC layout, Latin text becomes Cyrillic. Switch the PC to US (Win+Space) or choose RU for Russian text.")
        }
        SectionCard("Text") {
            OutlinedTextField(
                value = text, onValueChange = onText,
                label = { Text("multiline text") },
                minLines = 5, maxLines = 12,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("SEND", {
                    session.text(text, layout)
                    onToast("sent ${text.length} chars")
                }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text("${text.length}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InfoText("Typed quickly (Python daemon, ~200–500 chars/s). Layout: US for Latin/symbols, RU for Cyrillic.")
        }
        SectionCard("Modifiers") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mods and Keys.CTRL != 0, onClick = { onMods(mods xor Keys.CTRL) }, label = { Text("CTRL") })
                FilterChip(selected = mods and Keys.SHIFT != 0, onClick = { onMods(mods xor Keys.SHIFT) }, label = { Text("SHIFT") })
                FilterChip(selected = mods and Keys.ALT != 0, onClick = { onMods(mods xor Keys.ALT) }, label = { Text("ALT") })
                FilterChip(selected = mods and Keys.WIN != 0, onClick = { onMods(mods xor Keys.WIN) }, label = { Text("WIN") })
            }
            InfoText("Tap a modifier to keep it selected, then tap a key or letter. Tap it again to release.")
        }
        SectionCard("Keys (tap or hold)") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HoldKey("ESC", mods, Keys.ESC, session)
                HoldKey("TAB", mods, Keys.TAB, session)
                HoldKey("CAPS", mods, Keys.CAPS, session)
                HoldKey("ENTER", mods, Keys.ENTER, session)
                HoldKey("BKSP", mods, Keys.BACKSPACE, session)
                HoldKey("DEL", mods, Keys.DELETE, session)
                HoldKey("INS", mods, Keys.INSERT, session)
                HoldKey("HOME", mods, Keys.HOME, session)
                HoldKey("END", mods, Keys.END, session)
                HoldKey("PGUP", mods, Keys.PAGE_UP, session)
                HoldKey("PGDN", mods, Keys.PAGE_DOWN, session)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HoldKey("←", mods, Keys.LEFT, session)
                HoldKey("↑", mods, Keys.UP, session)
                HoldKey("↓", mods, Keys.DOWN, session)
                HoldKey("→", mods, Keys.RIGHT, session)
                ToggleKey("WIN", Keys.WIN, mods, onMods)
                HoldKey("MENU", mods, Keys.MENU, session)
                HoldKey("PRINT", mods, Keys.PRINT, session)
                HoldKey("SCROLL", mods, Keys.SCROLL, session)
                HoldKey("PAUSE", mods, Keys.PAUSE, session)
                HoldKey("NUM", mods, Keys.NUM, session)
                ToggleKey("SHIFT", Keys.SHIFT, mods, onMods)
                ToggleKey("CTRL", Keys.CTRL, mods, onMods)
                ToggleKey("ALT", Keys.ALT, mods, onMods)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in 1..12) HoldKey("F$n", mods, Keys.f(n), session)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in 13..24) HoldKey("F$n", mods, Keys.f(n), session)
            }
        }
        SectionCard("Letters and numbers") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (c in 'A'..'Z') HoldKey(c.toString(), mods, Keys.of(c.lowercaseChar())!!.second, session)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (c in '0'..'9') HoldKey(c.toString(), mods, Keys.of(c)!!.second, session)
            }
            InfoText("Select a modifier above, then tap a letter or number. Example: WIN + R.")
        }
        SectionCard("Common combos") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SecondaryButton("WIN+R", { session.keyTap(Keys.WIN, 21) })
                SecondaryButton("WIN+E", { session.keyTap(Keys.WIN, 8) })
                SecondaryButton("ALT+TAB", { session.keyTap(Keys.ALT, Keys.TAB) })
                SecondaryButton("CTRL+C", { session.keyTap(Keys.CTRL, 6) })
                SecondaryButton("CTRL+V", { session.keyTap(Keys.CTRL, 25) })
                SecondaryButton("CTRL+Z", { session.keyTap(Keys.CTRL, 29) })
                SecondaryButton("CTRL+A", { session.keyTap(Keys.CTRL, 4) })
            }
        }
    }
}

@Composable
private fun HoldKey(label: String, mod: Int, code: Int, session: HidSession) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Box(
            Modifier
                .pointerInput(mod, code) {
                    detectTapGestures(onPress = {
                        session.keyDown(mod, code)
                        tryAwaitRelease()
                        session.keyUp()
                    })
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(label, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ToggleKey(label: String, bit: Int, mods: Int, onMods: (Int) -> Unit) {
    val selected = mods and bit != 0
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.clickable { onMods(mods xor bit) }
    ) {
        Box(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                label,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MousePanel(session: HidSession, sens: Float, onSens: (Float) -> Unit) {
    SectionCard("Touchpad") {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .systemGestureExclusion()
                .pointerInput(sens) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var last = down.position
                        var moved = false
                        var accX = 0f
                        var accY = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) { ch.consume(); break }
                            ch.consume()
                            accX += (ch.position.x - last.x) * sens
                            accY += (ch.position.y - last.y) * sens
                            last = ch.position
                            val ix = accX.toInt()
                            val iy = accY.toInt()
                            if (ix != 0 || iy != 0) {
                                accX -= ix; accY -= iy
                                moved = true
                                session.mouse(0, ix.coerceIn(-127, 127), iy.coerceIn(-127, 127), 0)
                            }
                        }
                        if (!moved) session.click(1)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("TOUCHPAD  ·  tap = LMB", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("LMB", { session.click(1) }, Modifier.weight(1f))
            SecondaryButton("RMB", { session.click(2) }, Modifier.weight(1f))
            SecondaryButton("MMB", { session.click(4) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("WHEEL ↑", { repeat(3) { session.mouse(0, 0, 0, 1) } }, Modifier.weight(1f))
            SecondaryButton("WHEEL ↓", { repeat(3) { session.mouse(0, 0, 0, -1) } }, Modifier.weight(1f))
        }
        Text("Sensitivity: ${"%.1f".format(sens)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = sens, onValueChange = onSens, valueRange = 0.3f..3f)
    }
}

@Composable
fun TerminalScreen(onToast: (String) -> Unit) {
    val ctx = LocalContext.current
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("Terminal") {
                PrimaryButton("OPEN TERMUX", { openTermux(ctx) }, Modifier.fillMaxWidth())
                SecondaryButton("COPY 'linux'", { copyClipboard(ctx, "linux"); onToast("copied: linux") }, Modifier.fillMaxWidth())
                SecondaryButton("COPY 'cd /mnt/pc && opencode'", {
                    copyClipboard(ctx, "cd /mnt/pc && opencode"); onToast("copied")
                }, Modifier.fillMaxWidth())
                InfoText("The opencode TUI lives in Termux/Debian. The app talks to it over the HTTP API on the Agent screen.")
            }
        }
    }
}

fun openTermux(ctx: Context) {
    try {
        val i = Intent().setClassName("com.termux", "com.termux.app.TermuxActivity")
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (_: Exception) {
        try {
            ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage("com.termux"))
        } catch (_: Exception) {
        }
    }
}

fun copyClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("phone-lab", text))
}

fun openUrl(ctx: Context, url: String) {
    try {
        val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (_: Exception) {

    }
}
