package com.caretrail.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun circle(cx: Int, cy: Int, r: Int) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"
private fun rect(x: Int, y: Int, w: Int, h: Int, r: Int) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r $r v${h - 2 * r}a$r $r 0 0 1 ${-r} $r h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r} v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}Z"

/** Same 24x24 stroke icons as the web app's CareIcon. */
val CarePaths: Map<String, List<String>> = mapOf(
    "home" to listOf("m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z"),
    "bell" to listOf("M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM10 21h4"),
    "upload" to listOf("M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6"),
    "arrow" to listOf("M4 12h16m-6-6 6 6-6 6"),
    "visit" to listOf(circle(12, 6, 3), "M5 21v-2a7 7 0 0 1 14 0v2M12 11v7m-3.5-3.5h7"),
    "test" to listOf("M8 3h8m-7 0v7l-4 8a2 2 0 0 0 2 3h10a2 2 0 0 0 2-3l-4-8V3M8 16h8"),
    "scan" to listOf(rect(3, 4, 18, 13, 2), "M8 21h8m-4-4v4m-5-9 3-3 3 3 4-4"),
    "vaccination" to listOf("m5 19 3-3m-1-9 10 10M9 5l10 10M13 3l8 8m-8 8-8-8m-2 2 8 8"),
    "review" to listOf(rect(5, 4, 14, 17, 2), "M9 4V2h6v2M8 10h8m-8 4h8m-8 4h5"),
    "pregnancy" to listOf("M9 7a3 3 0 1 0 6 0 3 3 0 0 0-6 0ZM8 21v-4a4 4 0 0 1 8 0v4M12 14a4 4 0 0 1 4 4"),
    "movement" to listOf("M3 12h4l2-5 3 10 2-5h7M18 5l1-2m-1 18 1-2"),
    "warning" to listOf("M12 3 2 21h20L12 3Zm0 6v5m0 3v1"),
    "hospital" to listOf(rect(4, 7, 16, 14, 1), "M10 4h4v7m-6 1h8m-4-4v8m-2 5v-4h4v4"),
    "blood" to listOf("M12 2C10 6 5 11 5 15a7 7 0 0 0 14 0c0-4-5-9-7-13Z"),
    "food" to listOf("M5 3v8m3-8v8m-3 0h3m-1 0v10m8-18v18m0-18c3 2 4 5 4 8h-4"),
)

@Composable
fun CareIcon(name: String, size: Dp = 24.dp, tint: Color = CT.Brand) {
    val paths = remember(name) { (CarePaths[name] ?: CarePaths.getValue("review")).map { PathParser().parsePathString(it).toPath() } }
    Canvas(Modifier.size(size)) {
        val scale = this.size.width / 24f
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            paths.forEach { p: Path ->
                drawPath(p, tint, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
