package app.locana.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.locana.R

/*
 * Typography (why): from the mockup. Bricolage Grotesque ExtraBold gives headings and numbers (levels, XP,
 * ranks) a playful voice that belongs to the game; Plus Jakarta Sans carries body text and labels.
 * Bricolage Grotesque is SIL OFL 1.1 (res/raw/license_bricolage_grotesque.txt).
 */
val Display = FontFamily(
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)

/** Plus Jakarta Sans for body and labels (SIL OFL 1.1, res/raw/license_plus_jakarta_sans.txt). */
val Body = FontFamily(
    Font(R.font.plus_jakarta_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_extrabold, FontWeight.ExtraBold),
)

private val base = Typography()

val LocanaTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = base.labelMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp),
    labelSmall = base.labelSmall.copy(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp),
)

/** Big numbers (XP, level, rank). */
val NumberStyle = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp)

/** Hero numbers (level, today's progress): the one oversized element on a tab, its focal point. */
val HeroNumberStyle = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, lineHeight = 58.sp)
