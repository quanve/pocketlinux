package dev.quanve.phonelab

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FallbackDark = darkColorScheme(
    primary = Color(0xFF9BD7B0),
    onPrimary = Color(0xFF062015),
    secondary = Color(0xFFB8CCBD),
    tertiary = Color(0xFFA9C8D8),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE2E4DE),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE2E4DE),
    surfaceVariant = Color(0xFF242924),
    onSurfaceVariant = Color(0xFFBFC9C0),
    error = Color(0xFFFFB4AB)
)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF2F6B47),
    secondary = Color(0xFF4E6355),
    tertiary = Color(0xFF3D6373),
    background = Color(0xFFF7FAF5),
    surface = Color(0xFFF7FAF5),
    surfaceVariant = Color(0xFFE0E8E0),
    onSurfaceVariant = Color(0xFF434A44)
)

@Composable
fun PhoneLabTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) FallbackDark else FallbackLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
