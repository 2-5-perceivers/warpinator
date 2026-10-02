package org.perceivers25.warpinator.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.perceivers25.warpinator.BuildConfig
import org.perceivers25.warpinator.R
import org.perceivers25.warpinator.app.LocalNavigator
import org.perceivers25.warpinator.core.design.shapes.segmentedDynamicShapes
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.utils.Utils

private const val TRANSLATE_URL =
    "https://hosted.weblate.org/engage/warpinator-android/"
private const val GOOGLE_PLAY_URL =
    "https://play.google.com/store/apps/details?id=org.perceivers25.warpinator"
private const val SOURCE_URL =
    "https://github.com/slowscript/warpinator-android"
private const val ISSUES_URL = "$SOURCE_URL/issues"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun AboutScreen() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigator = LocalNavigator.current
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = { navigator?.goBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding.plus(
                PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp),
            ),
        ) {

            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_warpinator),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(64.dp),
                                colorFilter = ColorFilter.tint(
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )

                        }
                        Spacer(Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.version,
                                    BuildConfig.VERSION_NAME,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            item {
                SegmentedListItem(
                    onClick = {
                        Utils.openUrl(
                            context, TRANSLATE_URL,
                        )
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 3),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    content = {
                        Text(stringResource(R.string.help_translate_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.help_translate_subtitle))
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                        )
                    },
                )
                Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                SegmentedListItem(
                    onClick = {
                        Utils.openUrl(
                            context,
                            GOOGLE_PLAY_URL,
                        )
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 3),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    content = {
                        Text(stringResource(R.string.rate_on_google_play_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.rate_on_google_play_subtitle))
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.RateReview,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                SegmentedListItem(
                    onClick = {
                        Utils.openUrl(context, SOURCE_URL)
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(2, 3),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    content = {
                        Text(stringResource(R.string.see_the_source_code_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.contributions_are_welcome_subtitle))
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                SegmentedListItem(
                    onClick = {
                        Utils.openUrl(
                            context, ISSUES_URL,
                        )
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(0, 2),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    content = {
                        Text(stringResource(R.string.issues_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.issues_subtitle))
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.BugReport,
                            contentDescription = null,
                        )
                    },
                )
                Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                SegmentedListItem(
                    onClick = {
                        Utils.openUrl(context, LICENSE_URL)
                        // TODO: add a license screen to show all OSS licenses of dependencies and the project
                    },
                    shapes = ListItemDefaults.segmentedDynamicShapes(1, 2),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    content = {
                        Text(stringResource(R.string.license_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.license_type))
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Gavel,
                            contentDescription = null,
                        )
                    },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.warranty),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 24.dp,
                    ),
                )
            }
        }
    }
}

@Composable
@PreviewDynamicColors
@PreviewLightDark
fun AboutScreenPreview() {
    WarpinatorTheme {
        AboutScreen()
    }
}