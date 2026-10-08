package app.openscout.scout.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openscout.scout.ui.theme.Scout
import app.openscout.scout.ui.theme.ScoutType

/** Half a density-independent pixel: the system's hairline. */
val Hairline: Dp = 0.5.dp

private val BoxShape = RoundedCornerShape(10.dp)

/**
 * A box lit along its top edge: a faint top-down wash, a 0.5dp edge, a brighter
 * 0.5dp highlight on the top, and a soft drop beneath. Content shares the page.
 */
@Composable
fun LitBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Scout.colors
    Column(
        modifier
            .shadow(if (c.isDark) 10.dp else 4.dp, BoxShape, ambientColor = Color.Black, spotColor = Color.Black.copy(alpha = if (c.isDark) 0.7f else 0.18f))
            .clip(BoxShape)
            .background(c.page)
            .background(Brush.verticalGradient(0f to c.boxTop, 0.6f to c.boxTop.copy(alpha = c.boxTop.alpha * 0.3f), 1f to Color.Transparent))
            .border(Hairline, c.line, BoxShape)
            .drawBehind {
                drawLine(c.boxEdgeHighlight, Offset(10.dp.toPx(), 0.25.dp.toPx()), Offset(size.width - 10.dp.toPx(), 0.25.dp.toPx()), strokeWidth = Hairline.toPx())
            },
        content = content,
    )
}

/** A row separator inside a box. */
@Composable
fun RowRule() {
    Box(Modifier.fillMaxWidth().height(Hairline).background(Scout.colors.rule))
}

/** `LABEL · count ──────── trailing`: the structural section head. */
@Composable
fun SectionHead(
    title: String,
    count: String? = null,
    countColor: Color = Scout.colors.dim,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Scout.colors
    Row(
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).defaultMinSize(minHeight = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title.uppercase(), style = ScoutType.label, color = c.ink)
        if (count != null) Text("· $count", style = ScoutType.meta, color = countColor)
        Box(Modifier.weight(1f).height(Hairline).background(c.rule))
        trailing()
    }
}

enum class LampState { Live, Signal, Hollow }

@Composable
fun Lamp(state: LampState, modifier: Modifier = Modifier) {
    val c = Scout.colors
    Canvas(modifier.size(6.dp)) {
        val r = size.minDimension / 2
        when (state) {
            LampState.Live, LampState.Signal -> {
                val color = if (state == LampState.Live) c.life else c.signal
                if (c.isDark) drawCircle(color.copy(alpha = 0.18f), radius = r * 1.6f)
                drawCircle(color, radius = r)
            }
            LampState.Hollow -> drawCircle(c.dim, radius = r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
    }
}

/** Raised-plate segments (ios-soft-selection): the selected item is a plate, never bright ink. */
@Composable
fun Segmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Scout.colors
    Row(
        modifier.clip(RoundedCornerShape(7.dp)).border(Hairline, c.line, RoundedCornerShape(7.dp)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .then(if (on) Modifier.background(Brush.verticalGradient(listOf(c.plateTop, c.plateBottom))) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { this.selected = on }
                    .defaultMinSize(minHeight = 24.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = ScoutType.meta, color = if (on) c.plateText else c.second, maxLines = 1)
            }
        }
    }
}

/** Ten round dots for a quota window; a lit dot past 80% turns signal. */
@Composable
fun DotMeter(percent: Int?, modifier: Modifier = Modifier) {
    val c = Scout.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(10) { i ->
            val edge = (i + 1) * 10
            val lit = percent != null && edge <= percent + 5
            val hot = lit && edge > 80
            Box(Modifier.size(4.dp).clip(CircleShape).background(if (hot) c.signal else if (lit) c.dotOn else c.dotOff))
        }
    }
}

enum class Harness { Claude, Codex, Other;
    companion object {
        fun of(raw: String?): Harness = when {
            raw == null -> Other
            raw.contains("claude", ignoreCase = true) -> Claude
            raw.contains("codex", ignoreCase = true) -> Codex
            else -> Other
        }
    }
}

private fun parsePath(d: String): Path = PathParser().parsePathString(d).toPath()

@Composable
fun HarnessMark(harness: Harness, size: Dp = 11.dp, color: Color = Scout.colors.second) {
    val path = remember(harness) {
        when (harness) {
            Harness.Claude -> parsePath(HarnessPaths.CLAUDE)
            Harness.Codex -> parsePath(HarnessPaths.CODEX)
            Harness.Other -> null
        }?.apply { fillType = PathFillType.EvenOdd }
    }
    Canvas(Modifier.size(size).semantics { contentDescription = harness.name.lowercase() }) {
        if (path == null) {
            drawCircle(color, radius = this.size.minDimension * 0.32f, style = Stroke(1.dp.toPx()))
        } else {
            scale(this.size.width / 24f, pivot = Offset.Zero) { drawPath(path, color) }
        }
    }
}

