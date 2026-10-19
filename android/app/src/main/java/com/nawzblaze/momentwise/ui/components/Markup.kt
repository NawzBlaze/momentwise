package com.nawzblaze.momentwise.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Renders the small subset of markup used in Momentwise copy: `<b>…</b>`.
 * Anything else is shown as plain text. Avoids pulling in an HTML renderer
 * for a handful of emphasis spans.
 */
fun simpleMarkup(source: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < source.length) {
        val open = source.indexOf("<b>", index)
        if (open < 0) {
            append(source.substring(index))
            break
        }
        append(source.substring(index, open))
        val close = source.indexOf("</b>", open + 3)
        if (close < 0) {
            append(source.substring(open))
            break
        }
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(source.substring(open + 3, close))
        }
        index = close + 4
    }
}

@Composable
fun MarkupText(
    source: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Text(
        text = simpleMarkup(source),
        modifier = modifier,
        color = color,
        style = style,
    )
}
