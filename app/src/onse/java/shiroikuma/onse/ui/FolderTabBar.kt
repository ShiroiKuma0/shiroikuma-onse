package shiroikuma.onse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The folder-style tab bar of 白い熊 メッセージ (its `views/FolderTabBar.kt`), in Compose and with the
 * same measurements: tabs drawn as stacked paper folders — a border line runs under the inactive
 * tabs, turns up the left of the active folder, across its top, slants down its right and continues
 * under the rest; the inactive folders show their own shaded outlines and dimmed titles.
 */

private val BAR_HEIGHT = 46.dp
private val INACTIVE_HEIGHT = 34.dp
private val ACTIVE_TOP = 4.dp
private val SLANT = 10.dp
private val STROKE = 1.5.dp
private val CORNER = 8.dp
private val TAB_PADDING = 14.dp
private val START_GAP = 8.dp
private const val SHADED_ALPHA = 0x55 / 255f
private const val DIM_ALPHA = 0x8C / 255f

@Composable
fun FolderTabBar(
    titles: List<String>,
    selected: Int,
    ink: Color,
    background: Color,
    fontFamily: FontFamily? = null,
    onSelect: (Int) -> Unit,
) {
    val bounds = remember(titles.size) { mutableStateListOf<Rect>().apply { repeat(titles.size) { add(Rect.Zero) } } }
    Box(
        Modifier.fillMaxWidth().height(BAR_HEIGHT).background(background).drawWithContent {
            drawContent()
            val stroke = STROKE.toPx()
            val slant = SLANT.toPx()
            val bottom = size.height - stroke / 2
            fun folder(r: Rect, fromLeftEdge: Boolean) = Path().apply {
                val top = r.top + stroke / 2
                if (fromLeftEdge) { moveTo(0f, bottom); lineTo(r.left, bottom) } else moveTo(r.left, bottom)
                lineTo(r.left, top)
                lineTo(r.right, top)
                lineTo(r.right + slant, bottom)
            }
            val style = Stroke(width = stroke, join = StrokeJoin.Round, pathEffect = PathEffect.cornerPathEffect(CORNER.toPx()))
            bounds.forEachIndexed { i, r -> if (i != selected && r != Rect.Zero) drawPath(folder(r, false), ink.copy(alpha = SHADED_ALPHA), style = style) }
            val line = bounds.getOrNull(selected)?.takeIf { it != Rect.Zero }?.let { folder(it, true) }
                ?: Path().apply { moveTo(0f, bottom) }
            line.lineTo(size.width, bottom)
            drawPath(line, ink, style = style)
        },
    ) {
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.width(START_GAP))
            titles.forEachIndexed { i, title ->
                val active = i == selected
                Box(
                    Modifier
                        .then(if (active) Modifier.padding(top = ACTIVE_TOP).fillMaxHeight() else Modifier.height(INACTIVE_HEIGHT))
                        .onGloballyPositioned { bounds[i] = it.boundsInParent() }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                        .padding(horizontal = TAB_PADDING),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        color = if (active) ink else ink.copy(alpha = DIM_ALPHA),
                        fontSize = 15.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(SLANT))
            }
        }
    }
}
