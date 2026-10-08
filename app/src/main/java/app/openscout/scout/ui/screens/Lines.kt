package app.openscout.scout.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.openscout.scout.ui.components.MonoText
import app.openscout.scout.ui.components.StatusDot
import app.openscout.scout.ui.theme.Scout
import app.openscout.scout.ui.theme.ScoutType

/** A quiet "still reading" line: a breathing dim lamp and mono text. */
@Composable
fun LoadingLine(text: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(Scout.colors.dim, pulsing = true)
        Spacer(Modifier.width(10.dp))
        MonoText(text)
    }
}

/** One line of quiet copy where a list is empty. */
@Composable
fun QuietLine(text: String) {
    Text(text, style = ScoutType.bodySmall, color = Scout.colors.dim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
