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
 * Shapes (why): from the mockup. Chunky buttons are 18 dp, cards 20 to 24 dp, sheets 28 dp, chips and
 * badges are pills.
 */
val LocanaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalReward = staticCompositionLocalOf { DarkReward }
val LocalHero = staticCompositionLocalOf { LightHero }
val LocalTokens = staticCompositionLocalOf { LightTokens }
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
    // The system bars follow the app's theme, not the phone's: dark icons on a light app, light icons on a dark one.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            var c: android.content.Context = view.context
            while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
            (c as? android.app.Activity)?.window?.let { w ->
                val controller = androidx.core.view.WindowCompat.getInsetsController(w, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        LocalReward provides if (dark) DarkReward else LightReward,
        LocalHero provides if (dark) DarkHero else LightHero,
        LocalTokens provides if (dark) DarkTokens else LightTokens,
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
    val tokens: Tokens
        @Composable @ReadOnlyComposable get() = LocalTokens.current
    val reward: RewardColors
        @Composable @ReadOnlyComposable get() = LocalReward.current
}
