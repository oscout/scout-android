// Scout for Android in the iPhone's current voice: "D · web voice"
// (design/studio/views/ios-calmer-surfaces.tsx) and Home, denser round II
// (ios-home-dense.tsx). A near-black ground lit from above with a little grain,
// 0.5dp low-alpha hairlines, boxes lit along their top edge, caps mono labels on
// a long rule, harness marks, and colour kept for state. Selection is a raised
// plate in dark (ios-soft-selection.tsx); light keeps ink selection.

package app.openscout.scout.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

enum class ThemeMode { System, Light, Dark }

/** The D voice, resolved per appearance. */
@Immutable
data class ScoutColors(
    val page: Color,
    val ink: Color,
    val body: Color,
    val second: Color,
    val dim: Color,
    /** Edges of boxes, chips and segments. */
    val line: Color,
    /** Row separators and the rule that trails a section label. */
    val rule: Color,
    val life: Color,
    val signal: Color,
    val danger: Color,
    val info: Color,
    val keyLight: Color,
    val boxTop: Color,
    val boxEdgeHighlight: Color,
    val plateTop: Color,
    val plateBottom: Color,
    val plateText: Color,
    val well: Color,
    val dotOff: Color,
    val dotOn: Color,
    val popTop: Color,
    val popBottom: Color,
    val kindEdit: Color,
    val kindShell: Color,
    val kindRead: Color,
    val kindTool: Color,
    val kindOut: Color,
    val kindSay: Color,
    val kindYou: Color,
    val isDark: Boolean,
)

val DarkScout = ScoutColors(
    page = Color(0xFF08090A),
    ink = Color(0xFFECEEF1),
    body = Color(0xFFD2D6DC),
    second = Color(0xFF9AA0A9),
    dim = Color(0xFF737A84),
    line = Color.White.copy(alpha = 0.085f),
    rule = Color.White.copy(alpha = 0.055f),
    life = Color(0xFF3FD29B),
    signal = Color(0xFFE8955A),
    danger = Color(0xFFF2725B),
    info = Color(0xFF7CC4F2),
    keyLight = Color(0xFFA0AFC8).copy(alpha = 0.075f),
    boxTop = Color.White.copy(alpha = 0.028f),
    boxEdgeHighlight = Color.White.copy(alpha = 0.07f),
    plateTop = Color(0xFF33363D),
    plateBottom = Color(0xFF282B31),
    plateText = Color(0xFFECEEF1),
    well = Color.Black.copy(alpha = 0.35f),
    dotOff = Color.White.copy(alpha = 0.09f),
    dotOn = Color(0xFFC2C8CF),
    popTop = Color(0xFF17191D),
    popBottom = Color(0xFF121417),
    kindEdit = Color(0xFFE0A458),
    kindShell = Color(0xFF7FA7D9),
    kindRead = Color(0xFF8E96A0),
    kindTool = Color(0xFFB596DB),
    kindOut = Color(0xFF5F6670),
    kindSay = Color(0xFFD2D6DC),
    kindYou = Color(0xFFECEEF1),
    isDark = true,
)

val LightScout = ScoutColors(
    page = Color(0xFFEFEDE8),
    ink = Color(0xFF141413),
    body = Color(0xFF2C2B28),
    second = Color(0xFF55524C),
    dim = Color(0xFF6E6A63),
    line = Color(0xFF141413).copy(alpha = 0.11f),
    rule = Color(0xFF141413).copy(alpha = 0.075f),
    life = Color(0xFF0B8F62),
    signal = Color(0xFFC2410C),
    danger = Color(0xFFB23422),
    info = Color(0xFF2F6C8F),
    keyLight = Color.White.copy(alpha = 0.7f),
    boxTop = Color(0xFFFBFAF7),
    boxEdgeHighlight = Color.White,
    plateTop = Color(0xFF141413),
    plateBottom = Color(0xFF141413),
    plateText = Color(0xFFFBFAF7),
    well = Color(0xFFE7E4DE),
    dotOff = Color(0xFF141413).copy(alpha = 0.10f),
    dotOn = Color(0xFF55524C),
    popTop = Color(0xFFFBFAF7),
    popBottom = Color(0xFFF5F3EE),
    kindEdit = Color(0xFFA8661A),
    kindShell = Color(0xFF2F6FB8),
    kindRead = Color(0xFF6B737D),
    kindTool = Color(0xFF7E4FB8),
    kindOut = Color(0xFF9AA0A8),
    kindSay = Color(0xFF2E3238),
    kindYou = Color(0xFF0E1012),
    isDark = false,
)

