package com.kg.museumly.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.feature.scroll.presentation.components.GalleryNotice
import com.kg.museumly.feature.scroll.presentation.components.GalleryPlacard
import com.kg.museumly.feature.scroll.presentation.components.GalleryWall

//@Preview
//@Composable
//private fun GalleryPlacardPreview() {
//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(Color(0xFF0D0C0B)),
//        contentAlignment = Alignment.Center,
//    ) {
//        GalleryPlacard(
//            title = "No connection",
//            body = "Check your connection and try again.",
//            actionLabel = "Retry",
//            onAction = {},
//        )
//    }
//}


@Preview
@Composable
private fun GalleryNoticeErrorPreview() {
    GalleryNotice(
        title = "No connection",
        body = "Check your connection and try again.",
        actionLabel = "Retry",
        onAction = {},
    )
}

@Preview
@Composable
private fun GalleryNoticeTailPreview() {
    GalleryNotice(
        title = "End of the gallery",
        body = "You've seen everything here.",
    )
}