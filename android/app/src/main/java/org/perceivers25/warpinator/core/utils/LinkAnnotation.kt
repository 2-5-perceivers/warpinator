package org.perceivers25.warpinator.core.utils

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

@Composable
fun rememberAnnotatedLinkText(
    text: String,
    accentColor: Color,
): AnnotatedString {
    val context = LocalContext.current
    val configuration = LocalWindowInfo.current.containerDpSize
    val isLargeScreen = configuration.width >= 600.dp

    return remember(text) {
        buildAnnotatedString {
            val urlRegex =
                """((?:https?://|www\.)[^\s<>"'{}|\\^`\[\]]*[^\s<>"'{}|\\^`\[\].,;:!?)])""".toRegex()
            var lastIndex = 0

            urlRegex.findAll(text).forEach { match ->
                append(text.substring(lastIndex, match.range.first))

                withLink(
                    LinkAnnotation.Url(
                        url = match.value,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                textDecoration = TextDecoration.Underline,
                                color = accentColor,
                            ),
                        ),
                        linkInteractionListener = { annotation ->
                            val url = (annotation as LinkAnnotation.Url).url
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())

                            if (isLargeScreen) {
                                intent.addFlags(
                                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
                                )
                            } else {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }

                            context.startActivity(intent)
                        },
                    ),
                ) {
                    append(match.value)
                }

                lastIndex = match.range.last + 1
            }
            append(text.substring(lastIndex))
        }
    }
}