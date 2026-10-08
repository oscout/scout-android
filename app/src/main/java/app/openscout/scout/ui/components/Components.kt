package app.openscout.scout.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openscout.scout.ui.theme.EyebrowStyle
import app.openscout.scout.ui.theme.MonoDetailStyle
import app.openscout.scout.ui.theme.Scout
import app.openscout.scout.ui.theme.ScoutType
import java.util.Locale
import kotlin.random.Random

/**
 * A 128px monochrome grain tile, made once per process from a fixed seed with a narrow
 * mid-grey spread (the iOS grain: 82–175), and laid over the ground at a few percent.
 */
private val grainTile: ImageBitmap by lazy {
    val size = 128
    val rnd = Random(0x5C0)
    val pixels = IntArray(size * size) {
        val v = 82 + rnd.nextInt(94)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The lit ground: page colour, light falling from the top edge, and a little grain. */
@Composable
fun ScoutCanvas(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Scout.colors
    val grain = remember { ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated)) }
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(c.page)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(c.keyLight, Color.Transparent),
                        center = Offset(size.width / 2f, -size.height * 0.06f),
                        radius = size.width * 1.25f,
                    ),
                )
                drawRect(grain, alpha = if (c.isDark) 0.07f else 0.05f, blendMode = if (c.isDark) BlendMode.Overlay else BlendMode.Multiply)
            },
    ) {
        // The canvas is not a Surface, so set the content colour explicitly.
        CompositionLocalProvider(LocalContentColor provides c.ink) { content() }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Scout.colors.ink) {
    Text(text.uppercase(Locale.ROOT), style = EyebrowStyle, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** A list screen's section head: caps label, a long hairline, an optional trailing control. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    SectionHead(title, modifier = modifier.padding(horizontal = 12.dp)) { trailing?.invoke() }
}

/** A genuine container: the lit box with 14dp of padding. */
@Composable
fun ScoutCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    LitBox(modifier) { Column(Modifier.padding(14.dp), content = content) }
}

/**
 * Was the chamfered signal panel. The D voice retires the chamfer and marks: a lit box
 * whose state is carried by one lamp and a short datum on the top edge.
 */
@Composable
fun SignalPanel(
    active: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Scout.colors
    LitBox(modifier) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), content = content)
            Box(
                Modifier.padding(start = 14.dp).width(if (active) 30.dp else 18.dp).height(1.5.dp)
                    .background(if (active) accent else c.dim.copy(alpha = 0.6f)),
            )
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp, pulsing: Boolean = false) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val a by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
        a
    } else 1f
    val glow = Scout.colors.isDark
    Canvas(modifier.size(size)) {
        if (glow) drawCircle(color.copy(alpha = 0.22f * alpha), radius = this.size.minDimension)
        drawCircle(color.copy(alpha = alpha))
    }
}

/** An outlined mono pill: harness, kind, branch-like facts. */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, color: Color = Scout.colors.second) {
    val c = Scout.colors
    Text(
        text.lowercase(Locale.ROOT),
        style = ScoutType.micro,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .border(Hairline, if (color == c.second) c.line else color.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Scout.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).border(Hairline, c.line, RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(c.boxTop, Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.second, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = ScoutType.name.copy(fontSize = ScoutType.prose.fontSize), color = c.ink, textAlign = TextAlign.Center)
        Text(body, style = ScoutType.bodySmall, color = c.second, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(6.dp))
            action()
        }
    }
}

/** The host didn't answer: one quiet line with a signal lamp and Retry. Content below may be stale. */
@Composable
fun OfflineBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, retrying: Boolean = false) {
    val c = Scout.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).defaultMinSize(minHeight = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Lamp(LampState.Signal)
            Text(message, style = ScoutType.bodySmall, color = c.body, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (retrying) "Retrying…" else "Retry",
                style = ScoutType.name,
                color = c.ink,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !retrying, role = Role.Button, onClick = onRetry)
                    .defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        RowRule()
    }
}

@Composable
fun InlineError(message: String, modifier: Modifier = Modifier) {
    Text(message, style = ScoutType.bodySmall, color = Scout.colors.danger, modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, color: Color = Scout.colors.second, maxLines: Int = 1) {
    Text(text, style = MonoDetailStyle, color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Initials on a quiet tile, for people and agents without a harness mark. */
@Composable
fun Monogram(name: String, modifier: Modifier = Modifier, tint: Color = Scout.colors.second, size: Dp = 32.dp) {
    val c = Scout.colors
    val letters = name.split(' ', '-', '_', '.', '/').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp)).border(Hairline, c.line, RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(c.boxTop, Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        Text(letters, style = ScoutType.small.copy(fontWeight = FontWeight.SemiBold), color = tint)
    }
}

/** The Scout hex mark, in ink. */
@Composable
fun ScoutMark(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color = Scout.colors.ink) {
    Box(modifier) { HexMark(size, color) }
}

/** The primary action: a raised plate (ios-soft-selection), never a bright fill. */
@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    ActionButton(text, ButtonTone.Plate, onClick, modifier.defaultMinSize(minHeight = 48.dp), enabled, icon)
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    ActionButton(text, ButtonTone.Outline, onClick, modifier.defaultMinSize(minHeight = 48.dp), enabled, icon)
}

@Composable
private fun ActionButton(text: String, tone: ButtonTone, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, icon: ImageVector?) {
    val c = Scout.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .clip(shape)
            .then(
                if (tone == ButtonTone.Plate) Modifier.background(Brush.verticalGradient(listOf(c.plateTop, c.plateBottom)))
                else Modifier.border(Hairline, c.line, shape),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = (if (tone == ButtonTone.Plate) c.plateText else c.body).copy(alpha = if (enabled) 1f else 0.4f)
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = tint)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = ScoutType.name.copy(fontSize = ScoutType.prose.fontSize), color = tint)
    }
}

val ListContentPadding = PaddingValues(bottom = 96.dp)

fun relativeTime(ms: Long?, now: Long = System.currentTimeMillis()): String {
    if (ms == null || ms <= 0) return ""
    val diff = (now - ms).coerceAtLeast(0)
    val s = diff / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        s < 7 * 86_400 -> "${s / 86_400}d"
        else -> java.text.SimpleDateFormat("MMM d", Locale.getDefault()).format(java.util.Date(ms))
    }
}

fun harnessLabel(harness: String?): String? = when (harness?.lowercase()) {
    null, "", "unattributed" -> null
    "claude", "claude-code" -> "Claude"
    "codex" -> "Codex"
    "pi" -> "Pi"
    "opencode" -> "OpenCode"
    "acp" -> "ACP"
    "openai" -> "OpenAI"
    "kimi", "kimi-code" -> "Kimi"
    "gemini" -> "Gemini"
    else -> harness.replaceFirstChar { it.uppercase() }
}

