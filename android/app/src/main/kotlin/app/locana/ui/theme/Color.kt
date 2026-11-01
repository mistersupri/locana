package app.locana.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Palette (why): navy is the calm, trustworthy base for a product that handles location;
 * teal is the single accent and always means "location / live / act here"; amber appears
 * only for XP and rewards so game value never competes with safety signals.
 * Contrast pairs were checked for WCAG AA (body text >= 4.5:1).
 */
object Palette {
    val Navy950 = Color(0xFF0D1522)
    val Navy900 = Color(0xFF142033)
    val Navy850 = Color(0xFF1B2A40)
    val Navy800 = Color(0xFF22344D)
    val Navy700 = Color(0xFF33465F)
    val Mist100 = Color(0xFFE8EEF5)
    val Mist300 = Color(0xFFA9B6C6)
    val Teal300 = Color(0xFF3CC6B4)
    val Teal900 = Color(0xFF11433F)
    val Teal100 = Color(0xFFA8F0E5)
    val Teal700 = Color(0xFF0B7268)
    val Teal50 = Color(0xFFCDEFEA)
    val Paper = Color(0xFFF5F7F6)
    val Ink = Color(0xFF0F1B2B)
    val Slate600 = Color(0xFF4A5868)
    val Amber400 = Color(0xFFF2B544)
    val Amber800 = Color(0xFF8A5600)
    val Amber100 = Color(0xFFFBE7BF)
}

val DarkColors = darkColorScheme(
    primary = Palette.Teal300,
    onPrimary = Color(0xFF04201C),
    primaryContainer = Palette.Teal900,
    onPrimaryContainer = Palette.Teal100,
    secondary = Palette.Mist300,
    onSecondary = Palette.Navy950,
    secondaryContainer = Palette.Navy800,
    onSecondaryContainer = Palette.Mist100,
    background = Palette.Navy950,
    onBackground = Palette.Mist100,
    surface = Palette.Navy950,
    onSurface = Palette.Mist100,
    surfaceVariant = Palette.Navy850,
    onSurfaceVariant = Palette.Mist300,
    surfaceContainerLowest = Color(0xFF0A111C),
    surfaceContainerLow = Palette.Navy900,
    surfaceContainer = Palette.Navy900,
    surfaceContainerHigh = Palette.Navy850,
    surfaceContainerHighest = Palette.Navy800,
    outline = Color(0xFF667B95),
    outlineVariant = Color(0xFF26364C),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD5),
)

val LightColors = lightColorScheme(
    primary = Palette.Teal700,
    onPrimary = Color.White,
    primaryContainer = Palette.Teal50,
    onPrimaryContainer = Color(0xFF00302B),
    secondary = Palette.Slate600,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E7EC),
    onSecondaryContainer = Palette.Ink,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFE7ECEF),
    onSurfaceVariant = Palette.Slate600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF2F3),
    surfaceContainerHighest = Color(0xFFE5EAEC),
    outline = Color(0xFF6B7988),
    outlineVariant = Color(0xFFD5DCE1),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/** Reward colors live outside Material roles so they cannot leak into ordinary UI. */
@Immutable
data class RewardColors(val reward: Color, val onRewardContainer: Color, val rewardContainer: Color)

val DarkReward = RewardColors(reward = Palette.Amber400, onRewardContainer = Palette.Amber100, rewardContainer = Color(0xFF3D2A06))
val LightReward = RewardColors(reward = Palette.Amber800, onRewardContainer = Color(0xFF4A2E00), rewardContainer = Palette.Amber100)

/** Avatar backgrounds keyed by the server's avatarKey. Initials sit on them in white or ink. */
val AvatarColors = mapOf(
    "explorer_teal" to (Color(0xFF0B7268) to Color.White),
    "explorer_amber" to (Color(0xFF8F5C00) to Color.White),
    "explorer_slate" to (Color(0xFF4A5868) to Color.White),
    "explorer_coral" to (Color(0xFFB4483C) to Color.White),
    "explorer_moss" to (Color(0xFF4F6B2E) to Color.White),
    "explorer_plum" to (Color(0xFF6E4A7E) to Color.White),
)

/*
 * "Night map" hero panels (why): each main tab has one navy panel that holds its focal point
 * (level, today's quests, the circle, you). Navy in both themes keeps the brand voice and makes
 * the panel read as the map at night, the place the game happens. Text on it is checked for AA:
 * Mist100 on Navy950 15.7:1, Mist300 8.9:1, Teal300 8.7:1, Amber400 10:1; on Navy850 the same
 * pairs stay above 6:1.
 */
@Immutable
data class HeroColors(
    val background: Color,
    val raised: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val reward: Color,
    val tileHidden: Color,
)

val LightHero = HeroColors(Palette.Navy950, Palette.Navy850, Palette.Mist100, Palette.Mist300, Palette.Teal300, Palette.Amber400, Color(0xFF3A5175))
val DarkHero = HeroColors(Palette.Navy850, Palette.Navy800, Palette.Mist100, Palette.Mist300, Palette.Teal300, Palette.Amber400, Color(0xFF4A6085))
