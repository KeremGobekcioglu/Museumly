package com.kg.museumly.presentation.feature.eras.era

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.Section
import com.kg.museumly.presentation.BuildConfig
import com.kg.museumly.presentation.feature.eras.components.CaptionMetaStyle
import com.kg.museumly.presentation.feature.eras.components.HeroCapsStyle
import com.kg.museumly.presentation.feature.eras.components.HeroParts
import com.kg.museumly.presentation.feature.eras.components.eraSpan
import com.kg.museumly.presentation.feature.eras.components.heroParts
import com.kg.museumly.presentation.feature.scroll.presentation.TailState
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryLoading
import com.kg.museumly.presentation.feature.scroll.presentation.components.GalleryNotice
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsGutter
import com.kg.museumly.presentation.feature.scroll.presentation.components.SectionPicker
import com.kg.museumly.presentation.feature.scroll.presentation.components.TopBarChipOutline
import com.kg.museumly.presentation.feature.scroll.presentation.components.WallColor
import com.kg.museumly.presentation.feature.scroll.presentation.failedBody
import com.kg.museumly.presentation.feature.scroll.presentation.failedTitle
import com.kg.museumly.presentation.ui.theme.LibreCaslon
import com.kg.museumly.presentation.ui.theme.White60

/** Names the room above the wall: smaller than the timeline's hero. */
private val EraTitleStyle: TextStyle = TextStyle(
    fontFamily = LibreCaslon,
    fontWeight = FontWeight.Normal,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    color = Color.White,
)

@Composable
fun EraScreen(
    state: EraUiState,
    onIntent: (EraIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WallColor)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ReelsGutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackChip(onClick = { onIntent(EraIntent.BackClicked) })
            SectionPicker(
                selected = state.selected,
                allLabel = "All Galleries",
                sections = state.sections,
                onSectionSelected = { picked: Section? ->
                    onIntent(EraIntent.SectionPicked(picked))
                },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = 8.dp)
            )
        }
        EraHeader(
            era = state.era,
            modifier = Modifier.padding(start = ReelsGutter, end = ReelsGutter, bottom = 16.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val index: Int? = state.initialIndex
            val tail: TailState = state.tail
            if (index != null && state.artworks.isNotEmpty()) {
                key(state.selected) {
                    EraGrid(
                        artworks = state.artworks,
                        initialIndex = index,
                        onIntent = onIntent,
                        footer = {
                            EraTail(
                                tail = state.tail,
                                isOnline = state.isOnline,
                                eraLabel = eraSpan(state.era),
                                onRetry = { onIntent(EraIntent.RetryClicked) },
                            )
                        },
                    )
                }
            } else if (state.artworks.isEmpty() && tail is TailState.Failed) {
                GalleryNotice(
                    title = failedTitle(state.isOnline, false),
                    body = failedBody(state.isOnline, false),
                    actionLabel = if (state.isOnline) "Retry" else "Try anyway",
                    onAction = { onIntent(EraIntent.RetryClicked) },
                    isBusy = tail.retrying,
                    debugDetail = if (BuildConfig.DEBUG) tail.message else null,
                    onBack = { onIntent(EraIntent.BackClicked) },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (state.artworks.isEmpty() && tail is TailState.Exhausted) {
                GalleryNotice(
                    title = "Nothing from these years",
                    body = "No works came back from " + eraSpan(state.era) + ".",
                    onBack = { onIntent(EraIntent.BackClicked) },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                GalleryLoading(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun EraHeader(era: Era, modifier: Modifier = Modifier) {
    val parts: HeroParts = heroParts(era)
    val title: AnnotatedString = buildAnnotatedString {
        val prefix: String? = parts.prefix
        if (prefix != null) {
            withStyle(HeroCapsStyle) { append(prefix) }
            append(" ")
        }
        append(parts.number)
        val suffix: String? = parts.suffix
        if (suffix != null) {
            append(" ")
            withStyle(HeroCapsStyle) { append(suffix) }
        }
    }
    Column(modifier = modifier) {
        Text(text = title, style = EraTitleStyle)
        Text(text = eraSpan(era), style = CaptionMetaStyle.copy(textAlign = TextAlign.Start))
    }
}

/** Same outline as the picker and the favorites sort chip. */
@Composable
private fun BackChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        border = BorderStroke(1.dp, TopBarChipOutline),
        contentColor = Color.White,
        shape = CircleShape
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier
                .padding(8.dp)
                .size(20.dp)
        )
    }
}

/**
 * The end of the era wall: loading, retry, or the end of the room.
 * Idle shows nothing; the next load starts before the user gets here.
 */
@Composable
internal fun EraTail(
    tail: TailState,
    isOnline: Boolean,
    eraLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        if (tail is TailState.Loading) {
            CircularProgressIndicator(
                color = White60,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        } else if (tail is TailState.Failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    // Offline: the ViewModel retries on reconnect by itself.
                    text = if (isOnline) "Couldn't load more works" else "Offline. More will load when you're back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = White60
                )
                Spacer(modifier = Modifier.height(12.dp))
                RetryChip(
                    retrying = tail.retrying,
                    onClick = onRetry
                )
            }
        } else if (tail is TailState.Exhausted) {
            Text(
                text = "That's everything from " + eraLabel,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

/** Same outline as the back chip and the picker. */
@Composable
private fun RetryChip(
    retrying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        // While a retry runs: not tappable, and the label says so. The
        // ViewModel keeps it visible at least 600 ms, like the feed.
        enabled = !retrying,
        modifier = modifier,
        color = Color.Transparent,
        border = BorderStroke(1.dp, TopBarChipOutline),
        contentColor = Color.White,
        shape = CircleShape
    ) {
        Text(
            text = if (retrying) "Retrying…" else "Retry",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}