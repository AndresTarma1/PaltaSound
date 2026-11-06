@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package example.nucleus.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.images.MusicPlayerImage
import example.nucleus.ui.components.images.PlaceholderType
import example.nucleus.ui.themes.AppShapes

@Composable
fun CoverArt(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
) {

    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = AppShapes.small,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        MusicPlayerImage(
            url = url,
            contentDescription = title,
            modifier = Modifier
                .fillMaxSize(),
            placeholderType = PlaceholderType.SONG,
            contentScale = ContentScale.Crop,
        )
    }
}
