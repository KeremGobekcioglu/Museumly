package com.kg.museumly.presentation.feature.scroll.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.presentation.feature.scroll.presentation.components.SheetHandle
import com.kg.museumly.presentation.ui.theme.LibreCaslon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


// Colours from board 08 on the First Run canvas.
private val SheetBackground: Color = Color(0xFF151413)
private val SheetHairline: Color = Color(0xFFEDE8DF).copy(alpha = 0.12f)
private val SheetHandle: Color = Color(0xFFEDE8DF).copy(alpha = 0.25f)
private val SheetTitle: Color = Color(0xFFF4EFE6)
private val SheetBody: Color = Color(0xFFC9C4BA)
private val SheetFootnote: Color = Color(0xFF9A958C)
private val PrimaryButtonBackground: Color = Color(0xFFF4EFE6)
private val PrimaryButtonText: Color = Color(0xFF141413)

// The feed behind the sheet sits at about 40% in the design.
private val SheetScrim: Color = Color.Black.copy(alpha = 0.6f)

private val SheetShape: RoundedCornerShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * Asked once, at the first favorite on the feed. Stateless: whether it shows,
 * and what "asked once" means, belong to the caller.
 *
 * Both buttons slide the sheet down first and only then call back, so the
 * caller's state change (or navigation to the auth screen) doesn't cut the
 * sheet off mid-frame. Dragging it down or tapping the scrim counts as
 * "Not now".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
)
{
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = SheetBackground,
        contentColor = SheetTitle,
        tonalElevation = 0.dp,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
        // ModalBottomSheet has no border parameter. This draws on top of the
        // sheet's background, along the top edge and faintly down the sides.
        modifier = Modifier.border(1.dp, SheetHairline, SheetShape),
    ) {
        SignInSheetContent(
            onSignIn = {
                hideThen(scope, sheetState, onSignIn)
            },
            onNotNow = {
                hideThen(scope, sheetState, onDismiss)
            },
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
private fun hideThen(scope: CoroutineScope, sheetState: SheetState, action: () -> Unit) {
    scope.launch { sheetState.hide() }.invokeOnCompletion {
        if (!sheetState.isVisible) {
            action()
        }
    }
}
@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(SheetHandle, CircleShape),
    )
}

/**
 * The sheet's inside, split out so it can be previewed — ModalBottomSheet
 * opens its own window and doesn't render in the preview pane.
 */
@Composable
private fun SignInSheetContent(
    onSignIn: () -> Unit,
    onNotNow: () -> Unit,
)
{
    // Top padding is 20dp, not the design's 12dp: the handle already sits
    // 12dp down, and this is the 20dp gap between it and the title.
    // The sheet adds the navigation-bar inset under the 24dp at the bottom.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Keep your collection",
                color = SheetTitle,
                style = TextStyle(
                    fontFamily = LibreCaslon, // your existing FontFamily from res/font
                    fontWeight = FontWeight.Normal,
                    fontSize = 26.sp,
                ),
            )
            Text(
                text = "Your first favorite is saved on this phone. " +
                        "Sign in to keep your collection on every device.",
                color = SheetBody,
                style = TextStyle(fontSize = 15.sp, lineHeight = 22.5.sp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSignIn,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryButtonBackground,
                    contentColor = PrimaryButtonText,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = "Continue with Google",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
            }
            TextButton(
                onClick = onNotNow,
                shape = CircleShape,
                colors = ButtonDefaults.textButtonColors(contentColor = SheetBody),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = "Not now",
                    style = TextStyle(fontSize = 15.sp),
                )
            }
        }

        Text(
            text = "Asked once. Favorites keep working offline either way.",
            color = SheetFootnote,
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 12.sp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
        )
    }
}

@Preview(widthDp = 390)
@Composable
private fun SignInSheetContentPreview() {
    Column(modifier = Modifier.background(SheetBackground, SheetShape)) {
        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            SheetHandle()
        }
        SignInSheetContent(onSignIn = {}, onNotNow = {})
    }
}