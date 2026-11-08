package dev.quanve.phonelab

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentScreen(
    prefs: SharedPreferences,
    status: Status,
    onToast: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var serverOk by remember { mutableStateOf(false) }
    var serverLine by remember { mutableStateOf("checking server…") }
    var serverBusy by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf<List<OpencodeApi.Session>>(emptyList()) }
    var selectedId by remember { mutableStateOf(prefs.getString("a_session", null)) }
    var selected by remember { mutableStateOf<OpencodeApi.Session?>(null) }
    var messages by remember { mutableStateOf<List<OpencodeApi.Message>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var chatError by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<OpencodeApi.Model>>(emptyList()) }
    var showModels by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<OpencodeApi.Session?>(null) }
    var pinned by remember { mutableStateOf(prefs.getStringSet("a_pinned", emptySet()) ?: emptySet()) }
    var renameTarget by remember { mutableStateOf<OpencodeApi.Session?>(null) }
    var renameText by remember { mutableStateOf("") }


    var scenario by remember {
        mutableStateOf(
            runCatching {
                AgentScenario.valueOf(prefs.getString("a_scenario", "FREE") ?: "FREE")
            }.getOrDefault(AgentScenario.FREE)
        )
    }
    var workspace by remember { mutableStateOf(prefs.getString("a_workspace", "") ?: "") }
    var workspaceAuto by remember { mutableStateOf(prefs.getBoolean("a_ws_auto", true)) }
    var task by remember { mutableStateOf(prefs.getString("a_task", "") ?: "") }
    var problem by remember { mutableStateOf(prefs.getString("a_problem", "") ?: "") }
    var access by remember { mutableStateOf(prefs.getString("a_access", "AUTO") ?: "AUTO") }
    var template by remember {
        mutableStateOf(
            prefs.getString("a_tpl_${scenario.name}", null)
                ?: AgentPresets.template(scenario)
        )
    }
    var model by remember {
        mutableStateOf(prefs.getString("a_model", OpencodeApi.DEFAULT_MODEL) ?: OpencodeApi.DEFAULT_MODEL)
    }
    var auto by remember { mutableStateOf(prefs.getBoolean("a_auto", true)) }
    var builtPrompt by remember { mutableStateOf("") }

    fun defaultWs() = AgentPresets.defaultWorkspace(scenario, status.pc)

    fun save() {
        prefs.edit()
            .putString("a_scenario", scenario.name)
            .putString("a_workspace", workspace)
            .putBoolean("a_ws_auto", workspaceAuto)
            .putString("a_task", task)
            .putString("a_problem", problem)
            .putString("a_access", access)
            .putString("a_model", model)
            .putBoolean("a_auto", auto)
            .putString("a_tpl_${scenario.name}", template)
            .apply()
    }

    fun buildPromptNow(): String = AgentPresets.render(
        template,
        AgentPresets.values(
            workspace = workspace.ifBlank { defaultWs() },
            task = task,
            problem = problem,
            access = access
        )
    )

    fun select(id: String, s: OpencodeApi.Session? = null) {
        selectedId = id
        selected = s
        messages = emptyList()
        chatError = ""
        prefs.edit().putString("a_session", id).apply()
    }

    suspend fun loadSessionsNow() {
        try {
            sessions = withContext(Dispatchers.IO) { OpencodeApi.sessions() }
        } catch (e: Exception) {
            onToast("sessions: ${e.message}")
        }
    }

    fun reloadSessions() {
        scope.launch {
            loading = true
            loadSessionsNow()
            loading = false
        }
    }

    fun ensureServer(autoStart: Boolean) {
        scope.launch {
            serverBusy = true
            val st = if (autoStart) {
                withContext(Dispatchers.IO) { OpencodeApi.ensureServer() }
            } else {
                val i = withContext(Dispatchers.IO) { OpencodeApi.info() }
                if (i != null) OpencodeApi.ServerState(true, "opencode ${i.version} · pid ${i.pid}")
                else OpencodeApi.ServerState(false, "server is not running")
            }
            serverOk = st.ok
            serverLine = st.text
            if (st.ok) {
                loadSessionsNow()
                selectedId?.let { id ->
                    selected = withContext(Dispatchers.IO) {
                        runCatching { OpencodeApi.session(id) }.getOrNull()
                    }
                }
            }
            serverBusy = false
        }
    }

    fun startServer() {
        scope.launch {
            serverBusy = true
            val st = withContext(Dispatchers.IO) { OpencodeApi.startServer() }
            serverOk = st.ok
            serverLine = st.text
            if (st.ok) loadSessionsNow()
            serverBusy = false
        }
    }

    fun stopServer() {
        scope.launch {
            serverBusy = true
            val st = withContext(Dispatchers.IO) { OpencodeApi.stopServer() }
            serverOk = false
            serverLine = st.text
            serverBusy = false
        }
    }

    fun createAndPrompt(title: String, promptText: String?, directory: String) {
        val dir = directory.trim()
        if (dir.startsWith("/mnt/pc") && !status.pc) {
            onToast("PC is not mounted: connect the PC channel or choose a local folder")
            return
        }
        scope.launch {
            loading = true
            try {
                val s = withContext(Dispatchers.IO) {
                    OpencodeApi.ensureWorkspace(dir)
                    OpencodeApi.createSession(title, dir, model, auto)
                }
                sessions = withContext(Dispatchers.IO) { OpencodeApi.sessions() }
                select(s.id, s)
                if (!promptText.isNullOrBlank()) {
                    sending = true
                    try {
                        withContext(Dispatchers.IO) { OpencodeApi.prompt(s.id, promptText) }
                    } finally {
                        sending = false
                    }
                }
                tab = 0
                onToast("session created")
            } catch (e: Exception) {
                onToast("create: ${e.message}")
            } finally {
                loading = false
            }
        }
    }

    fun sendPrompt(text: String) {
        val id = selectedId ?: run {
            onToast("select or create a session first")
            return
        }
        if (text.isBlank()) return
        scope.launch {
            sending = true
            try {
                withContext(Dispatchers.IO) { OpencodeApi.prompt(id, text) }
                messages = withContext(Dispatchers.IO) { OpencodeApi.messages(id) }
                chatError = ""
            } catch (e: Exception) {
                onToast("send: ${e.message}")
                chatError = e.message ?: "send failed"
            } finally {
                sending = false
            }
        }
    }

    fun sendInput() {
        val text = input.trim()
        if (text.isBlank()) return
        if (selectedId == null) {
            onToast("select or create a session first")
            return
        }
        val id = selectedId!!
        scope.launch {
            sending = true
            try {
                withContext(Dispatchers.IO) { OpencodeApi.prompt(id, text) }
                input = ""
                messages = withContext(Dispatchers.IO) { OpencodeApi.messages(id) }
                chatError = ""
            } catch (e: Exception) {
                onToast("send: ${e.message}")
                chatError = e.message ?: "send failed"
            } finally {
                sending = false
            }
        }
    }

    fun interrupt() {
        val id = selectedId ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { OpencodeApi.interrupt(id) }
                onToast("stopped")
            } catch (e: Exception) {
                onToast("interrupt: ${e.message}")
            }
        }
    }

    fun deleteSession(s: OpencodeApi.Session) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { OpencodeApi.deleteSession(s.id) }
                if (selectedId == s.id) {
                    selectedId = null
                    selected = null
                    messages = emptyList()
                    prefs.edit().remove("a_session").apply()
                }
                sessions = withContext(Dispatchers.IO) { OpencodeApi.sessions() }
                onToast("session deleted")
            } catch (e: Exception) {
                onToast("delete: ${e.message}")
            }
        }
    }

    fun loadModels() {
        scope.launch {
            try {
                models = withContext(Dispatchers.IO) {
                    OpencodeApi.models(workspace.ifBlank { defaultWs() })
                }
                showModels = true
                if (models.isEmpty()) onToast("no models received")
            } catch (e: Exception) {
                onToast("models: ${e.message}")
            }
        }
    }

    fun togglePin(id: String) {
        val next = if (id in pinned) pinned - id else pinned + id
        pinned = next
        prefs.edit().putStringSet("a_pinned", next).apply()
    }

    fun renameSessionNow(s: OpencodeApi.Session, title: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { OpencodeApi.renameSession(s.id, title) }
                sessions = withContext(Dispatchers.IO) { OpencodeApi.sessions() }
                if (selectedId == s.id) {
                    selected = withContext(Dispatchers.IO) {
                        runCatching { OpencodeApi.session(s.id) }.getOrNull()
                    }
                }
                onToast("session renamed")
            } catch (e: Exception) {
                onToast("rename: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) { ensureServer(true) }



    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { PcBridge.start() } }
    }

    LaunchedEffect(scenario) {
        template = prefs.getString("a_tpl_${scenario.name}", null)
            ?: AgentPresets.template(scenario)
    }

    LaunchedEffect(scenario, status.pc) {
        if (workspaceAuto) workspace = defaultWs()
    }


    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        while (true) {
            try {
                val pair = withContext(Dispatchers.IO) {
                    OpencodeApi.messages(id) to
                        runCatching { OpencodeApi.session(id) }.getOrNull()
                }
                messages = pair.first
                pair.second?.let { selected = it }
                chatError = ""
            } catch (e: Exception) {
                chatError = e.message ?: "error"
            }
            val working = sending || selected?.busy == true
            delay(if (working) 1_500 else 5_000)
        }
    }

    val busy = selected?.busy == true || sending
    val orderedSessions = sessions.sortedWith(
        compareByDescending<OpencodeApi.Session> { it.id in pinned }
            .thenByDescending { it.updated }
    )

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("CHAT", "TASK", "SESSIONS", "SHARE", "PC").forEachIndexed { i, label ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { tab = i },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        when (tab) {

            0 -> {
                if (selectedId == null) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionCard("Chat") {
                            Text("No session selected.")
                            InfoText("Select a session in SESSIONS or create one in TASK.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton("SESSIONS", { tab = 2 }, Modifier.weight(1f))
                                SecondaryButton("TASK", { tab = 1 }, Modifier.weight(1f))
                            }
                        }
                        if (chatError.isNotBlank()) {
                            SectionCard("Server") {
                                Text(chatError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                SecondaryButton("CHECK SERVER", { ensureServer(true) }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    var follow by remember { mutableStateOf(true) }
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
                            .collect { (scrolling, canForward) ->
                                if (!scrolling && !canForward) follow = true
                                else if (scrolling && canForward) follow = false
                            }
                    }
                    LaunchedEffect(messages) {
                        if (follow && messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
                    }
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        SessionHeader(
                            s = selected,
                            id = selectedId!!,
                            busy = busy,
                            pinned = selectedId in pinned,
                            onInterrupt = { interrupt() },
                            onPin = { togglePin(selectedId!!) },
                            onRename = {
                                val cur = selected ?: sessions.firstOrNull { it.id == selectedId }
                                if (cur != null) {
                                    renameTarget = cur
                                    renameText = cur.title
                                }
                            },
                            onDelete = {
                                val cur = selected ?: sessions.firstOrNull { it.id == selectedId }
                                if (cur != null) confirmDelete = cur
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            if (messages.isEmpty()) {
                                item { InfoText("No messages yet. Write the first one.") }
                            }
                            items(messages, key = { it.id }) { m -> MessageCard(m) }
                            item { Spacer(Modifier.height(4.dp)) }
                        }
                        if (chatError.isNotBlank()) {
                            Text(
                                chatError,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Row(
                                Modifier.padding(8.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Message the agent…") },
                                    maxLines = 5
                                )
                                if (busy) {
                                    SecondaryButton("STOP", { interrupt() })
                                } else {
                                    PrimaryButton("SEND", { sendInput() })
                                }
                            }
                        }
                    }
                }
            }


            1 -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SectionCard("Scenario") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AgentScenario.entries.forEach { sc ->
                                    FilterChip(
                                        selected = scenario == sc,
                                        onClick = {
                                            save()
                                            scenario = sc
                                            if (workspaceAuto) workspace =
                                                AgentPresets.defaultWorkspace(sc, status.pc)
                                        },
                                        label = { Text(sc.label) }
                                    )
                                }
                            }
                            Text(
                                if (status.pc) "PC mounted: /mnt/pc" else "PC not mounted; only the local workspace is available",
                                color = if (status.pc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                            when (scenario) {
                                AgentScenario.HOME_LINUX, AgentScenario.HOME_WINDOWS -> {
                                    OutlinedTextField(
                                        problem, { problem = it },
                                        label = { Text("what should be done on the PC") }, minLines = 3,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("Access", style = MaterialTheme.typography.labelMedium)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(selected = access == "AUTO", onClick = { access = "AUTO" }, label = { Text("AUTO") })
                                        FilterChip(selected = access == "HID", onClick = { access = "HID" }, label = { Text("HID") })
                                        FilterChip(selected = access == "SSH", onClick = { access = "SSH" }, label = { Text("SSH") })
                                    }
                                    InfoText(
                                        "HID types on the PC; SSH exposes PC files through /mnt/pc. " +
                                            "Available now: HID (USB HID tab) and the PC channel (PC tab)."
                                    )
                                }
                                AgentScenario.FREE -> {
                                    OutlinedTextField(
                                        task, { task = it },
                                        label = { Text("task") }, minLines = 4,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            OutlinedTextField(
                                workspace, {
                                    workspace = it
                                    workspaceAuto = false
                                },
                                label = { Text("workspace (absolute path)") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            SecondaryButton("AUTO PATH", {
                                workspaceAuto = true
                                workspace = defaultWs()
                                onToast(workspace)
                            }, Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        SectionCard("Prompt template") {
                            OutlinedTextField(
                                template, {
                                    template = it
                                    prefs.edit().putString("a_tpl_${scenario.name}", it).apply()
                                },
                                minLines = 8,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace, fontSize = 11.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            InfoText(
                                "Placeholders: {{workspace}} {{task}} {{problem}} {{access}} {{date}}"
                            )
                        }
                    }
                    item {
                        SectionCard("Model and launch") {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        model, { model = it },
                                        label = { Text("provider/model") }, singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SecondaryButton("MODELS", { loadModels() })
                                }
                                DropdownMenu(
                                    expanded = showModels,
                                    onDismissRequest = { showModels = false }
                                ) {
                                    models.filter { it.enabled }.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text("${m.ref} · ${m.name}", fontSize = 12.sp) },
                                            onClick = {
                                                model = m.ref
                                                showModels = false
                                            }
                                        )
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = auto,
                                    onClick = { auto = !auto },
                                    label = { Text("AUTO") }
                                )
                                InfoText("AUTO = allow tools without confirmation")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryButton("BUILD", {
                                    save()
                                    builtPrompt = buildPromptNow()
                                    onToast("prompt built")
                                }, Modifier.weight(1f))
                                SecondaryButton("SAVE", {
                                    save()
                                    onToast("settings saved")
                                }, Modifier.weight(1f))
                            }
                            OutlinedTextField(
                                builtPrompt, { builtPrompt = it },
                                label = { Text("final prompt (editable)") },
                                minLines = 5,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace, fontSize = 11.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            PrimaryButton("NEW SESSION + RUN", {
                                save()
                                val dir = workspace.ifBlank { defaultWs() }
                                workspace = dir
                                val p = builtPrompt.ifBlank { buildPromptNow() }
                                builtPrompt = p
                                createAndPrompt(
                                    AgentPresets.title(scenario, problem, task),
                                    p,
                                    dir
                                )
                            }, Modifier.fillMaxWidth())
                            if (selectedId != null) {
                                SecondaryButton("ADD TO CURRENT SESSION", {
                                    save()
                                    val p = builtPrompt.ifBlank { buildPromptNow() }
                                    builtPrompt = p
                                    sendPrompt(p)
                                    tab = 0
                                }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }


            2 -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SectionCard("Server opencode") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (serverOk) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                        )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    serverLine,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (serverBusy) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("starting…", fontSize = 12.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton("START", { startServer() }, Modifier.weight(1f))
                                SecondaryButton("STOP", { stopServer() }, Modifier.weight(1f))
                                SecondaryButton("REFR", { ensureServer(false) }, Modifier.weight(1f))
                            }
                            InfoText("127.0.0.1:4096 · basic auth · password in ${OpencodeApi.PW_FILE}")
                        }
                    }
                    item {
                        SectionCard("New session") {
                            val dir = workspace.ifBlank { defaultWs() }
                            Text(dir, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton("NEW SESSION", {
                                    save()
                                    createAndPrompt("New session", null, dir)
                                }, Modifier.weight(1f))
                                SecondaryButton("TASK", { tab = 1 }, Modifier.weight(1f))
                            }
                            InfoText("An empty session in the current workspace. Send the task from CHAT.")
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "SESSIONS (${sessions.size})",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { reloadSessions() }) { Text("REFRESH") }
                        }
                        if (loading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    if (sessions.isEmpty() && !loading) {
                        item {
                            InfoText("No sessions. Create one or start the server.")
                        }
                    }
                    items(orderedSessions, key = { it.id }) { s ->
                        SessionRow(
                            s = s,
                            selected = s.id == selectedId,
                            pinned = s.id in pinned,
                            onClick = {
                                select(s.id, s)
                                tab = 0
                            },
                            onPin = { togglePin(s.id) },
                            onDelete = { confirmDelete = s }
                        )
                    }
                }
            }


            3 -> SharePanel(
                prefs = prefs,
                workspace = workspace.ifBlank { defaultWs() },
                onToast = onToast
            )


            else -> PcPanel(prefs = prefs, onToast = onToast)
        }
    }

    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete session?") },
            text = {
                Text(
                    "${s.title}\n\n${s.id}\n\n" +
                        "Files in the workspace will remain. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteSession(s)
                    confirmDelete = null
                }) { Text("DELETE", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("CANCEL") }
            }
        )
    }

    renameTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename session") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = renameText.trim()
                    if (t.isNotBlank()) renameSessionNow(s, t)
                    renameTarget = null
                }) { Text("SAVE") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
private fun SessionHeader(
    s: OpencodeApi.Session?,
    id: String,
    busy: Boolean,
    pinned: Boolean,
    onInterrupt: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (busy) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (pinned) "★ " else "") + (s?.title ?: id),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (busy) "busy" else "idle",
                    fontSize = 10.sp,
                    color = if (busy) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${s?.directory ?: "?"} · ${s?.modelShort ?: "?"}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPin) { Text(if (pinned) "UNPIN" else "PIN", fontSize = 11.sp) }
                TextButton(onClick = onRename) { Text("RENAME", fontSize = 11.sp) }
                if (busy) TextButton(onClick = onInterrupt) { Text("STOP", fontSize = 11.sp) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text("DEL", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MessageCard(m: OpencodeApi.Message) {
    when (m.type) {
        "user" -> Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("YOU", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                SelectionContainer {
                    Text(m.text, fontSize = 13.sp)
                }
            }
        }

        "assistant" -> Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AGENT", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        m.model.substringAfter('/'),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (m.parts.isEmpty() && !m.completed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("thinking…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                m.parts.forEach { PartView(it) }
                if (m.error.isNotBlank()) {
                    Text("error: ${m.error}", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        }

        "idle" -> {
            val ok = m.outcome == "succeeded"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ok) "✓ done" else "■ ${m.outcome.ifBlank { "finished" }}",
                    fontSize = 10.sp,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                if (m.error.isNotBlank()) {
                    Text(
                        " · ${m.error}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        "shell" -> Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SelectionContainer {
                    Text(
                        "$ ${m.command}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
                if (m.shellOutput.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            m.shellOutput.take(20000),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }

        "synthetic", "system" -> InfoText(m.text)
        else -> Unit
    }
}

@Composable
private fun PartView(p: OpencodeApi.Part) {
    when (p.type) {
        "text" -> SelectionContainer {
            Text(p.text, fontSize = 13.sp)
        }

        "reasoning" -> {
            var open by remember { mutableStateOf(false) }
            Column {
                Text(
                    if (open) "▾ reasoning" else "▸ reasoning",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { open = !open }
                )
                if (open) {
                    Text(
                        p.text,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        "tool" -> {
            var open by remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .clickable { open = !open }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "⚙ ${p.name.ifBlank { "tool" }}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            p.status,
                            fontSize = 9.sp,
                            color = when (p.status) {
                                "completed" -> MaterialTheme.colorScheme.primary
                                "error" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (p.detail.isNotBlank()) {
                        Text(
                            p.detail,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (open) 20 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (p.error.isNotBlank()) {
                        Text(p.error, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                    if (open && p.text.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                p.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    s: OpencodeApi.Session,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    (if (pinned) "★ " else "") + s.title,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    s.directory,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${s.id} · ${OpencodeApi.formatTime(s.updated)} · ${s.modelShort}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (s.busy) "running" else "idle",
                    fontSize = 9.sp,
                    color = if (s.busy) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPin) {
                        Text(if (pinned) "★" else "☆", fontSize = 14.sp)
                    }
                    TextButton(onClick = onDelete) {
                        Text("DEL", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
