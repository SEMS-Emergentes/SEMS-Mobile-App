package pe.edu.upc.sems.shared.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.edu.upc.sems.shared.designsystem.SemsSpacing

/**
 * Plain bar chart (Figma M13: "Potencia por hora"). Bars are scaled to the highest value; a bar with no
 * value is drawn as a faint stub so the time axis stays readable. [xLabels] sit under the bars, spread evenly.
 * [description] is what screen readers say, since the canvas itself has no text.
 */
@Composable
fun BarChart(
    values: List<Double>,
    xLabels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val bar = MaterialTheme.colorScheme.primary
    val stub = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            if (values.isEmpty()) return@Canvas
            val max = values.max().takeIf { it > 0.0 } ?: 1.0
            val slot = size.width / values.size
            val gap = slot * 0.25f
            val radius = CornerRadius(3.dp.toPx())
            val minHeight = 3.dp.toPx()
            values.forEachIndexed { index, value ->
                val height = if (value > 0.0) maxOf((value / max * size.height).toFloat(), minHeight) else minHeight
                drawRoundRect(
                    color = if (value > 0.0) bar else stub,
                    topLeft = Offset(index * slot + gap / 2, size.height - height),
                    size = Size(slot - gap, height),
                    cornerRadius = radius,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SemsSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            xLabels.forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
