package com.kg.museumly.presentation.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.presentation.R
import com.kg.museumly.presentation.feature.share.platform.ShareTarget
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Facebook
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.brands.Whatsapp

/** One round tile in the sheet's row of destinations. */
class ShareOption(
    val target: ShareTarget,
    val label: String,
    val icon: ImageVector,
    val background: Brush,
)

// The app is always dark (see MainActivity), so the sheet is too, whatever the system theme.
internal val SheetColor = Color(0xFF181715)
private val SheetBorder = Color.White.copy(alpha = 0.12f)
private val SheetAccent = Color(0xFFE8D9B5)

private val InstagramGradient = Brush.linearGradient(
    listOf(Color(0xFFFEDA75), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5))
)
private val WhatsAppGreen = SolidColor(Color(0xFF25D366))
private val FacebookBlue = SolidColor(Color(0xFF1877F2))
private val NeutralTile = SolidColor(Color.White.copy(alpha = 0.12f))

/** Brand-colored tiles with the brands' own glyphs (Font Awesome, via compose-icons). */
@Composable
fun rememberShareOptions(): List<ShareOption> = listOf(
    ShareOption(
        target = ShareTarget.InstagramStories,
        label = stringResource(R.string.share_instagram_stories),
        icon = FontAwesomeIcons.Brands.Instagram,
        background = InstagramGradient,
    ),
    ShareOption(
        target = ShareTarget.WhatsApp,
        label = stringResource(R.string.share_whatsapp),
        icon = FontAwesomeIcons.Brands.Whatsapp,
        background = WhatsAppGreen,
    ),
    ShareOption(
        target = ShareTarget.Facebook,
        label = stringResource(R.string.share_facebook),
        icon = FontAwesomeIcons.Brands.Facebook,
        background = FacebookBlue,
    ),
    ShareOption(
        target = ShareTarget.SystemSheet,
        label = stringResource(R.string.share_more),
        icon = Icons.Outlined.Share,
        background = NeutralTile,
    ),
)

/**
 * @param preview already-scaled preview of the card (see ScaledShareCard) — not the raw card,
 * whose fixed dp children would clip if squeezed in here.
 * @param enabled false while the card is still loading or an export is running.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheet(
    preview: @Composable () -> Unit,
    enabled: Boolean,
    statusMessage: String?,
    isError: Boolean,
    options: List<ShareOption>,
    onOptionClick: (ShareTarget) -> Unit,
    onShareTextClick: () -> Unit,
    onSaveImageClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = SheetColor,
        tonalElevation = 0.dp,
    ) {
        ShareBottomSheetContent(
            preview = preview,
            enabled = enabled,
            statusMessage = statusMessage,
            isError = isError,
            options = options,
            onOptionClick = onOptionClick,
            onShareTextClick = onShareTextClick,
            onSaveImageClick = onSaveImageClick,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The sheet's scrollable body, split out of [ShareBottomSheet] so it can be previewed:
 * ModalBottomSheet renders through a Popup, which Android Studio's static preview never
 * captures, so previewing the sheet itself always comes out blank. Preview this instead,
 * on a [SheetColor] background.
 */
@Composable
fun ShareBottomSheetContent(
    preview: @Composable () -> Unit,
    enabled: Boolean,
    statusMessage: String?,
    isError: Boolean,
    options: List<ShareOption>,
    onOptionClick: (ShareTarget) -> Unit,
    onShareTextClick: () -> Unit,
    onSaveImageClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { preview() }

        Text(
            text = stringResource(R.string.share_title),
            color = SheetAccent,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement =
                if (options.size <= 2) Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)
                else Arrangement.SpaceBetween,
        ) {
            options.forEach { option ->
                OptionTile(option, enabled) { onOptionClick(option.target) }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = SheetBorder)

        ActionRow(Icons.Outlined.Download, stringResource(R.string.share_save_image), enabled, onSaveImageClick)

        if (statusMessage != null) {
            Text(
                text = statusMessage,
                color = if (isError) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        HorizontalDivider(color = SheetBorder)
        Text(
            text = stringResource(R.string.share_close),
            color = SheetAccent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDismiss)
                .padding(vertical = 20.dp),
        )
    }
}

@Composable
private fun OptionTile(option: ShareOption, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(option.background)
                // No overlays or shadows in the design; alpha is the disabled signal.
                //.alpha(if (enabled) 1f else 0.4f)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.label,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = option.label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SheetAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}
