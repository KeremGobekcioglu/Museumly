package com.kg.museumly.presentation.feature.auth.components


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.presentation.R
import com.kg.museumly.presentation.ui.theme.LibreCaslon

@Composable
internal fun AuthActions(
    isLoading: Boolean,
    onContinueWithGoogle: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier
)
{
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        GoogleSignInButton(
            isLoading = isLoading,
            onClick = onContinueWithGoogle,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        )
        Box(
            modifier = Modifier.height(44.dp),
            contentAlignment = Alignment.Center
        )
        {
            if(!isLoading)
            {
                TextButton(
                    onClick = onNotNow,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE3E3E3))
                ) {
                    Text(
                        text = stringResource(R.string.auth_not_now),
                        fontFamily = LibreCaslon,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fill = Color(0xFF131314)
    val content = Color(0xFFE3E3E3)
    val signingIn: String = stringResource(R.string.auth_signing_in)

    Button(
        onClick = onClick,
        enabled = !isLoading,
        shape = CircleShape,
        border = BorderStroke(width = 1.dp, color = Color(0xFF8E918F)),
        colors = ButtonDefaults.buttonColors(
            containerColor = fill,
            contentColor = content,
            disabledContainerColor = fill,
            disabledContentColor = content
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.semantics {
            if(isLoading)
            {
                contentDescription = signingIn
            }
        }
    ) {
        if(isLoading)
        {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = content,
                trackColor = content.copy(alpha = 0.25f),
                strokeWidth = 2.dp
            )
        }
        else
        {
            Image(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.auth_continue_with_google),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}