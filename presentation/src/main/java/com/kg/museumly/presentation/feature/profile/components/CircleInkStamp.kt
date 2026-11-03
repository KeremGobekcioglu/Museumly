package com.kg.museumly.presentation.feature.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StampInk: Color = Color(0xFF9C3D24)
private val StampSize: Dp = 112.dp

/**
 * If game feature is oni top text is null.
 * main text becomes score , bottom text becomes streak.
 * If game feature is off,
 * top text is museumly. main text is date day - month or month - day.
 * botttom text is year.
 */
@Composable
internal fun CircleInkStamp(
    modifier: Modifier = Modifier,
    topText: String? = null,
    mainText: String? = null,
    bottomText: String? = null
)
{
    Column(
        modifier = modifier
            .size(StampSize)
            .graphicsLayer {
                rotationZ = -9f
                alpha = 0.92f
            }
            .drawBehind {
                val outerStroke = 3.dp.toPx()
                val innerStroke = 1.5.dp.toPx()
                val gap = 4.dp.toPx()
                val half = size.minDimension / 2f

                drawCircle(
                    color = StampInk,
                    radius = half - outerStroke / 2f,
                    style = Stroke(width = outerStroke)
                )
                drawCircle(
                    color = StampInk,
                    radius = half - outerStroke - gap - innerStroke / 2f,
                    style = Stroke(width = innerStroke)
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)

    ) {
        if (topText != null) {
            Text(text = topText, fontSize = 12.sp, color = StampInk)
        }
        if (mainText != null) {
            Text(text = mainText, fontFamily = TicketSerif, fontSize = 27.sp, lineHeight = 28.sp, color = StampInk)
        }
        if (bottomText != null) {
            Text(text = bottomText, fontSize = 12.sp, color = StampInk)
        }
    }
}

@Composable
internal fun EmptyStamp(
    text: String
)
{
    Box(
        modifier = Modifier
            .size(StampSize)
            .drawBehind {
                drawCircle(
                    color = TicketInk.copy(alpha = 0.3f),
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = TicketMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Stamp · date", showBackground = true, backgroundColor = 0xFFECE4D3)
@Composable
private fun InkStampDatePreview() {
    Box(modifier = Modifier.padding(16.dp)) {
        CircleInkStamp(topText = "Museumly", mainText = "4 Nov", bottomText = "2026")
    }
}

@Preview(name = "Stamp · score", showBackground = true, backgroundColor = 0xFFECE4D3)
@Composable
private fun InkStampScorePreview() {
    Box(modifier = Modifier.padding(16.dp)) {
        CircleInkStamp(mainText = "1,240", bottomText = "6-day streak")
    }
}

