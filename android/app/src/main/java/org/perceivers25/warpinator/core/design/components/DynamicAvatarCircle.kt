package org.perceivers25.warpinator.core.design.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.transformed
import kotlinx.coroutines.isActive
import org.perceivers25.warpinator.core.design.theme.WarpinatorTheme
import org.perceivers25.warpinator.core.utils.ProfilePicturePainter

// Slightly sized down loading shape to prevent revealing outside of the bitmap
private val matrix = Matrix().apply {
    setScale(0.95f, 0.95f)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val LoadingShape = MaterialShapes.Cookie4Sided.transformed(matrix)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DynamicAvatarCircle(
    bitmap: Bitmap? = null,
    isFavorite: Boolean = false,
    hasError: Boolean = false,
    isDisabled: Boolean = false,
    isLoading: Boolean = false,
    size: Dp = 42.dp,
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        while (this.isActive) {
            rotation.animateTo(
                targetValue = 180f,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 80f,
                    visibilityThreshold = 1.0f,
                ),
            )
            rotation.snapTo(0f)
        }
    }

    val backgroundColor = if (hasError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val iconColor = if (hasError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val errorBorder = if (hasError) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.error)
    } else null

    val containerShape = when {
        isLoading -> LoadingShape.toShape()
        isFavorite -> MaterialShapes.Cookie7Sided.toShape()
        else -> CircleShape
    }


    Surface(
        modifier = Modifier
            .size(size)
            .padding(1.dp)
            .alpha(if (isDisabled) 0.5f else 1f)
            .rotate(rotation.value),
        shape = containerShape,
        color = backgroundColor,
        border = errorBorder,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .rotate(-rotation.value),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = iconColor,
                )
            }
        }
    }
}

@PreviewDynamicColors()
@PreviewLightDark
@Composable
fun DynamicAvatarCirclePreview() {
    val bitmap =
        ProfilePicturePainter.getProfilePicture("1", LocalContext.current)

    WarpinatorTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.safeContentPadding(),
        ) {
            FlowRow(
                modifier = Modifier
                    .padding(12.dp)
                    .width((44 * 6).dp),
            ) {
                DynamicAvatarCircle()
                DynamicAvatarCircle(isLoading = true)
                DynamicAvatarCircle(isFavorite = true)
                DynamicAvatarCircle(hasError = true)
                DynamicAvatarCircle(hasError = true, isFavorite = true)
                DynamicAvatarCircle(isDisabled = true)

                // With bitmaps
                DynamicAvatarCircle(bitmap = bitmap)
                DynamicAvatarCircle(bitmap = bitmap, isLoading = true)
                DynamicAvatarCircle(bitmap = bitmap, isFavorite = true)
                DynamicAvatarCircle(bitmap = bitmap, hasError = true)
                DynamicAvatarCircle(
                    bitmap = bitmap,
                    hasError = true,
                    isFavorite = true,
                )
                DynamicAvatarCircle(bitmap = bitmap, isDisabled = true)

                // Increased size
                DynamicAvatarCircle(size = 84.dp)
                DynamicAvatarCircle(size = 84.dp, isLoading = true)
                DynamicAvatarCircle(size = 84.dp, isFavorite = true)
                DynamicAvatarCircle(size = 84.dp, hasError = true)
                DynamicAvatarCircle(
                    size = 84.dp,
                    hasError = true,
                    isFavorite = true,
                )
                DynamicAvatarCircle(size = 84.dp, isDisabled = true)
            }
        }
    }
}
