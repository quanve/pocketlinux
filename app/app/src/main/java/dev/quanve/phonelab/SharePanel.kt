package dev.quanve.phonelab

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SharePanel(
    prefs: SharedPreferences,
    workspace: String,
    onToast: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("checking…") }
    var dir by remember {
        mutableStateOf(prefs.getString("a_share_dir", workspace) ?: workspace)
    }
    var sizeMb by remember {
        mutableStateOf(prefs.getString("a_share_size", "4096") ?: "4096")
    }

    fun scriptPath(): String {
        val installed = "/data/linux/bin/usb-share.sh"
        val tmp = "/data/local/tmp/usb-share.sh"
        return if (java.io.File(installed).exists()) installed else tmp
    }


    fun hostDir(p: String): String {
        val t = p.trim().trimEnd('/')
        if (t.isEmpty()) return "/data/linux/debian/root/work"
        if (t.startsWith("/data/linux/debian/")) return t
        if (t.startsWith("/data/")) return t
        return "/data/linux/debian$t"
    }

    fun run(vararg args: String) {
        if (busy) return
        scope.launch {
            busy = true
            val cmd = (listOf("sh", scriptPath()) + args).joinToString(" ") { shq(it) }
            val out = withContext(Dispatchers.IO) { RootShell.run(cmd, 240) }
            statusText = out.text.ifBlank { "exit ${out.code}" }
            onToast(out.text.lineSequence().lastOrNull { it.isNotBlank() } ?: "done")
            busy = false
        }
    }

    fun refresh() {
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                RootShell.run("sh ${shq(scriptPath())} status", 30)
            }
            statusText = out.text.ifBlank { "exit ${out.code}" }
        }
    }

    LaunchedEffect(Unit) {
        dir = prefs.getString("a_share_dir", workspace) ?: workspace
        refresh()
        while (true) {
            delay(5_000)
            if (!busy) refresh()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionCard("USB share (flash drive)") {
            Text(
                statusText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            if (statusText.contains("state:  exported")) {
                Text(
                    "The drive is on the PC: file transfer (MTP) and adb are disabled. " +
                        "After copying, press IMPORT to restore normal USB mode.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("REFRESH", { refresh() }, Modifier.weight(1f))
                PrimaryButton("IMPORT", { run("import") }, Modifier.weight(1f))
            }
            InfoText(
                "IMPORT takes the drive back from the PC, restores file transfer (MTP) and adb, " +
                    "and mounts the image for the agent again."
            )
        }

        SectionCard("Exchange folder") {
            OutlinedTextField(
                dir, {
                    dir = it
                    prefs.edit().putString("a_share_dir", it).apply()
                },
                label = { Text("agent file folder") },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("SYNC", { run("sync", hostDir(dir)) }, Modifier.weight(1f))
                PrimaryButton("EXPORT", { run("export", hostDir(dir)) }, Modifier.weight(1f))
            }
            InfoText(
                "SYNC copies the folder into the image. EXPORT copies it and connects the phone " +
                    "to the PC as a normal flash drive (MTP and adb are disabled meanwhile)."
            )
        }

        SectionCard("Image") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    sizeMb, {
                        sizeMb = it.filter { c -> c.isDigit() }.take(5)
                        prefs.edit().putString("a_share_size", sizeMb).apply()
                    },
                    label = { Text("size, MB") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton("INIT", {
                    run("init", sizeMb.ifBlank { "4096" })
                }, Modifier.weight(1f))
            }
            InfoText(
                "FAT32, label PHONELAB. Image file: /data/linux/share.img. " +
                    "While the drive is on the PC, the agent must not write to the exchange folder; " +
                    "press IMPORT after copying."
            )
        }
    }
}
