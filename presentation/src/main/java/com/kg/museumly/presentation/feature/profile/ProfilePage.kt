package com.kg.museumly.presentation.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.museumly.presentation.feature.profile.components.ChevronMark
import com.kg.museumly.presentation.feature.profile.components.CouponStripRow
import com.kg.museumly.presentation.feature.profile.components.MemberTicket
import com.kg.museumly.presentation.feature.profile.components.TicketInk
import com.kg.museumly.presentation.feature.profile.components.TicketMuted
import com.kg.museumly.presentation.feature.profile.components.TicketPaper
import com.kg.museumly.presentation.navigation.ProfilePage

@Composable
fun ProfilePage()
{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        )
    {
        MemberTicket(
            isSignedIn = false,
            hasGame = false,
            onSignInClick = {},
            visitDay = "4 Nov",
            visitYear = "2026",
        )
        CouponStripRow(
            favoriteCount = 24,
            sharedCount = 7,
            onFavoritesClick = {},
            onSharedClick = {},
        )
        SettingsRow(onClick = {})
    }
}

private val BubbleColor: Color = Color(0xFF141312)
private val BubbleBorder: Color = Color(0xFFEDE8DF).copy(alpha = 0.09f)
private val OnDarkText: Color = Color(0xFFEDE8DF)
private val OnDarkMuted: Color = Color(0xFF9A958C)

@Composable
private fun SettingsRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
{
    val shape: RoundedCornerShape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(TicketPaper)
            .border(width = 1.dp, color = BubbleBorder, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Settings and account",
            fontSize = 15.sp,
            color = TicketInk,
            modifier = Modifier.weight(1f),
        )
        ChevronMark(color = TicketInk)
    }
}