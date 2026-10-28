package app.locana.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.locana.data.prefs.ThemeMode

/*
 * Shapes (why): radius encodes hierarchy. Small 8 dp for chips and inputs, medium 14 dp for
 * cards, large 24 dp for sheets. Nothing is pill-shaped except status chips, which are labels.
 */
val LocanaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalReward = staticCompositionLocalOf { DarkReward }
val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalDarkTheme = staticCompositionLocalOf { true }

@Composable
fun LocanaTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    // Honors "Remove animations" in system accessibility settings.
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalReward provides if (dark) DarkReward else LightReward,
        LocalReducedMotion provides reduced,
        LocalDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = LocanaTypography,
            shapes = LocanaShapes,
            content = content,
        )
    }
}

object LocanaThemeExt {
    val reward: RewardColors
        @Composable @ReadOnlyComposable get() = LocalReward.current
}