/** The Scout hex mark: ring stroked, core filled. */
@Composable
fun HexMark(size: Dp = 20.dp, color: Color = Scout.colors.ink, ringOnly: Boolean = false) {
    val ring = remember { parsePath(HarnessPaths.HEX_RING) }
    val core = remember { parsePath(HarnessPaths.HEX_CORE) }
    Canvas(Modifier.width(size).height(size * (236f / 224f))) {
        scale(this.size.width / 224f, pivot = Offset.Zero) {
            drawPath(ring, color, style = Stroke(if (ringOnly) 14f else 24f, join = StrokeJoin.Round))
            if (!ringOnly) drawPath(core, color)
        }
    }
}

/** A thin-line glyph on a 16-unit grid (the web Home icon set, 1.25 stroke). */
@Composable
fun Glyph(d: String, size: Dp = 16.dp, color: Color = Scout.colors.second, grid: Float = 16f, stroke: Float = 1.25f) {
    val path = remember(d) { parsePath(d) }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / grid, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

object Glyphs {
    const val BURGER = "M2.5 4.5h11M2.5 8h11M2.5 11.5h11"
    const val WAVE = "M2 8h1M4.5 5v6M7 3v10M9.5 5.5v5M12 7v2"
    const val BRANCH = "M4.5 2a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3M4.5 11a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3M11.5 4a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3M4.5 5v6M11.5 7c0 2.5-3 2.5-7 4"
    const val DOWN = "M4 6l4 4 4-4"
    const val BACK = "M13 8H3M7.5 3.5 3 8l4.5 4.5"
    const val CLOSE = "M3.5 3.5l9 9M12.5 3.5l-9 9"
    const val SEARCH = "M10.5 10.5 14 14M2.5 7a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0-9 0"
    const val SEND = "M8 13V3M3.5 7.5 8 3l4.5 4.5"
    const val PLUS = "M8 3v10M3 8h10"
    const val TUNE = "M2.5 5h7M12.5 5h1M2.5 11h1M6.5 11h7M9.5 5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0M3.5 11a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0"

    // Navigation glyphs on the 24 grid (V3NavigationGlyph), drawn at 1.3.
    const val HOME = "M3 10.5 L12 3 L21 10.5 M5.5 9 V20 H9.5 V14 H14.5 V20 H18.5 V9"
    const val CHATS = "M5 4 H19 Q21 4 21 6 V14 Q21 16 19 16 H10 L5 20 V16 Q3 16 3 14 V6 Q3 4 5 4 Z M7 8.5 H17 M7 12 H13"
    const val AGENTS = "M9 7 A3 3 0 1 0 15 7 A3 3 0 1 0 9 7 M5.5 21 V18 Q5.5 13 12 13 Q18.5 13 18.5 18 V21 M5 6 A2.5 2.5 0 0 0 5 11 M19 6 A2.5 2.5 0 0 1 19 11 M3.5 18 V16.5 Q3.5 14.5 5 13.5 M20.5 18 V16.5 Q20.5 14.5 19 13.5"
    const val OPS = "M5 3 H19 Q21 3 21 5 V19 Q21 21 19 21 H5 Q3 21 3 19 V5 Q3 3 5 3 Z M7 8 L11 12 L7 16 M13 16 H17"
    const val ALERTS = "M5.5 10 Q5.5 4 12 4 Q18.5 4 18.5 10 V14 L20.75 17 H3.25 L5.5 14 Z M10 20 Q12 22 14 20 M12 2 V4"
}

@Composable
fun BranchPill(branch: String, modifier: Modifier = Modifier) {
    val c = Scout.colors
    Row(
        modifier
            .widthIn(max = 150.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(Hairline, c.line, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Glyph(Glyphs.BRANCH, size = 9.dp, color = c.dim)
        Text(branch, style = ScoutType.micro.copy(letterSpacing = ScoutType.meta.letterSpacing), color = c.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An outlined pill for kinds and asks: `exec`, `ask`. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(999.dp)).border(Hairline, color.copy(alpha = 0.5f), RoundedCornerShape(999.dp)).padding(horizontal = 6.dp),
    ) {
        Text(text, style = ScoutType.micro, color = color)
    }
}

/** A 48dp icon target for the masthead. */
@Composable
fun IconTarget(description: String, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

enum class ButtonTone { Outline, Plate }

@Composable
fun ScoutButton(text: String, tone: ButtonTone, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Scout.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .then(
                if (tone == ButtonTone.Plate) Modifier.background(Brush.verticalGradient(listOf(c.plateTop, c.plateBottom)))
                else Modifier.border(Hairline, c.line, shape),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ScoutType.name, color = if (tone == ButtonTone.Plate) c.plateText else c.body)
    }
}

@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

/** The masthead seam under the status bar. */
@Composable
fun MastRule() = Box(Modifier.fillMaxWidth().height(Hairline).background(Scout.colors.rule))
