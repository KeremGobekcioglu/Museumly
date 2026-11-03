package com.kg.museumly.presentation.feature.profile.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.NumberFormat

private val CouponStripHeight: Dp = 128.dp

@Composable
fun CouponStripRow(
    favoriteCount: Int,
    sharedCount: Int,
    onFavoritesClick: () -> Unit,
    onSharedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape: CouponStripShape = remember {
        CouponStripShape(cornerRadius = 16.dp, notchRadius = 10.dp)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CouponStripHeight)
            .clip(shape)
            .background(TicketPaper)
    ) {
        Coupon(
            label = "Favorites",
            count = favoriteCount,
            oneText = "work kept",
            manyText = "works kept",
            emptyHint = "Tap the heart on a work",
            onClick = onFavoritesClick,
            modifier = Modifier.weight(1f),
        )
        VerticalDashedLines()
        Coupon(
            label = "Shared",
            count = sharedCount,
            oneText = "card sent",
            manyText = "cards sent",
            emptyHint = "Send a work as a card",
            onClick = onSharedClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Coupon(
    label: String,
    count: Int,
    oneText: String,
    manyText: String,
    emptyHint: String,
    onClick: () -> Unit,
    modifier: Modifier,
)
{
    val value: String
    val hint: String
    val valueSize: TextUnit
    if (count == 0) {
        value = "None yet"
        hint = emptyHint
        valueSize = 20.sp
    } else {
        value = count.toString()
        if (count == 1) {
            hint = oneText
        } else {
            hint = manyText
        }
        valueSize = 34.sp
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontSize = 13.sp, color = TicketMuted)
            Spacer(modifier = Modifier.width(4.dp))
            ChevronMark()
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = value, fontFamily = TicketSerif, fontSize = valueSize, color = TicketInk)
            Text(text = hint, fontSize = 12.sp, lineHeight = 16.sp, color = TicketMuted)
        }
    }
}

@Composable
internal fun ChevronMark(color: Color = TicketMuted)
{
    Canvas(
        modifier = Modifier.size(10.dp)
    ) {
        val path: Path = Path()
        path.moveTo(size.width * 0.35f, size.height * 0.15f)
        path.lineTo(size.width * 0.7f, size.height * 0.5f)
        path.lineTo(size.width * 0.35f, size.height * 0.85f)
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun VerticalDashedLines() {
    Canvas(
        modifier = Modifier
            .width(2.dp)
            .fillMaxHeight()
            .padding(vertical = 16.dp)
    ) {
        val x: Float = size.width / 2f
        drawLine(
            color = TicketInk.copy(alpha = 0.26f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
    }
}

@Preview(name = "Coupon strip", showBackground = true, backgroundColor = 0xFF000000, widthDp = 390)
@Composable
private fun CouponStripPreview() {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CouponStripRow(favoriteCount = 24, sharedCount = 7, onFavoritesClick = {}, onSharedClick = {})
        CouponStripRow(favoriteCount = 1, sharedCount = 0, onFavoritesClick = {}, onSharedClick = {})
        CouponStripRow(favoriteCount = 0, sharedCount = 0, onFavoritesClick = {}, onSharedClick = {})
    }
}