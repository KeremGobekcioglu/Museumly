package com.kg.museumly.feature.detail

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.model.Artwork
import com.kg.museumly.model.ArtworkDetail

private val LabelInk: Color = Color(0xFFE8E3D9)
private const val TAG = "WallLabel"

// Slightly lighter than the wall and brighter at the top: the label is
// lit from above like the work, just less directly.
private val PanelSurface: Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1D1B18),
        Color(0xFF131210),
    ),
)

/**
 * The museum's tombstone label, printed straight onto the wall below the
 * work: attribution, title, date, then the catalogue lines. Light ink on the
 * dark wall and no card — the hierarchy comes from the type, not a box.
 *
 * When no artist is known (much of the Met's ancient and Islamic material),
 * culture and period take the artist's place, the way a real label does.
 */

@Composable
fun WallLabel(
    artwork: Artwork,
    detail: ArtworkDetail,
    modifier: Modifier = Modifier
)
{
    val artist = clean(artwork.artist)
    val origin = joinClean(clean(detail.culture) , clean(detail.period))
    val attribution = artist ?: origin
    val originLine = if (artist != null) origin else null

    Column(
        modifier = modifier
            .background(brush = PanelSurface)
            .drawBehind { drawFrameEdges(strength = 0.6f) }
            .padding(24.dp),
    ) {
        attribution?.let {
            Log.d(TAG, "attribution: $attribution")
            Text(
                text = attribution.uppercase(),
                color = LabelInk.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.sp,
            )
        }

        Log.d(TAG, "title: ${artwork.title}")
        Text(
            text = artwork.title,
            color = LabelInk,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.padding(top = 8.dp),
        )

        val year = clean(artwork.year)
        year?.let {
            Log.d(TAG, "year: $year")
            Text(
                text = year,
                color = LabelInk.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // A short rule between who and what the work is, and its record.
        Box(
            modifier = Modifier
                .padding(top = 20.dp)
                .size(width = 32.dp, height = 1.dp)
                .background(LabelInk.copy(alpha = 0.25f)),
        )

        LabelLine(value = detail.medium, topPadding = 20.dp)
        LabelLine(value = detail.dimensions)
        LabelLine(value = originLine)
        LabelLine(
            value = detail.creditLine,
            alpha = 0.45f,
            style = MaterialTheme.typography.bodySmall,
            topPadding = 16.dp,
        )
    }
}

@Composable
fun LabelLine(
    value: String?,
    alpha: Float = 0.7f,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    topPadding: Dp = 8.dp,
)
{
    val text: String = clean(value) ?: return
    Log.d(TAG, "line: $text")
    Text(
        text = text,
        color = LabelInk.copy(alpha = alpha),
        style = style,
        modifier = Modifier.padding(top = topPadding),
    )
}


// The APIs return "" for missing fields as often as null.
private fun clean(value: String?): String? {
    if (value == null) {
        return null
    }
    val trimmed: String = value.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    return trimmed
}

private fun joinClean(first: String? , second: String? ) : String?
{
    if( first != null && second != null)
    {
        return "$first, $second"
    }
    if(first != null)
        return first
    return second
}