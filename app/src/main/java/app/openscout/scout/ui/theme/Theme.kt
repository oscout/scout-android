// Scout for Android — the Material dialect of the Lit Control Room
// (apps/ios/DESIGN.md, root DESIGN.md). Warm paper by day, a lit warm cockpit
// after dark, one emerald accent, a status triad, and uppercase mono eyebrows
// as the structural label voice. Material You (wallpaper) color is offered as
// an opt-in alternative on Android 12+.

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

// -- Scout palette (iOS DESIGN.md values) ------------------------------------

private val LightCanvas = Color(0xFFF6F3ED)
private val LightSurface = Color(0xFFFFFDF9)
private val LightChrome = Color(0xFFF2EEE6)
private val LightInk = Color(0xFF23211D)
private val LightMuted = Color(0xFF58534B)
private val LightBorder = Color(0xFFCDC5B8)
private val LightAccent = Color(0xFF07785B)

private val DarkCanvasTop = Color(0xFF100E0B)
private val DarkCanvasFloor = Color(0xFF060504)
private val DarkCardTop = Color(0xFF211C18)
private val DarkCardBottom = Color(0xFF171411)
private val DarkEdge = Color(0xFF433A30)
private val DarkInset = Color(0xFF161310)
private val DarkRaised = Color(0xFF1C1915)
private val DarkInk = Color(0xFFEEEAE2)
private val DarkMuted = Color(0xFFB8B8B8)
private val DarkAccent = Color(0xFF2FCB94)

val AccentTail = Color(0xFF0BC5A5)

private val ScoutLight = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EDE3),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF5E5A52),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E3D8),
    onSecondaryContainer = LightInk,
    tertiary = Color(0xFF2F6C8F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E9F4),
    onTertiaryContainer = Color(0xFF0B2E44),
    background = LightCanvas,
    onBackground = LightInk,
    surface = LightCanvas,
    onSurface = LightInk,
    surfaceVariant = Color(0xFFEAE4DA),
    onSurfaceVariant = LightMuted,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightChrome,
    surfaceContainerHigh = Color(0xFFEDE8DF),
    surfaceContainerHighest = Color(0xFFE6E0D5),
    surfaceBright = LightSurface,
    surfaceDim = Color(0xFFE3DDD2),
    outline = LightBorder,
    outlineVariant = Color(0xFFE0D9CD),
    error = Color(0xFFB3412E),
    onError = Color.White,
    errorContainer = Color(0xFFF8DCD5),
    onErrorContainer = Color(0xFF4A1309),
)

private val ScoutDark = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color(0xFF00281C),
    primaryContainer = Color(0xFF123A2D),
    onPrimaryContainer = Color(0xFFB9F2DB),
    secondary = Color(0xFFCBC4B8),
    onSecondary = Color(0xFF2B2722),
    secondaryContainer = DarkRaised,
    onSecondaryContainer = DarkInk,
    tertiary = Color(0xFF7CC4F2),
    onTertiary = Color(0xFF07263A),
    tertiaryContainer = Color(0xFF15303F),
    onTertiaryContainer = Color(0xFFD3ECFB),
    background = DarkCanvasTop,
    onBackground = DarkInk,
    surface = DarkCanvasTop,
    onSurface = DarkInk,
    surfaceVariant = DarkRaised,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = DarkCanvasFloor,
    surfaceContainerLow = DarkCardBottom,
    surfaceContainer = DarkCardBottom,
    surfaceContainerHigh = DarkCardTop,
    surfaceContainerHighest = Color(0xFF2A241F),
    surfaceBright = Color(0xFF2E2822),
    surfaceDim = DarkCanvasFloor,
    outline = DarkEdge,
    outlineVariant = Color(0xFF2E2822),
    error = Color(0xFFF2725B),
    onError = Color(0xFF3A0A02),
    errorContainer = Color(0xFF4A1A10),
    onErrorContainer = Color(0xFFFFDAD2),
)

/** Status triad + structural tokens that Material's scheme doesn't name. */
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

private val LightTokens = ScoutTokens(
    ok = Color(0xFF07785B),
    warn = Color(0xFF9A6200),
    danger = Color(0xFFB3412E),
    info = Color(0xFF2F6C8F),
    inset = Color(0xFFEFEAE1),
    raised = LightSurface,
    edge = LightBorder,
    canvasTop = LightCanvas,
    canvasFloor = Color(0xFFF0ECE4),
    keyLight = Color(0x00FFFFFF),
    isDark = false,
)

private val DarkTokens = ScoutTokens(
    ok = DarkAccent,
    warn = Color(0xFFF2B34D),
    danger = Color(0xFFF2725B),
    info = Color(0xFF7CC4F2),
    inset = DarkInset,
    raised = DarkRaised,
    edge = DarkEdge,
    canvasTop = DarkCanvasTop,
    canvasFloor = DarkCanvasFloor,
    keyLight = Color(0xFFFFF0DB),
    isDark = true,
)

val LocalScoutTokens = staticCompositionLocalOf { LightTokens }

object Scout {
    val tokens: ScoutTokens @Composable get() = LocalScoutTokens.current
}

// -- Type: grotesque for content, mono for structure ---------------------------

val Mono = FontFamily.Monospace

private val BaseType = Typography()

private val ScoutTypography = Typography(
    displaySmall = BaseType.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseType.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseType.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = BaseType.bodyMedium.copy(lineHeight = 20.sp),
    labelSmall = BaseType.labelSmall.copy(fontSize = 11.sp),
)

/** Uppercase mono eyebrow — the system's signature label voice. Uppercase the text at the call site. */
val EyebrowStyle = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    letterSpacing = 0.12.em,
    lineHeight = 14.sp,
)

/** Mono detail: ids, paths, counts, timing. */
val MonoDetailStyle = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp)

@Composable
fun ScoutTheme(
    mode: ThemeMode = ThemeMode.System,
    wallpaperColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        wallpaperColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> ScoutDark
        else -> ScoutLight
    }
    val base = if (dark) DarkTokens else LightTokens
    val tokens = if (wallpaperColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        base.copy(
            ok = scheme.primary,
            inset = scheme.surfaceContainerHighest,
            raised = scheme.surfaceContainerHigh,
            edge = scheme.outlineVariant,
            canvasTop = scheme.surface,
            canvasFloor = scheme.surfaceContainerLowest,
        )
    } else base

    androidx.compose.runtime.CompositionLocalProvider(LocalScoutTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = ScoutTypography, content = content)
    }
}
