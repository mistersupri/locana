package app.locana.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Palette (why): taken from the owner's mockup (locana.pen). A warm cream page, white cards and one
 * saturated violet for everything you can act on. Yellow marks XP and rewards, mint marks "good / live",
 * coral marks danger and alerts, sky marks information. Every text pair was checked for WCAG AA:
 * ink on cream 15.7:1, ink-2 on white 6.6:1, white on primary 6.2:1, primary on white 6.2:1, #8A5A00 on
 * yellow-soft 5.4:1. ink-3 is for decoration and placeholders only.
 */
object Palette {
    val Cream = Color(0xFFFBF6EE)
    val Ink = Color(0xFF1F1645)
    val Ink2 = Color(0xFF5F587F)
    val Ink3 = Color(0xFF9C97B6)
    val Line = Color(0xFFE9E3F4)
    val Violet700 = Color(0xFF6B3BF5)
    val Violet900 = Color(0xFF4620C4)
    val Violet100 = Color(0xFFECE4FF)
    val Violet50 = Color(0xFFF4EFFF)
    val Violet300 = Color(0xFFB9A2FF)
    val Yellow = Color(0xFFFFC93C)
    val YellowDark = Color(0xFFD99A0B)
    val YellowSoft = Color(0xFFFFF1C9)
    val Mint = Color(0xFF22C489)
    val MintSoft = Color(0xFFD8F6E9)
    val Coral = Color(0xFFFF6550)
    val CoralDark = Color(0xFFD13E2B)
    val CoralSoft = Color(0xFFFFE2DC)
    val Sky = Color(0xFF45AFFF)
    val SkySoft = Color(0xFFDCF0FF)

    // Dark theme surfaces: the same hues, deep and a little violet.
    val Night950 = Color(0xFF100B22)
    val Night900 = Color(0xFF181133)
    val Night850 = Color(0xFF211946)
    val Night800 = Color(0xFF2B2158)
    val Mist100 = Color(0xFFEDE8FB)
    val Mist300 = Color(0xFFB3ABD1)

    // Kept for call sites that still name them.
    val Paper = Cream
    val Slate600 = Ink2
    val InkChip = Ink
    val Amber400 = Yellow
    val Amber800 = Color(0xFF8A5A00)
    val Amber100 = YellowSoft
}

val DarkColors = darkColorScheme(
    primary = Palette.Violet300,
    onPrimary = Color(0xFF1A0B45),
    primaryContainer = Color(0xFF3A2A7A),
    onPrimaryContainer = Palette.Violet100,
    secondary = Palette.Mist300,
    onSecondary = Palette.Night950,
    secondaryContainer = Palette.Night800,
    onSecondaryContainer = Palette.Mist100,
    tertiary = Palette.Yellow,
    onTertiary = Color(0xFF3A2800),
    tertiaryContainer = Color(0xFF4A3500),
    onTertiaryContainer = Palette.YellowSoft,
    background = Palette.Night950,
    onBackground = Palette.Mist100,
    surface = Palette.Night950,
    onSurface = Palette.Mist100,
    surfaceVariant = Palette.Night850,
    onSurfaceVariant = Palette.Mist300,
    surfaceContainerLowest = Palette.Night900,
    surfaceContainerLow = Palette.Night900,
    surfaceContainer = Palette.Night900,
    surfaceContainerHigh = Palette.Night850,
    surfaceContainerHighest = Palette.Night800,
    outline = Color(0xFF7C73A6),
    outlineVariant = Color(0xFF332A5E),
    error = Color(0xFFFF8A7A),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD5),
)

val LightColors = lightColorScheme(
    primary = Palette.Violet700,
    onPrimary = Color.White,
    primaryContainer = Palette.Violet100,
    onPrimaryContainer = Palette.Violet900,
    secondary = Palette.Ink2,
    onSecondary = Color.White,
    secondaryContainer = Palette.Violet50,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.YellowDark,
    onTertiary = Color.White,
    tertiaryContainer = Palette.YellowSoft,
    onTertiaryContainer = Palette.Amber800,
    background = Palette.Cream,
    onBackground = Palette.Ink,
    surface = Palette.Cream,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Violet50,
    onSurfaceVariant = Palette.Ink2,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Palette.Violet50,
    surfaceContainerHighest = Palette.Violet100,
    outline = Palette.Ink3,
    outlineVariant = Palette.Line,
    error = Palette.CoralDark,
    onError = Color.White,
    errorContainer = Palette.CoralSoft,
    onErrorContainer = Color(0xFF7A1D10),
)

