package example.nucleus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.ui.components.layout.HorizontalScrollableRow
import androidx.compose.ui.draw.clip
import example.nucleus.ui.themes.AppShapes
import example.nucleus.ui.utils.circleAwareShape

@Composable
internal fun AlbumScreenSkeletonContent() {
    Row(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 24.dp)) {
        Column(
            modifier = Modifier.width(320.dp).padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { InfoPanelSkeleton(coverSize = 240.dp) }
        Spacer(Modifier.width(32.dp))
        Column(modifier = Modifier.weight(1f).padding(top = 24.dp, end = 12.dp)) {
            SongListSkeleton(count = 8)
        }
    }
}

@Composable
internal fun PlaylistScreenSkeletonContent() {
    Row(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 24.dp)) {
        Column(
            modifier = Modifier.width(320.dp).padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { InfoPanelSkeleton(coverSize = 240.dp) }
        Spacer(Modifier.width(32.dp))
        Column(modifier = Modifier.weight(1f).padding(top = 24.dp, end = 12.dp)) {
            PlaylistSongListSkeleton(count = 8)
        }
    }
}

@Composable
internal fun ArtistScreenSkeletonContent() {
    val surface = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.28f))
        ) {
            Box(Modifier.fillMaxSize().shimmerBackground())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Transparent,
                                0.30f to Color.Transparent,
                                0.58f to surface.copy(alpha = 0.36f),
                                0.78f to surface.copy(alpha = 0.82f),
                                1.00f to surface
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.36f),
                                0.70f to Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 32.dp)
            ) {
                Box(Modifier.width(320.dp).height(54.dp).shimmerBackground(AppShapes.extraSmall))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(180.dp).height(18.dp).shimmerBackground(AppShapes.extraSmall))
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(0.48f).height(15.dp).shimmerBackground(AppShapes.extraSmall))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.34f).height(15.dp).shimmerBackground(AppShapes.extraSmall))
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(118.dp).height(42.dp).shimmerBackground(circleAwareShape()))
                    Box(Modifier.width(96.dp).height(42.dp).shimmerBackground(circleAwareShape()))
                    Box(Modifier.width(150.dp).height(42.dp).shimmerBackground(circleAwareShape()))
                    Box(Modifier.size(36.dp).shimmerBackground(circleAwareShape()))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            repeat(3) {
                ArtistSectionSkeleton()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun SkeletonSongRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Caja de índice / PlayArrow — 36dp de ancho
        Box(Modifier.width(36.dp).height(36.dp).shimmerBackground(AppShapes.extraSmall))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.55f).height(18.dp).shimmerBackground(AppShapes.extraSmall))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth(0.3f).height(13.dp).shimmerBackground(AppShapes.extraSmall))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(36.dp).height(16.dp).shimmerBackground(AppShapes.extraSmall))
    }
}

@Composable
internal fun PlaylistSkeletonSongRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).shimmerBackground(RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.55f).height(18.dp).shimmerBackground(AppShapes.extraSmall))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth(0.35f).height(13.dp).shimmerBackground(AppShapes.extraSmall))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(36.dp).height(16.dp).shimmerBackground(AppShapes.extraSmall))
    }
}

@Composable
internal fun InfoPanelSkeleton(coverSize: Dp) {
    Spacer(Modifier.height(20.dp))
    Box(Modifier.size(coverSize).shimmerBackground(AppShapes.small))
    Spacer(Modifier.height(24.dp))
    Box(Modifier.width(200.dp).height(28.dp).shimmerBackground(AppShapes.extraSmall))
    Spacer(Modifier.height(6.dp))
    Box(Modifier.width(120.dp).height(16.dp).shimmerBackground(AppShapes.extraSmall))
    Spacer(Modifier.height(3.dp))
    Box(Modifier.width(150.dp).height(13.dp).shimmerBackground(AppShapes.extraSmall))
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).shimmerBackground(circleAwareShape()))
        Box(Modifier.size(56.dp).shimmerBackground(circleAwareShape()))
        Box(Modifier.size(44.dp).shimmerBackground(circleAwareShape()))
        Box(Modifier.size(44.dp).shimmerBackground(circleAwareShape()))
    }
}

@Composable
internal fun SongListSkeleton(count: Int) {
    repeat(count) { i ->
        SkeletonSongRow()
        if (i < count - 1) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f),
                modifier = Modifier.padding(start = 48.dp)
            )
        }
    }
}

@Composable
internal fun PlaylistSongListSkeleton(count: Int) {
    repeat(count) { i ->
        PlaylistSkeletonSongRow()
        if (i < count - 1) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f),
                modifier = Modifier.padding(start = 104.dp)
            )
        }
    }
}

@Composable
internal fun ArtistSectionSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth(0.3f)
                .height(22.dp)
                .shimmerBackground(AppShapes.extraSmall)
        )
        Spacer(Modifier.height(12.dp))
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            state = rowState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(5) {
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .clip(AppShapes.medium)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(Modifier.size(134.dp).shimmerBackground(AppShapes.small))
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth(0.85f).height(14.dp)
                            .shimmerBackground(AppShapes.extraSmall)
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth(0.55f).height(12.dp)
                            .shimmerBackground(AppShapes.extraSmall)
                    )
                }
            }
        }
    }
}
