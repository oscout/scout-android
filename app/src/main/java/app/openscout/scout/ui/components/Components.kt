package app.openscout.scout.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openscout.scout.ui.theme.EyebrowStyle
import app.openscout.scout.ui.theme.MonoDetailStyle
import app.openscout.scout.ui.theme.Scout
import java.util.Locale

/** The lit canvas: a vertical wash and, after dark, a faint warm key-light from the top edge. */
@Composable
fun ScoutCanvas(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = Scout.tokens
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(t.canvasTop, MaterialTheme.colorScheme.background, t.canvasFloor)))
            .drawBehind {
                if (t.isDark) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(t.keyLight.copy(alpha = 0.07f), Color.Transparent),
                            center = Offset(size.width / 2f, 0f),
                            radius = 380.dp.toPx(),
                        ),
                        radius = 380.dp.toPx(),
                        center = Offset(size.width / 2f, 0f),
                    )
                }
            },
    ) {
        // The canvas is not a Surface, so set the content color explicitly; otherwise
        // un-tinted Text/Icons fall back to black and vanish in Dark.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) { content() }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(Locale.ROOT), style = EyebrowStyle, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(title, Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Genuine container card: lifted surface with a solid edge (never a white-alpha hairline). */
@Composable
fun ScoutCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = Scout.tokens
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .shadow(if (t.isDark) 6.dp else 2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(shape)
            .background(
                if (t.isDark) Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerLow))
                else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.surfaceContainerLowest)),
            )
            .border(1.dp, Brush.verticalGradient(listOf(t.edge, MaterialTheme.colorScheme.outlineVariant)), shape)
            .padding(14.dp),
        content = content,
    )
}

/**
 * The signature signal panel: a chamfered eight-sided plate with four corner
 * registration marks and one datum line that carries state. The accent reaches
 * the marks and datum only — it never washes the panel.
 */
@Composable
fun SignalPanel(
    active: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = Scout.tokens
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val markColor = if (active) accent else neutral
    val shape = CutCornerShape(6.dp)
    val top = if (t.isDark) Color(0xFF131516) else Color(0xFFFFFEFA)
    val bottom = if (t.isDark) Color(0xFF0B0D0E) else Color(0xFFF3EFE7)
    val edge = if (t.isDark) Color(0xFF3A3E3F) else Color(0xFFC3BCB0)
    Column(
        modifier
            .shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.24f), spotColor = Color.Black.copy(alpha = 0.24f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, edge, shape)
            .drawBehind {
                val arm = 9.dp.toPx()
                val inset = 4.dp.toPx()
                val stroke = 1.dp.toPx()
                val c = markColor.copy(alpha = 0.82f)
                val w = size.width
                val h = size.height
                // Corner registration marks.
                listOf(
                    Offset(inset, inset) to Offset(1f, 1f),
                    Offset(w - inset, inset) to Offset(-1f, 1f),
                    Offset(inset, h - inset) to Offset(1f, -1f),
                    Offset(w - inset, h - inset) to Offset(-1f, -1f),
                ).forEach { (o, d) ->
                    drawLine(c, o, Offset(o.x + d.x * arm, o.y), stroke)
                    drawLine(c, o, Offset(o.x, o.y + d.y * arm), stroke)
                }
                // Datum line, top-left: 18dp neutral idle, 30dp accent when active.
                val datumWidth = (if (active) 30.dp else 18.dp).toPx()
                drawRect(markColor, topLeft = Offset(inset + arm + 6.dp.toPx(), inset), size = Size(datumWidth, stroke * 1.5f))
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp, pulsing: Boolean = false) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val a by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
        a
    } else 1f
    Box(modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(3.dp),
        color = Scout.tokens.inset,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text.uppercase(Locale.ROOT),
            style = EyebrowStyle.copy(letterSpacing = EyebrowStyle.letterSpacing * 0.6f),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CutCornerShape(10.dp)).background(Scout.tokens.inset)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CutCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(6.dp))
            action()
        }
    }
}

/** Connection problem banner with a retry; shown above content that may be stale. */
@Composable
fun OfflineBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, retrying: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = CutCornerShape(6.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onRetry, enabled = !retrying) {
                Text(if (retrying) "Retrying…" else "Retry")
            }
        }
    }
}

@Composable
fun InlineError(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines: Int = 1) {
    Text(text, style = MonoDetailStyle, color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Initials avatar on an inset plate. */
@Composable
fun Monogram(name: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 40.dp) {
    val letters = name.split(' ', '-', '_', '.', '/').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letters, style = EyebrowStyle.copy(fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize), color = tint)
    }
}

/** The Scout mark: a pointy hex with a lit facet, drawn — no bitmap. */
@Composable
fun ScoutMark(modifier: Modifier = Modifier, size: Dp = 28.dp, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.25f)
            lineTo(w, h * 0.75f)
            lineTo(w * 0.5f, h)
            lineTo(0f, h * 0.75f)
            lineTo(0f, h * 0.25f)
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(color, app.openscout.scout.ui.theme.AccentTail), Offset.Zero, Offset(w, h)))
        val inner = Path().apply {
            moveTo(w * 0.5f, h * 0.28f)
            lineTo(w * 0.72f, h * 0.4f)
            lineTo(w * 0.72f, h * 0.62f)
            lineTo(w * 0.5f, h * 0.74f)
            lineTo(w * 0.28f, h * 0.62f)
            lineTo(w * 0.28f, h * 0.4f)
            close()
        }
        drawPath(inner, Color.White.copy(alpha = 0.92f), style = Stroke(width = w * 0.07f))
        drawCircle(Color.White, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.51f))
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick = onClick, modifier = modifier.height(52.dp), enabled = enabled, shape = RoundedCornerShape(8.dp)) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), enabled = enabled, shape = RoundedCornerShape(8.dp)) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

val ListContentPadding = PaddingValues(bottom = 96.dp)

fun relativeTime(ms: Long?, now: Long = System.currentTimeMillis()): String {
    if (ms == null || ms <= 0) return ""
    val diff = (now - ms).coerceAtLeast(0)
    val s = diff / 1000
    return when {
        s < 60 -> "now"
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
