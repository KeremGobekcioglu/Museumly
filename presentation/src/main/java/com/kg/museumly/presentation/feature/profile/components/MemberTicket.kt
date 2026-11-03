package com.kg.museumly.presentation.feature.profile.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.NumberFormat

// ---- Tokens (move into your theme later) ----

val TicketPaper: Color = Color(0xFFECE4D3)
internal val TicketInk: Color = Color(0xFF1F1B16)
private val TicketBody: Color = Color(0xFF4A443B)
internal val TicketMuted: Color = Color(0xFF6A6255)
private val StampInk: Color = Color(0xFF9C3D24)

// TODO: swap for your bundled Libre Caslon FontFamily
internal val TicketSerif: FontFamily = FontFamily.Serif

private val TicketCorner: Dp = 20.dp
private val NotchRadius: Dp = 12.dp
internal val PerforationHeight: Dp = 20.dp
val StubHeight: Dp = 148.dp
private val StampSize: Dp = 112.dp
@Composable
fun MemberTicket(
    isSignedIn: Boolean,
    hasGame: Boolean,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
    name: String? = null,
    photoUrl: String? = null,
    memberNumber: String? = null,
    memberSince: String? = null,
    visitDay: String? = null,
    visitYear: String? = null,
    scoreText: String? = null,
    streakText: String? = null,
) {
    val shape: TicketShape = remember {
        TicketShape(
            cornerRadius = 20.dp,
            notchRadius = 12.dp,
            notchCenterFromBottom = StubHeight + PerforationHeight / 2,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = TicketPaper, shape = shape)
    ) {
        TicketHeader(
            isSignedIn = isSignedIn,
            name = name,
            photoUrl = photoUrl,
            memberNumber = memberNumber,
            memberSince = memberSince,
            onSignInClick = onSignInClick,
        )
        DashedLines()
        TicketStub(
            isSignedIn = isSignedIn,
            hasGame = hasGame,
            visitDay = visitDay,
            visitYear = visitYear,
            scoreText = scoreText,
            streakText = streakText,
        )
    }
}

@Preview(name = "In · no game", showBackground = true, backgroundColor = 0xFF000000, widthDp = 390)
@Composable
private fun TicketInNoGamePreview() {
    Box(modifier = Modifier.padding(24.dp)) {
        MemberTicket(
            isSignedIn = true, hasGame = false, onSignInClick = {},
            name = "Kerem Göbekcioğlu", memberNumber = "0412", memberSince = "November 2026",
            visitDay = "4 Nov", visitYear = "2026",
        )
    }
}

@Preview(name = "Out · no game", showBackground = true, backgroundColor = 0xFF000000, widthDp = 390)
@Composable
private fun TicketOutNoGamePreview() {
    Box(modifier = Modifier.padding(24.dp)) {
        MemberTicket(
            isSignedIn = false, hasGame = false, onSignInClick = {},
            visitDay = "4 Nov", visitYear = "2026",
        )
    }
}

@Preview(name = "In · game", showBackground = true, backgroundColor = 0xFF000000, widthDp = 390)
@Composable
private fun TicketInGamePreview() {
    Box(modifier = Modifier.padding(24.dp)) {
        MemberTicket(
            isSignedIn = true, hasGame = true, onSignInClick = {},
            name = "Kerem Göbekcioğlu", memberNumber = "0412", memberSince = "November 2026",
            scoreText = "1,240", streakText = "6-day streak",
        )
    }
}

@Preview(name = "Out · game", showBackground = true, backgroundColor = 0xFF000000, widthDp = 390)
@Composable
private fun TicketOutGamePreview() {
    Box(modifier = Modifier.padding(24.dp)) {
        MemberTicket(isSignedIn = false, hasGame = true, onSignInClick = {})
    }
}