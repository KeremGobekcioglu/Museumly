package com.kg.museumly.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.feature.scroll.presentation.components.GalleryLoading
import com.kg.museumly.feature.scroll.presentation.components.GalleryNotice
import com.kg.museumly.feature.scroll.presentation.components.GalleryPlacard

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
        title = "You've walked the whole gallery",
        body = "That's every work we have in Islamic. " +
            "Thank you for taking the time to look. " +
            "Another gallery is waiting in the menu above.",
    )
}

@Preview
@Composable
private fun GalleryLoadingPreview() {
    GalleryLoading()
}