/** The mockup's accent hues, outside Material roles so each keeps one job. */
@Immutable
data class Tokens(
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val card: Color,
    val primaryDark: Color,
    val yellow: Color,
    val yellowDark: Color,
    val yellowSoft: Color,
    val onYellowSoft: Color,
    val mint: Color,
    val mintSoft: Color,
    val coral: Color,
    val coralDark: Color,
    val coralSoft: Color,
    val sky: Color,
    val skySoft: Color,
    /** Fill under the chunky 3D buttons' bottom edge for secondary buttons. */
    val edge: Color,
)

val LightTokens = Tokens(
    ink2 = Palette.Ink2, ink3 = Palette.Ink3, line = Palette.Line, card = Color.White, primaryDark = Palette.Violet900,
    yellow = Palette.Yellow, yellowDark = Palette.YellowDark, yellowSoft = Palette.YellowSoft, onYellowSoft = Palette.Amber800,
    mint = Palette.Mint, mintSoft = Palette.MintSoft, coral = Palette.Coral, coralDark = Palette.CoralDark, coralSoft = Palette.CoralSoft,
    sky = Palette.Sky, skySoft = Palette.SkySoft, edge = Color(0xFFE1D6FA),
)

val DarkTokens = Tokens(
    ink2 = Palette.Mist300, ink3 = Color(0xFF8A82B0), line = Color(0xFF332A5E), card = Palette.Night900, primaryDark = Color(0xFF7B5FD6),
    yellow = Palette.Yellow, yellowDark = Palette.YellowDark, yellowSoft = Color(0xFF4A3500), onYellowSoft = Palette.YellowSoft,
    mint = Color(0xFF3DD9A0), mintSoft = Color(0xFF0F3A2D), coral = Color(0xFFFF7A66), coralDark = Color(0xFFFF8A7A), coralSoft = Color(0xFF4A1E18),
    sky = Color(0xFF6BC0FF), skySoft = Color(0xFF12344D), edge = Color(0xFF3A2F6B),
)

/** Reward colors live outside Material roles so they cannot leak into ordinary UI. */
@Immutable
data class RewardColors(val reward: Color, val onRewardContainer: Color, val rewardContainer: Color)

val DarkReward = RewardColors(reward = Palette.Yellow, onRewardContainer = Palette.YellowSoft, rewardContainer = Color(0xFF4A3500))
val LightReward = RewardColors(reward = Palette.Amber800, onRewardContainer = Color(0xFF6B4400), rewardContainer = Palette.YellowSoft)

/** Avatar backgrounds keyed by the server's avatarKey. Initials sit on them in white. */
val AvatarColors = mapOf(
    "explorer_teal" to (Palette.Violet700 to Color.White),
    "explorer_amber" to (Color(0xFFD9680F) to Color.White),
    "explorer_slate" to (Color(0xFF4A5568) to Color.White),
    "explorer_coral" to (Color(0xFFD13E2B) to Color.White),
    "explorer_moss" to (Color(0xFF2E7D4F) to Color.White),
    "explorer_plum" to (Color(0xFF8E4FA8) to Color.White),
)

/*
 * Hero panels (why): each main tab has one raised panel that holds its focal point (level, today's
 * quests, the circle, you). In the light theme it is a white card with a soft shadow; in the dark theme a
 * lifted violet surface.
 */
@Immutable
data class HeroColors(
    val background: Color,
    val raised: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val reward: Color,
    val tileHidden: Color,
    /** Light panels need a shadow to separate from the page; dark ones do not. */
    val shadow: Dp,
)

val LightHero = HeroColors(
    background = Color.White,
    raised = Palette.Violet50,
    text = Palette.Ink,
    muted = Palette.Ink2,
    accent = Palette.Violet700,
    onAccent = Color.White,
    reward = Palette.Amber800,
    tileHidden = Color(0xFFDDD3F5),
    shadow = 6.dp,
)
val DarkHero = HeroColors(
    background = Palette.Night850,
    raised = Palette.Night800,
    text = Palette.Mist100,
    muted = Palette.Mist300,
    accent = Palette.Violet300,
    onAccent = Color(0xFF1A0B45),
    reward = Palette.Yellow,
    tileHidden = Color(0xFF4A3F82),
    shadow = 0.dp,
)

/** The mockup's soft card gradients, as a lifted violet surface in the dark theme. Light uses the colours given. */
@androidx.compose.runtime.Composable
fun cardBrush(from: Color, to: Color, vertical: Boolean = false): androidx.compose.ui.graphics.Brush {
    val colors = if (LocalDarkTheme.current) listOf(Palette.Night850, Palette.Night800) else listOf(from, to)
    return if (vertical) androidx.compose.ui.graphics.Brush.verticalGradient(colors) else androidx.compose.ui.graphics.Brush.linearGradient(colors)
}