/** Material's scheme, resolved onto the D tokens so stock components sit in the same room. */
private fun scheme(c: ScoutColors): ColorScheme {
    val raised = if (c.isDark) Color(0xFF15171A) else Color(0xFFF7F5F1)
    val high = if (c.isDark) Color(0xFF1C1F23) else Color(0xFFE9E6E0)
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.life,
        onPrimary = c.page,
        primaryContainer = c.plateTop,
        onPrimaryContainer = c.plateText,
        secondary = c.second,
        onSecondary = c.page,
        secondaryContainer = high,
        onSecondaryContainer = c.ink,
        tertiary = c.info,
        onTertiary = c.page,
        background = c.page,
        onBackground = c.ink,
        surface = c.page,
        onSurface = c.ink,
        surfaceVariant = raised,
        onSurfaceVariant = c.second,
        surfaceContainerLowest = c.page,
        surfaceContainerLow = raised,
        surfaceContainer = raised,
        surfaceContainerHigh = high,
        surfaceContainerHighest = high,
        surfaceBright = high,
        surfaceDim = c.page,
        inverseSurface = c.ink,
        inverseOnSurface = c.page,
        outline = c.line.copy(alpha = c.line.alpha * 2.2f),
        outlineVariant = c.line,
        error = c.danger,
        onError = c.page,
        scrim = Color.Black,
    )
}

/** Status + structural tokens kept for the screens written against the first pass. */
@Immutable
data class ScoutTokens(
    val ok: Color,
    val warn: Color,
    val danger: Color,
    val info: Color,
    val inset: Color,
    val raised: Color,
    val edge: Color,
    val canvasTop: Color,
    val canvasFloor: Color,
    val keyLight: Color,
    val isDark: Boolean,
)

private fun tokens(c: ScoutColors) = ScoutTokens(
    ok = c.life,
    warn = c.signal,
    danger = c.danger,
    info = c.info,
    inset = c.well,
    raised = c.boxTop,
    edge = c.line,
    canvasTop = c.page,
    canvasFloor = c.page,
    keyLight = c.keyLight,
    isDark = c.isDark,
)

val LocalScoutTokens = staticCompositionLocalOf { tokens(DarkScout) }
val LocalScoutColors = staticCompositionLocalOf { DarkScout }

object Scout {
    val tokens: ScoutTokens @Composable get() = LocalScoutTokens.current
    val colors: ScoutColors @Composable get() = LocalScoutColors.current
}

val AccentTail = Color(0xFF0BC5A5)

// -- Type: sans names, mono times/kinds/paths/labels ----------------------------

val Mono = FontFamily.Monospace

object ScoutType {
    private val sans = FontFamily.SansSerif

    /** Caps section label: mono 10.5, semibold, 0.1em, followed by a long hairline. Uppercase at the call site. */
    val label = TextStyle(fontFamily = Mono, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em)
    val meta = TextStyle(fontFamily = Mono, fontSize = 10.5.sp)
    val small = TextStyle(fontFamily = Mono, fontSize = 10.sp)
    val micro = TextStyle(fontFamily = Mono, fontSize = 9.5.sp, letterSpacing = 0.04.em)
    val mono11 = TextStyle(fontFamily = Mono, fontSize = 11.sp)
    val mono12 = TextStyle(fontFamily = Mono, fontSize = 11.5.sp, lineHeight = 17.sp)
    val title = TextStyle(fontFamily = sans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val name = TextStyle(fontFamily = sans, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
    val nameSmall = TextStyle(fontFamily = sans, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontFamily = sans, fontSize = 13.5.sp, lineHeight = 18.sp)
    val bodySmall = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 17.sp)
    val prose = TextStyle(fontFamily = sans, fontSize = 14.sp, lineHeight = 21.sp)
}

private val BaseType = Typography()

private val ScoutTypography = Typography(
    displaySmall = BaseType.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    titleSmall = BaseType.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
    bodyLarge = BaseType.bodyLarge.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = BaseType.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
    bodySmall = BaseType.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
    labelLarge = BaseType.labelLarge.copy(fontSize = 13.5.sp),
    labelSmall = BaseType.labelSmall.copy(fontSize = 11.sp),
)

/** Uppercase mono eyebrow. Uppercase the text at the call site. */
val EyebrowStyle = ScoutType.label

/** Mono detail: ids, paths, counts, timing. */
val MonoDetailStyle = ScoutType.mono12

@Composable
fun ScoutTheme(
    mode: ThemeMode = ThemeMode.Dark,
    wallpaperColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) DarkScout else LightScout
    val context = LocalContext.current
    val materialScheme = if (wallpaperColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Opt-in: the wallpaper tints Material's accents; the D ground and hairlines stay.
        val wall = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        scheme(colors).copy(primary = wall.primary, onPrimary = wall.onPrimary)
    } else scheme(colors)
    CompositionLocalProvider(LocalScoutColors provides colors, LocalScoutTokens provides tokens(colors)) {
        MaterialTheme(colorScheme = materialScheme, typography = ScoutTypography, content = content)
    }
}
