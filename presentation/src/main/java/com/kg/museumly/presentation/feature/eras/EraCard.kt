package com.kg.museumly.presentation.feature.eras

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.domain.model.Era
import com.kg.museumly.domain.model.TimelineStop
import com.kg.museumly.presentation.feature.scroll.presentation.components.ReelsGutter
import com.kg.museumly.presentation.feature.scroll.presentation.components.museumName
import com.kg.museumly.presentation.ui.theme.White40
import kotlin.math.abs

/**
 * One era as a single piece: its works, the era, the caption, the pill.
 * Reads only its own group, so while it turns away it keeps showing its own
 * era, work and caption.
 */
@Composable
internal fun EraCard(
    group: EraGroup,
    isShownEra: () -> Boolean,
    isLoading: Boolean,
    hasError: Boolean,
    captionBlockHeight: Dp,
    nudgedEras: MutableSet<Era>,
    onIntent: (ErasIntent) -> Unit,
)
{
    // The work on screen, as a page of this era's works pager.
    var page: Int by remember { mutableIntStateOf(0) }

    // Reads how far the works pager is between two works, for the caption's
    // fade. Read in the draw phase, so following the finger never recomposes.
    var worksOffset: () -> Float by remember { mutableStateOf<() -> Float>({ 0f }) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            EraWorks(
                group = group,
                isShownEra = isShownEra,
                isLoading = isLoading,
                hasError = hasError,
                nudgedEras = nudgedEras,
                onIntent = onIntent,
                onVisibleIndex = { index: Int -> page = index - group.firstIndex },
                onWorksOffset = { reader: () -> Float -> worksOffset = reader }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        EraHero(
            era = group.era,
            hasWorks = group.stops.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ReelsGutter)
        )

        Spacer(modifier = Modifier.height(8.dp))
        // Frozen per work, so a caption fading out keeps its own stop.
        var captionTarget: CaptionTarget? = null
        val visibleStop: TimelineStop? = group.stops.getOrNull(page)
        if (visibleStop != null) {
            captionTarget = CaptionTarget(stop = visibleStop)
        }
        AnimatedContent(
            targetState = captionTarget,
            contentKey = { target: CaptionTarget? -> target?.stop?.artwork?.id },
            transitionSpec = {
                EnterTransition.None togetherWith ExitTransition.None
            },
            label = "eraCaption",
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .height(captionBlockHeight)
                .padding(horizontal = ReelsGutter)
        ) { target: CaptionTarget? ->
            if (target != null) {
                // Fades out as the image slides away and in as the next
                // one slides in: invisible at the halfway point, where
                // the text swaps. Driven by the finger, so it can't
                // lead or lag.
                EraCaption(
                    stop = target.stop,
                    modifier = Modifier.graphicsLayer {
                        val fraction: Float = abs(worksOffset())
                        alpha = (1f - 2.5f * fraction).coerceIn(0f, 1f)
                    }
                )
            }
        }

        // The pill sits in its own fixed row, so it never moves with the
        // caption. Its 48dp touch target adds about 10dp above the drawn
        // pill, so the 6dp spacer shows as a 16dp gap. The row keeps its
        // height in empty eras too, so the art never jumps.
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(PillRowHeight)
        ) {
            if (group.stops.isNotEmpty()) {
                OutlinedPill(
                    label = "More from " + eraSpan(group.era),
                    // Reads page on click, so it opens the work on screen
                    // without the pill recomposing on every swipe.
                    onClick = {
                        onIntent(ErasIntent.MoreClicked(group.firstIndex + page))
                    }
                )
            }
        }
    }
}

/**
 * The hero: the card's era. It turns with the card, so it has no motion of
 * its own. Dims to 40% while the era has no works, and brightens if they
 * arrive while the card is on screen.
 */
@Composable
private fun EraHero(
    era: Era,
    hasWorks: Boolean,
    modifier: Modifier = Modifier,
)
{
    val alpha: Float by animateFloatAsState(
        targetValue = if (hasWorks) 1f else 0.4f,
        animationSpec = tween(200),
        label = "heroAlpha"
    )
    val parts: HeroParts = heroParts(era)
    Text(
        text = heroText(parts),
        style = HeroNumberStyle,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clearAndSetSemantics { contentDescription = heroDescription(parts) }
    )
}

/** The work the caption shows. */
private class CaptionTarget(val stop: TimelineStop)

/**
 * Title in Libre Caslon, then "artist · year", then the museum in small
 * spaced caps, all centered. The title gets three lines and "artist · year"
 * one; a long one first shrinks to fit (title down to 15sp, "artist · year"
 * down to 11sp) and only then is ellipsized. The museum is never cut: it
 * wraps.
 */
@Composable
private fun EraCaption(
    stop: TimelineStop,
    modifier: Modifier = Modifier,
)
{
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        BasicText(
            text = stop.artwork.title,
            style = CaptionTitleStyle,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 19.sp)
        )
        val meta: String? = artistAndYear(stop.artwork)
        if (meta != null) {
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = meta,
                style = CaptionMetaStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 13.sp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = museumName(stop.artwork.providerId).uppercase(),
            color = White40,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            lineHeight = CaptionMuseumLineHeight,
            textAlign = TextAlign.Center
        )
    }
}
