package com.kg.museumly.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryLoading
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryNotice

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