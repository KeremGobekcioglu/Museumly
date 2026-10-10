package com.kg.museumly.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

internal fun switchTab(navController: NavHostController, route: Any) {
    navController.navigate(route) {
        popUpTo(ScrollPage) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
internal fun RowScope.MainBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val labelColor: Color by animateColorAsState(
        targetValue = if(selected) Color.White else Color.White.copy(0.55f),
        animationSpec = tween(200),
        label = "tabLabel"
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                color = labelColor,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                maxLines = 1,
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = labelColor
            )
        }
    }
}

internal data class MainTab(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

internal val MainTabs: List<MainTab> = listOf(
    MainTab(ScrollPage, "Gallery", Icons.Outlined.Museum, Icons.Filled.Museum),
    MainTab(FavoritesPage, "Favorites", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
)

@Composable
internal fun MainBottomBar(
    isSelected: (MainTab) -> Boolean,
    onTabClick: (MainTab) -> Unit,
) {
    var selectedIndex: Int = 0
    for (index: Int in MainTabs.indices) {
        if (isSelected(MainTabs[index])) {
            selectedIndex = index
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(56.dp),
    ) {
        val slotWidth: Dp = maxWidth / MainTabs.size
        val targetX: Dp = slotWidth * selectedIndex + (slotWidth - 24.dp) / 2
        val lightX: Dp by animateDpAsState(
            targetValue = targetX,
            animationSpec = tween(300),
            label = "light",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.10f)),
        )
        Box(
            modifier = Modifier
                .offset(x = lightX)
                .width(24.dp)
                .height(1.dp)
                .background(Color.White),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            for (tab in MainTabs) {
                val selected = isSelected(tab)
                MainBarItem(
                    selected = selected,
                    onClick = { onTabClick(tab) },
                    icon = if (selected) tab.selectedIcon else tab.icon,
                    label = tab.label,
                )
            }
        }
    }
}