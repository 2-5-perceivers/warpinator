package org.perceivers25.warpinator.core.design.components

import androidx.compose.animation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.core.design.shapes.segmentedDynamicShapes
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandableSegmentedListItem(
    isExpanded: Boolean,
    toggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    listItemModifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    itemIndex: Int,
    listItemCount: Int,
    subItemCount: Int,
    // Slots
    content: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    subItemBuilder: @Composable (
        subItemIndex: Int, containerColor: Color, shape: Shape,
    ) -> Unit,
    interactionSource: MutableInteractionSource? = null,
) {
    val motionScheme = MaterialTheme.motionScheme

    val headerShape = ListItemDefaults.segmentedDynamicShapes(
        index = itemIndex,
        count = listItemCount,
    ).copy(
        selectedShape = ListItemDefaults.segmentedDynamicShapes(
            index = 0, count = subItemCount + 1,
        ).shape,
    )

    Column(
        modifier = modifier,
    ) {

        val stateDescription =
            if (isExpanded) stringResource(R.string.expanded_state) else stringResource(
                R.string.collapsed_state,
            )

        val actionLabel =
            if (isExpanded) stringResource(R.string.collapse_action) else stringResource(
                R.string.expand_details_action,
            )


        SegmentedListItem(
            onClick = toggleExpand,
            modifier = listItemModifier.semantics {
                this.stateDescription = stateDescription
                onClick(label = actionLabel, action = null)

                role = Role.Button
            },
            colors = colors,
            selected = isExpanded,
            shapes = headerShape,
            leadingContent = leadingContent,
            trailingContent = {
                trailingContent?.let { trailingContent ->
                    AnimatedVisibility(
                        visible = !isExpanded,
                        enter = expandHorizontally(motionScheme.fastSpatialSpec()),
                        exit = shrinkHorizontally(motionScheme.fastSpatialSpec()),
                        label = "TrailingContentAnimatedVisibility",
                    ) {
                        trailingContent()
                    }
                }
            },
            content = content,
            supportingContent = supportingContent,
            interactionSource = interactionSource,
        )
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(motionScheme.fastSpatialSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()),
            label = "DetailsContentAnimatedVisibility",
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.selectedContentColor) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    modifier = Modifier.padding(top = ListItemDefaults.SegmentedGap),
                ) {
                    repeat(subItemCount) { subItemIndex ->
                        val shape = ListItemDefaults.segmentedDynamicShapes(
                            index = subItemIndex + 1, count = subItemCount + 1,
                        ).shape
                        subItemBuilder(
                            subItemIndex,
                            colors.selectedContainerColor,
                            shape,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun ExpandableListScreen() {
    val items = List(5) { "Item $it" }
    var expandedItemId by remember { mutableStateOf<String?>(items[1]) }

    WarpinatorTheme {
        Scaffold { paddingValues ->
            LazyColumn(
                contentPadding = paddingValues,
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(items) { itemIndex, itemId ->
                    ExpandableSegmentedListItem(
                        isExpanded = expandedItemId == itemId,
                        toggleExpand = {
                            expandedItemId =
                                if (expandedItemId == itemId) null else itemId
                        },
                        content = { Text(text = "Title for $itemId") },
                        supportingContent = { Text(text = "Subtitle details") },
                        subItemBuilder = { subItemIndex, containerColor, shape ->
                            Surface(
                                color = containerColor,
                                shape = shape,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "This is a detail panel",
                                    )
                                    if (subItemIndex == 1) Button(onClick = {}) {
                                        Text(
                                            "Action",
                                        )
                                    }
                                }
                            }
                        },
                        subItemCount = 2,
                        itemIndex = itemIndex,
                        listItemCount = items.size,
                    )
                }
            }
        }
    }
}

