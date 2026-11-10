package com.dabcode.hal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dabcode.settings.UiStyle

// The parts of the screens that change with the style.
// Paper: 2dp ink border, 18dp corners, a hard offset shadow, the label in the top border.
// Mono: 1dp ink border, 6dp corners, a small uppercase label in the top border.
// Quiet: no box. Small uppercase labels. Hairlines in the line color.

/** Return the app name as the style shows it. Mono shows app names in lower case. */
@Composable
fun styledAppName(name: String): String =
    if (LocalUiStyle.current == UiStyle.MONO) name.lowercase() else name

/** A small label. Mono and Quiet show it in upper case. */
@Composable
fun StyleLabel(text: String, modifier: Modifier = Modifier) {
    val style = LocalUiStyle.current
    val p = LocalPalette.current
    val isPaper = style == UiStyle.PAPER
    val textStyle = if (isPaper) MaterialTheme.typography.labelMedium.copy(color = p.ink)
    // The letter spacing is in em, so that it shrinks with the font (1sp at 11sp).
    else MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.09.em, color = p.muted)
    // The label is always one line. A long label (for example "ENTSPERRUNGEN · HEUTE") shrinks to fit.
    // Below the minimum size, it ends with an ellipsis. It never wraps.
    BasicText(
        if (isPaper) text else text.uppercase(),
        modifier = modifier,
        style = textStyle,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = textStyle.fontSize, stepSize = 0.5.sp)
    )
}

/**
 * A box in the look of the current style, with an optional [label] in the top border.
 * Quiet has no box: it shows the label above the content.
 * Set [fillHeight] only when the parent fixes the height (for example a Row with IntrinsicSize.Min).
 * In a Column with a bounded height, fillHeight makes the box take all the free space.
 */
@Composable
fun StyleBox(
    modifier: Modifier = Modifier,
    label: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalUiStyle.current
    val p = LocalPalette.current
    if (style == UiStyle.QUIET) {
        Column(modifier) {
            if (label != null) StyleLabel(label, Modifier.padding(bottom = 4.dp))
            content()
        }
        return
    }

    val isPaper = style == UiStyle.PAPER
    val shape = RoundedCornerShape(if (isPaper) 18.dp else 6.dp)
    val borderWidth = if (isPaper) 2.dp else 1.dp
    // The label sits on the top border. Move the box down by half the label height.
    val labelInset = if (label != null) 8.dp else 0.dp

    Box(modifier.padding(end = if (isPaper) 3.dp else 0.dp, bottom = if (isPaper) 4.dp else 0.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .padding(top = labelInset)
                .then(if (isPaper) Modifier.hardShadow(shape, p.ink) else Modifier)
                .background(p.bg, shape)
                .border(borderWidth, p.ink, shape)
                .padding(contentPadding),
            content = content
        )
        if (label != null) {
            // The background of the label hides the border line under the label.
            StyleLabel(
                label,
                Modifier
                    // The end padding keeps a long label off the right corner of the box.
                    .padding(start = 14.dp, end = 8.dp)
                    .background(p.bg)
                    .padding(horizontal = 6.dp)
            )
        }
    }
}

/** Draw a copy of the shape 3dp right and 4dp down, with no blur. */
private fun Modifier.hardShadow(shape: Shape, color: Color): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(left = 3.dp.toPx(), top = 4.dp.toPx()) { drawOutline(outline, color) }
}

/**
 * One value with a label. [note] is an optional short line under the value. A null note shows no line.
 * When [onNoteClick] is set, a tap on the note calls it.
 */
data class Stat(val label: String, val value: String, val note: String? = null, val onNoteClick: (() -> Unit)? = null)

/**
 * Two stats side by side. Paper and Mono use two boxes. Quiet uses hairlines.
 * The row has the height of the taller stat (IntrinsicSize.Min), and each box fills that height,
 * so both tiles have the same height also when only one has a note.
 */
@Composable
fun StatPair(first: Stat, second: Stat, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    if (LocalUiStyle.current == UiStyle.QUIET) {
        Column(modifier) {
            HorizontalDivider(thickness = 1.dp, color = p.line)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                StatContent(first, withLabel = true, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                VerticalDivider(thickness = 1.dp, color = p.line)
                StatContent(
                    second,
                    withLabel = true,
                    modifier = Modifier.weight(1f).padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
                )
            }
            HorizontalDivider(thickness = 1.dp, color = p.line)
        }
    } else {
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // fillHeight: both tiles get the height of the taller one.
            StyleBox(Modifier.weight(1f), label = first.label, fillHeight = true) { StatContent(first, withLabel = false) }
            StyleBox(Modifier.weight(1f), label = second.label, fillHeight = true) { StatContent(second, withLabel = false) }
        }
    }
}

@Composable
private fun StatContent(stat: Stat, withLabel: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (withLabel) StyleLabel(stat.label, Modifier.padding(bottom = 4.dp))
        // The value is always one line. A long value (for example "23 t 59 min") shrinks to fit the tile width.
        // Material3 Text has no autoSize in this version, so use BasicText. It does not read LocalContentColor.
        val valueStyle = MaterialTheme.typography.headlineMedium
        BasicText(
            stat.value,
            style = valueStyle.copy(color = LocalContentColor.current),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = valueStyle.fontSize, stepSize = 0.5.sp)
        )
        val note = stat.note
        if (note != null) {
            val onNoteClick = stat.onNoteClick
            Text(
                note,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = LocalPalette.current.muted,
                modifier = if (onNoteClick != null) Modifier.clickable(onClick = onNoteClick) else Modifier
            )
        }
    }
}

/**
 * Seven squares for Monday to Sunday. [underLimit] has one value for each day from Monday to today.
 * An ink square is a day under the limit. A square in the empty color is a day over the limit.
 * A square with only a border is a day with no data (null), or a day that did not come yet.
 */
@Composable
fun WeekSquares(underLimit: List<Boolean?>, modifier: Modifier = Modifier, squareSize: Dp = 14.dp) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(3.dp)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (day in 0 until 7) {
            val under = underLimit.getOrNull(day)
            val fill = when (under) {
                true -> Modifier.background(p.ink, shape)
                false -> Modifier.background(p.empty, shape)
                null -> Modifier.border(1.dp, p.line, shape)
            }
            Box(Modifier.size(squareSize).then(fill))
        }
    }
}

/** The line that shows the active focus profile. Paper and Mono use a box. Quiet uses an italic line. */
@Composable
fun FocusBanner(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    if (LocalUiStyle.current == UiStyle.QUIET) {
        Text(
            text,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = p.ink
        )
    } else {
        StyleBox(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = p.ink)
        }
    }
}
