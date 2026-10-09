package com.kg.museumly.presentation.feature.scroll.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kg.museumly.domain.model.Section
import kotlinx.coroutines.launch



/** Outline of the section pill and the page counter, a matching pair. */
val TopBarChipOutline: Color = Color.White.copy(alpha = 0.3f)

val Section.label: String
    get() = when (this) {
        Section.EGYPT_NEAR_EAST -> "Egypt & Near East"
        Section.GREEK_ROMAN -> "Greek & Roman"
        Section.ISLAMIC -> "Islamic"
        Section.MEDIEVAL -> "Medieval"
        Section.EUROPEAN -> "European"
        Section.ASIA -> "Asia"
        Section.AFRICA_OCEANIA_AMERICAS -> "Africa, Oceania & Americas"
    }


/** Scroll feed: a section is always selected. */
@Composable
fun SectionPicker(
    selected: Section,
    onSectionSelected: (Section) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionPickerCore(
        selected = selected,
        allLabel = null,
        // Core only emits null when an "All" row exists, so this is safe.
        onSelected = { it?.let(onSectionSelected) },
        modifier = modifier
    )
}

/** Favorites: null means "show everything". */
@Composable
fun SectionPicker(
    selected: Section?,
    allLabel: String,
    onSectionSelected: (Section?) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionPickerCore(
        selected = selected,
        allLabel = allLabel,
        onSelected = onSectionSelected,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionPickerCore(
    selected: Section?,
    allLabel: String?,
    onSelected: (Section?) -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    Surface(
        onClick = { sheetOpen = true },
        shape = CircleShape,
        // Sits on the wall, not over an image, so it needs no fill. Same
        // hairline as the page counter.
        color = Color.Transparent,
        border = BorderStroke(1.dp, TopBarChipOutline),
        contentColor = Color.White,
        modifier = modifier
            .widthIn(max = 200.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = selected?.label ?: allLabel.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                // Wraps instead of truncating: a cut-off section name reads
                // as a bug.
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Change section",
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        // null entry = the "All" row, only present when allLabel is given.
        val options: List<Section?> =
            if (allLabel != null) listOf(null) + Section.entries else Section.entries

        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = Color(0xFF141414),
            contentColor = Color.White
        ) {
            Text(
                text = "Galleries",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            for (option in options) {
                val isSelected = option == selected
                ListItem(
                    headlineContent = {
                        Text(
                            text = option?.label ?: allLabel.orEmpty(),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    trailingContent = {
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected")
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = Color.White,
                        trailingIconColor = Color.White
                    ),
                    modifier = Modifier.clickable {
                        onSelected(option)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { sheetOpen = false }
                    }
                )
            }
        }
    }
}
