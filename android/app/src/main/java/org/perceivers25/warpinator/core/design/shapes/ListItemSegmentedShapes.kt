package org.perceivers25.warpinator.core.design.shapes

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@ExperimentalMaterial3ExpressiveApi
@Composable
fun ListItemDefaults.segmentedDynamicShapes(
    index: Int,
    count: Int,
    defaultShapes: ListItemShapes = shapes(),
    overrideShape: CornerBasedShape = MaterialTheme.shapes.large,
): ListItemShapes {
    return remember(index, count, defaultShapes, overrideShape) {
        when {
            count == 1 -> {
                val defaultBaseShape = defaultShapes.shape
                if (defaultBaseShape is CornerBasedShape) {
                    defaultShapes.copy(
                        shape = defaultBaseShape.copy(
                            topStart = overrideShape.topStart,
                            topEnd = overrideShape.topEnd,
                            bottomStart = overrideShape.bottomStart,
                            bottomEnd = overrideShape.bottomEnd,
                        ),
                    )
                } else {
                    defaultShapes
                }
            }

            index == 0 -> {
                val defaultBaseShape = defaultShapes.shape
                if (defaultBaseShape is CornerBasedShape) {
                    defaultShapes.copy(
                        shape = defaultBaseShape.copy(
                            topStart = overrideShape.topStart,
                            topEnd = overrideShape.topEnd,
                        ),
                    )
                } else {
                    defaultShapes
                }
            }

            index == count - 1 -> {
                val defaultBaseShape = defaultShapes.shape
                if (defaultBaseShape is CornerBasedShape) {
                    defaultShapes.copy(
                        shape = defaultBaseShape.copy(
                            bottomStart = overrideShape.bottomStart,
                            bottomEnd = overrideShape.bottomEnd,
                        ),
                    )
                } else {
                    defaultShapes
                }
            }

            else -> defaultShapes
        }
    }
}

@ExperimentalMaterial3ExpressiveApi
@Composable
fun ListItemDefaults.segmentedHorizontalDynamicShapes(
    index: Int,
    count: Int,
    defaultShapes: ListItemShapes = shapes(),
    overrideShape: CornerBasedShape = MaterialTheme.shapes.large,
): ListItemShapes {
    return remember(index, count, defaultShapes, overrideShape) {
        when {
            count == 1 -> {
                val defaultBaseShape = defaultShapes.shape
                if (defaultBaseShape is CornerBasedShape) {
                    defaultShapes.copy(
                        shape = defaultBaseShape.copy(
                            topStart = overrideShape.topStart,
                            topEnd = overrideShape.topEnd,
                            bottomStart = overrideShape.bottomStart,
                            bottomEnd = overrideShape.bottomEnd,
                        ),
                    )
                } else {
                    defaultShapes
                }
            }

            index == 0 -> {
                val defaultBaseShape = defaultShapes.shape
                if (defaultBaseShape is CornerBasedShape) {
                    defaultShapes.copy(
                        shape = defaultBaseShape.copy(
                            topStart = overrideShape.topStart,
                            bottomStart = overrideShape.topEnd,
                        ),
                    )
                } else {
                    defaultShapes
                }
            }

            index == count - 1 -> {
                val defaultBaseShape = defaultShapes.shape
                if (defaultBaseShape is CornerBasedShape) {
                    defaultShapes.copy(
                        shape = defaultBaseShape.copy(
                            topEnd = overrideShape.bottomStart,
                            bottomEnd = overrideShape.bottomEnd,
                        ),
                    )
                } else {
                    defaultShapes
                }
            }

            else -> defaultShapes
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ListItemShapes.toIconButtonShapes(): IconButtonShapes {
    return IconButtonShapes(
        this.shape,
        this.pressedShape,
    )
}