package com.kg.museumly.presentation.feature.scroll.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val WallColor = Color(0xFF0D0C0B)

@Composable
fun GalleryNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    isBusy: Boolean = false,
    debugDetail: String? = null,
    onBack: (() -> Unit)? = null
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WallColor)
            .safeDrawingPadding(),
    ) {
        val geometry = galleryGeometry(width = maxWidth, height = maxHeight)

        PendantLamp(geometry = geometry, modifier = Modifier.fillMaxSize())

        // offset, not padding: padding participates in measurement and would
        // put these back in a chain with each other.
        EmptyFrame(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = geometry.frameTop)
                .size(width = geometry.frameWidth, height = geometry.frameHeight),
        )

        GalleryPlacard(
            title = title,
            body = body,
            actionLabel = actionLabel,
            onAction = onAction,
            isBusy = isBusy,
            debugDetail = debugDetail,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = geometry.placardTop),
        )

        // Same placement as DetailScreen's back button; the lamp hangs from
        // TopCenter, so the corner is free.
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
        }
    }
}
