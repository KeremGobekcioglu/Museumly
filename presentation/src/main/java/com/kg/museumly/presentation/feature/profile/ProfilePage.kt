package com.kg.museumly.presentation.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kg.museumly.presentation.feature.profile.components.MemberTicket
import com.kg.museumly.presentation.navigation.ProfilePage

@Composable
fun ProfilePage()
{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
            .background(Color.Black)
    ) {
        MemberTicket(
            isSignedIn = false, hasGame = false, onSignInClick = {},
            visitDay = "4 Nov", visitYear = "2026",
        )
    }
}