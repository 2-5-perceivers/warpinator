package org.perceivers25.warpinator.feature.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.core.design.components.DynamicAvatarCircle
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.model.ui.RemoteUi
import org.perceivers25.warpinator.core.utils.RemoteDisplayInfo

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun RemoteLargeFlexibleTopAppBar(
    remote: RemoteUi,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    containerColor: Color = Color.Unspecified,
    elevatedContainerColor: Color = Color.Unspecified,
) {
    val isFavorite = remote.isFavorite

    val titleFormat = RemoteDisplayInfo.fromRemote(remote)

    val favoriteString = stringResource(R.string.favorite_label)
    val ipAddressString = stringResource(
        R.string.remote_ip_content_description,
        titleFormat.label ?: "",
    )

    val semanticContentDescription = remember(titleFormat) {
        buildString {
            if (isFavorite) append(favoriteString, " ")
            append(titleFormat.title, " ")
            append(titleFormat.subtitle, ". ")
            if (titleFormat.label != null) append(ipAddressString)
        }
    }

    TwoRowsTopAppBar(
        title = { expanded ->
            if (expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp, 12.dp)
                        .clearAndSetSemantics {
                            contentDescription = semanticContentDescription
                        },
                ) {
                    DynamicAvatarCircle(
                        bitmap = remote.picture,
                        isFavorite = isFavorite,
                        size = 64.dp,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            titleFormat.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            titleFormat.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        titleFormat.label?.let {
                            Text(
                                it,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = semanticContentDescription
                },
            ) {
                DynamicAvatarCircle(
                    bitmap = remote.picture,
                    isFavorite = isFavorite,
                )
                Text(titleFormat.title, modifier = Modifier.padding(8.dp))
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = elevatedContainerColor,
        ),
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
        expandedHeight = TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight,
        windowInsets = TopAppBarDefaults.windowInsets,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun RemoteLargeFlexibleTopAppBarPreview() {
    val remote = RemoteUi(
        displayName = "Test Device",
        username = "user",
        hostname = "hostname",
        ip = "192.168.0.100",
    )

    WarpinatorTheme {
        RemoteLargeFlexibleTopAppBar(
            remote = remote,
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack, "",
                    )
                }
            },
        )
    }
}