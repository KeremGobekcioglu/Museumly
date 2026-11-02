package com.kg.museumly.presentation.feature.share

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.kg.museumly.presentation.R
import com.kg.museumly.presentation.feature.share.capture.CaptureHost
import com.kg.museumly.presentation.feature.share.capture.ScaledShareCard
import com.kg.museumly.presentation.feature.share.capture.ShareCardExportDensity
import com.kg.museumly.presentation.feature.share.capture.toPngBytes
import com.kg.museumly.domain.share.ShareManager
import com.kg.museumly.domain.share.ShareResult
import com.kg.museumly.domain.share.ShareTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.kg.museumly.domain.share.WallpaperTarget
private const val TAG = "ShareFlow"

/**
 * Compose can't `@Inject`, so the singleton [ShareManager] reaches the host through this.
 * Holds no state of its own.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    val shareManager: ShareManager,
) : ViewModel()

private enum class CardState { Loading, Ready, Failed }

/**
 * Shows the share sheet while [state] holds a [ShareContent]. Any screen opens the flow with
 * `state.open(content)`; this host owns the capture, the sheet and the dismiss wiring.
 *
 * Place it once, above the screens that can share, in a box that fills the window.
 */
@Composable
fun ShareFlowHost(
    state: ShareFlowState,
    modifier: Modifier = Modifier,
    shareManager: ShareManager = hiltViewModel<ShareViewModel>().shareManager,
) {
    // Outside the `content != null` block below so the snackbar survives state.dismiss():
    // dismiss unmounts that block, which would cancel a scope remembered inside it mid-animation.
    val snackbarScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.share_saved)
    val genericError = stringResource(R.string.share_error_generic)
    val imageError = stringResource(R.string.share_error_image)
    val preparing = stringResource(R.string.share_preparing)
    val wallpaperSetMessage: String = stringResource(R.string.wallpaper_set)
    val wallpaperError: String = stringResource(R.string.wallpaper_error)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val content = state.content
        if (content != null) {
            val layer = rememberGraphicsLayer()
            // Scoped to the open sheet: dies with it, so never launch the snackbar on this one.
            val shareScope = rememberCoroutineScope()
            var isWorking by remember { mutableStateOf(false) }
            var cardState by remember(content) { mutableStateOf(CardState.Loading) }
            var errorMessage by remember(content) { mutableStateOf<String?>(null) }

            val availableTargets = remember(shareManager) {
                ShareTarget.entries.filter(shareManager::isAvailable).toSet()
            }
            val canSetWallpaper: Boolean = remember(shareManager) { shareManager.canSetWallpaper() }
            val options = rememberShareOptions().filter { it.target in availableTargets }

            // Offscreen export copy. Reports its real size (a zero-sized node is skipped in the
            // draw phase, so nothing would be recorded) inside a 0dp box with unbounded wrapping
            // so the parent still reserves no space for it.
            Box(modifier = Modifier.size(0.dp).wrapContentSize(unbounded = true)) {
                CompositionLocalProvider(LocalDensity provides ShareCardExportDensity) {
                    CaptureHost(layer = layer) {
                        content.card { ok -> cardState = if (ok) CardState.Ready else CardState.Failed }
                    }
                }
            }

            fun perform(successMessage: String? = null, action: suspend (ByteArray) -> ShareResult) {
                if (isWorking || cardState != CardState.Ready) return // double-tap guard
                shareScope.launch {
                    isWorking = true
                    errorMessage = null
                    try {
                        if (layer.size.width == 0 || layer.size.height == 0) {
                            errorMessage = genericError
                            return@launch
                        }
                        val bitmap = layer.toImageBitmap()
                        // Encoding 1080x1920 takes a noticeable moment: keep it off the main thread.
                        val png = withContext(Dispatchers.Default) { bitmap.toPngBytes() }
                        when (val result = action(png)) {
                            ShareResult.Success -> {
                                state.dismiss()
                                if (successMessage != null) {
                                    snackbarScope.launch { snackbarHostState.showSnackbar(successMessage) }
                                }
                            }
                            // Target missing: the user asked to share, so give them the system
                            // sheet rather than an error. Reuses the same bytes.
                            ShareResult.TargetUnavailable -> {
                                if (shareManager.share(png, ShareTarget.SystemSheet) == ShareResult.Success) {
                                    state.dismiss()
                                } else {
                                    errorMessage = genericError
                                }
                            }
                            is ShareResult.Failed -> {
                                Log.w(TAG, "Share failed", result.cause)
                                errorMessage = genericError
                            }
                        }
                    } finally {
                        isWorking = false
                    }
                }
            }

            fun setWallpaper(url: String, target: WallpaperTarget) {
                if (isWorking) return // double-tap guard, shared with share and save
                shareScope.launch {
                    isWorking = true
                    errorMessage = null
                    try {
                        val result: ShareResult = shareManager.setWallpaper(url, target)
                        when (result) {
                            ShareResult.Success -> {
                                state.dismiss()
                                snackbarScope.launch { snackbarHostState.showSnackbar(wallpaperSetMessage) }
                            }
                            ShareResult.TargetUnavailable -> {
                                errorMessage = wallpaperError
                            }
                            is ShareResult.Failed -> {
                                Log.w(TAG, "Set wallpaper failed", result.cause)
                                errorMessage = wallpaperError
                            }
                        }
                    } finally {
                        isWorking = false
                    }
                }
            }

            ShareBottomSheet(
                preview = { ScaledShareCard { content.card {} } },
                enabled = !isWorking && cardState == CardState.Ready,
                statusMessage = when {
                    cardState == CardState.Failed -> imageError
                    errorMessage != null -> errorMessage
                    cardState == CardState.Loading -> preparing
                    else -> null
                },
                isError = cardState == CardState.Failed || errorMessage != null,
                options = options,
                onOptionClick = { target -> perform { shareManager.share(it, target) } },
                onSaveImageClick = {
                    perform(successMessage = savedMessage) { shareManager.saveToGallery(it) }
                },
                onShareTextClick = {
                    if (!isWorking) {
                        shareScope.launch {
                            val result = shareManager.shareText(content.text, ShareTarget.SystemSheet)
                            if (result is ShareResult.Failed) {
                                Log.w(TAG, "Share text failed", result.cause)
                                errorMessage = genericError
                            }
                        }
                    }
                },
                onDismiss = state::dismiss,
                showWallpaper = canSetWallpaper,
                wallpaperEnabled = !isWorking,
                onSetWallpaperClick = { target: WallpaperTarget ->
                    val url: String? = content.wallpaperUrl
                    if (url != null) {
                        setWallpaper(url, target)
                    }
                },
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.navigationBarsPadding())
    }
}
