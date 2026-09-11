package com.kg.museumly.feature.scroll.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PlacardSurface = Color(0xFFE8E3D9)
private val PlacardInk = Color(0xFF1A1713)

/**
 * A museum wall label. Cream card, dark ink, left-aligned, deliberately small —
 * on a dark wall this reads as a placard and nothing else does.
 *
 * Used for every non-loading state the feed can be in: cold failure, cold empty,
 * tail failure, exhausted. The action is optional because only the cold states
 * offer Retry — on the tail the user can swipe off and back, which re-fires the
 * LaunchedEffect and reloads without a button.
 */
@Composable
fun GalleryPlacard(
    title: String,
    body: String,
    actionLabel: String? = null,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null
)
{
    Column(
        modifier = modifier
            .widthIn(280.dp)
            .background(color = PlacardSurface, shape = RoundedCornerShape(2.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {

        Text(
            text = title,
            color = PlacardInk,
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 0.15.sp
        )

        Text(
            text = body,
            color = PlacardInk.copy(0.7f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )

        if(actionLabel != null && onAction != null)
        {
            val interactionSource = remember { MutableInteractionSource() }
            Text(
                text = actionLabel,
                color = PlacardInk,
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onAction
                    )
            )
        }
    }
}