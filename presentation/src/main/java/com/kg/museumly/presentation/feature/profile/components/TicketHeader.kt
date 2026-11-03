package com.kg.museumly.presentation.feature.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
internal fun TicketHeader(
    isSignedIn: Boolean, name: String?, photoUrl: String?,
    memberNumber: String? = null,
    memberSince: String? = null,
    onSignInClick: () -> Unit,
)
{
    val passKind: String
    val holder: String
    if (isSignedIn) {
        passKind = "Membership"
        if (name != null) {
            holder = name
        } else {
            holder = "Member"
        }
    } else {
        passKind = "Day ticket"
        holder = "Day visitor"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = "Museumly", fontFamily = TicketSerif, fontSize = 17.sp, color = TicketInk)
            Text(text = passKind, fontSize = 13.sp, color = TicketMuted)
        }

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isSignedIn) {
                ProfilePhoto(photoUrl = photoUrl)
            } else {
                EmptyPhoto()
            }
            Text(
                text = holder,
                fontFamily = TicketSerif,
                fontSize = 25.sp,
                lineHeight = 30.sp,
                color = TicketInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))

        if (isSignedIn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TicketField(label = "Member no.", value = memberNumber, modifier = Modifier.weight(1f))
                TicketField(label = "Valid from", value = memberSince, modifier = Modifier.weight(1f))
            }
        } else {
            Text(
                text = "Sign in to turn this into a membership. Favorites on this phone come with you.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = TicketBody,
            )
            Spacer(modifier = Modifier.height(12.dp))
            /***
             * we could use GoogleSignInButton from AuthActions.kt.
             */
            Button(
                onClick = onSignInClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TicketInk,
                    contentColor = TicketPaper,
                ),
            ) {
                Text(text = "Sign in", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ProfilePhoto(photoUrl: String?) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2420))
    ) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EmptyPhoto() {
    Box(
        modifier = Modifier
            .size(68.dp)
            .drawBehind {
                drawRoundRect(
                    color = TicketInk.copy(alpha = 0.35f),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
    )
}

private val TicketBody: Color = Color(0xFF4A443B)

@Composable
private fun TicketField(label: String, value: String?, modifier: Modifier) {
    val shown: String
    if (value != null) {
        shown = value
    } else {
        shown = "—"
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 12.sp, color = TicketMuted)
        Text(text = shown, fontSize = 18.sp, color = TicketInk)
    }
}