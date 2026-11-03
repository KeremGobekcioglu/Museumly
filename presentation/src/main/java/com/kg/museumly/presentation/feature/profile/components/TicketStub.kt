package com.kg.museumly.presentation.feature.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TicketStub(
    isSignedIn: Boolean,
    hasGame: Boolean,
    visitDay: String?,
    visitYear: String?,
    scoreText: String?,
    streakText: String?,
) {
    val title: String
    val subtitle: String
    val note: String?
    if (hasGame) {
        title = "Today’s game"
        subtitle = "Guess the era"
        if (isSignedIn) {
            note = null
        } else {
            note = "Sign in to play"
        }
    } else {
        title = "Today’s visit"
        subtitle = "A new stamp every day you come"
        note = null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(StubHeight)
            .padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, fontFamily = TicketSerif, fontSize = 19.sp, color = TicketInk)
            Text(text = subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = TicketMuted)
            if (note != null) {
                Text(text = note, fontSize = 13.sp, color = TicketMuted)
            }
        }

        if (!hasGame) {
            CircleInkStamp(topText = "Museumly", mainText = visitDay, bottomText = visitYear)
        } else if (isSignedIn && scoreText != null) {
            CircleInkStamp(mainText = scoreText, bottomText = streakText)
        } else {
            EmptyStamp(text = "No score yet")
        }
    }
}