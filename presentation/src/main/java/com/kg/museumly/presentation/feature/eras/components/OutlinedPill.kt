package com.kg.museumly.presentation.feature.eras.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.presentation.feature.scroll.presentation.components.TopBarChipOutline

/** The app's outlined pill: transparent, 1dp white at 30%, white label. */
@Composable
internal fun OutlinedPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
{
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        border = BorderStroke(1.dp, TopBarChipOutline),
        contentColor = Color.White,
        shape = CircleShape
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}
