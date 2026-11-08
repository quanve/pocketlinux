package dev.quanve.phonelab

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REFRESH_THROTTLE_MS = 2_500L

enum class Screen(val title: String, val glyph: String) {
    STATUS("Status", "S"),
    AGENT("Agent", "A"),
    ZAPRET("Zapret", "Z"),
    HID("USB HID", "K"),
    PC("PC channel", "P"),
    TERMINAL("Terminal", ">_")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PhoneLabTheme { PhoneLabApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneLabApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("phone-lab", Context.MODE_PRIVATE) }
    var screen by rememberSaveable { mutableStateOf(Screen.STATUS) }
    var status by remember { mutableStateOf(Status()) }
    var busy by remember { mutableStateOf(false) }
    var lastRefresh by remember { mutableLongStateOf(0L) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    fun toast(msg: String) {
        scope.launch { snack.showSnackbar(msg) }
    }

    fun refresh(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastRefresh < REFRESH_THROTTLE_MS) return
        lastRefresh = now
        scope.launch { status = withContext(Dispatchers.IO) { PhoneLab.status() } }
    }

    fun act(name: String, block: () -> RootShell.Out) {
        scope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) { block() }
            busy = false
            toast(
                if (r.code == 0) "$name: ok"
                else "$name: ${r.text.lineSequence().lastOrNull { it.isNotBlank() } ?: "exit ${r.code}"}"
            )
            refresh(force = true)
        }
    }

    LaunchedEffect(Unit) { refresh(force = true) }
    LaunchedEffect(screen) { refresh() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        "phone-lab",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "RMX3834 · Debian · root",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                Screen.entries.forEach { sc ->
                    NavigationDrawerItem(
                        label = { Text(sc.title) },
                        selected = sc == screen,
                        onClick = {
                            screen = sc
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Text(
                                sc.glyph,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(screen.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "phone-lab · RMX3834",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { refresh(force = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            snackbarHost = { SnackbarHost(snack) }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (screen) {
                    Screen.STATUS -> StatusScreen(status, onRefresh = { refresh(force = true) })
                    Screen.AGENT -> AgentScreen(prefs, status, onToast = { toast(it) })
                    Screen.ZAPRET -> ZapretScreen(status) { n, b -> act(n, b) }
                    Screen.HID -> HidScreen({ n, b -> act(n, b) }, onToast = { toast(it) })
                    Screen.PC -> PcScreen(prefs, status, { n, b -> act(n, b) }, onToast = { toast(it) })
                    Screen.TERMINAL -> TerminalScreen(onToast = { toast(it) })
                }
                if (busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}
