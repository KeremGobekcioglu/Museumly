//package com.kg.museumly.feature.scroll.presentation.components
//
//import android.content.ContentValues.TAG
//import android.util.Log
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.interaction.MutableInteractionSource
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.BoxWithConstraints
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.safeDrawingPadding
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.layout.widthIn
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.dp
//import coil3.compose.AsyncImage
//import coil3.compose.AsyncImagePainter
//import com.kg.museumly.model.Artwork
//
//@Composable
//internal fun ArtworkPageWithRespectToAspectRatio(
//    artwork: Artwork,
//    modifier: Modifier = Modifier,
//    onDetailPage: (String) -> Unit,
//) {
//    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
//            val containerRatio = maxWidth / maxHeight
//            val aspectRatio = artwork.aspectRatio ?: containerRatio
//            val (imageWidth, imageHeight) = if (aspectRatio > containerRatio) {
//                maxWidth to maxWidth / aspectRatio
//            } else {
//                maxHeight * aspectRatio to maxHeight
//            }
//
//            var loadFailed by remember(artwork.id) { mutableStateOf(false) }
//            Text(
//                text = sourceLabel(artwork),
//                color = Color.White,
//                maxLines = 1,
//                overflow = TextOverflow.Ellipsis,
//                modifier = Modifier
//                    .align(Alignment.TopEnd)
//                    .safeDrawingPadding()
//                    .padding(16.dp)
//                    .widthIn(max = maxWidth * 0.4f)
//            )
//            AsyncImage(
//                model = artwork.imageUrl,
//                contentDescription = artwork.title,
//                onState = { state ->
//                    if (state is AsyncImagePainter.State.Error) {
//                        Log.e(TAG, "Failed to load ${artwork.id}: ${artwork.imageUrl}", state.result.throwable)
//                        loadFailed = true
//                    }
//                },
//                modifier = Modifier
//                    .align(Alignment.Center)
//                    .width(imageWidth)
//                    .height(imageHeight)
//                    .clickable(
//                        interactionSource = remember { MutableInteractionSource() },
//                        indication = null,
//                    ) { onDetailPage(artwork.id) }
//            )
//
//            if (loadFailed) {
//                Text(
//                    text = "Couldn't load this image",
//                    color = Color.Red,
//                    style = MaterialTheme.typography.bodyMedium,
//                    modifier = Modifier.align(Alignment.Center),
//                )
//            }
//        }
//
//        ArtworkCaption(artwork = artwork, modifier = Modifier.align(Alignment.BottomStart))
//    }
//}
//
//@Composable
//internal fun ArtworkPageWithoutAspectRatio(
//    artwork: Artwork,
//    modifier: Modifier = Modifier,
//    onDetailPage: (String) -> Unit,
//)  {
//    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//        var loadFailed by remember(artwork.id) { mutableStateOf(false) }
//        AsyncImage(
//            model = artwork.imageUrl,
//            contentDescription = artwork.title,
//            onState = { state ->
//                if (state is AsyncImagePainter.State.Error) {
//                    Log.e(TAG, "Failed to load ${artwork.id}: ${artwork.imageUrl}", state.result.throwable)
//                    loadFailed = true
//                }
//            },
//            modifier = Modifier.fillMaxSize(),
//            contentScale = ContentScale.Fit
//        )
//        Text(
//            text = sourceLabel(artwork),
//            color = Color.White,
//            maxLines = 1,
//            overflow = TextOverflow.Ellipsis,
//            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
//        )
//        if (loadFailed) {
//            Text(
//                text = "Couldn't load this image",
//                color = Color.Red,
//                style = MaterialTheme.typography.bodyMedium,
//                modifier = Modifier.align(Alignment.Center),
//            )
//        }
//        ArtworkCaption(artwork = artwork, modifier = Modifier.align(Alignment.BottomStart))
//    }
//}