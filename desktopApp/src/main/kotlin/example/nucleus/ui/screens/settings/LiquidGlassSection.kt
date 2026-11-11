package example.nucleus.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsGroup
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.liquid_glass_beta
import example.nucleus.generated.resources.liquid_glass_blur
import example.nucleus.generated.resources.liquid_glass_opacity
import example.nucleus.generated.resources.liquid_glass_refraction
import example.nucleus.generated.resources.liquid_glass_specular
import example.nucleus.ui.components.glass.rememberLiquidGlassStyle
import example.nucleus.ui.components.player.GlassIconButton
import example.nucleus.viewmodels.AppearanceSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * Apartado de ajuste fino del Liquid Glass con preview en vivo.
 *
 * Los sliders escriben directo a preferencias: la preview y toda la app usan
 * los estilos compartidos (que leen [LocalLiquidGlassTuning]), así que el
 * cambio se ve al instante en todas partes. Solo tiene efecto con fondo
 * Sólido (ver gate en App/Navigation): con superficies transparentes la
 * fuente del cristal queda con huecos y el blur no rinde, por eso el
 * interruptor fija el fondo y bloquea su selector.
 */
@Composable
fun LiquidGlassSettings() {
    val colors = LocalSettingsColors.current
    val viewModel: AppearanceSettingsViewModel = koinInject()
    val blur by viewModel.liquidGlassBlur.collectAsState()
    val baseAlpha by viewModel.liquidGlassBaseAlpha.collectAsState()
    val specular by viewModel.liquidGlassSpecular.collectAsState()
    val refraction by viewModel.liquidGlassRefraction.collectAsState()

    SettingsGroup(
        title = {
            Text(
                stringResource(Res.string.liquid_glass_beta),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        colors = colors,
    ) {
        GlassSliderRow(
            label = stringResource(Res.string.liquid_glass_blur),
            valueLabel = "${blur.roundToInt()} dp",
            value = blur,
            range = 0f..48f,
            steps = 47,
            onChange = { viewModel.setLiquidGlassBlur(it) },
        )
        GlassSliderRow(
            label = stringResource(Res.string.liquid_glass_opacity),
            valueLabel = "${(baseAlpha * 100).roundToInt()} %",
            value = baseAlpha,
            range = 0.5f..0.98f,
            steps = 47,
            onChange = { viewModel.setLiquidGlassBaseAlpha(it) },
        )
        GlassSliderRow(
            label = stringResource(Res.string.liquid_glass_specular),
            valueLabel = "${(specular * 100).roundToInt()} %",
            value = specular,
            range = 0f..1f,
            steps = 19,
            onChange = { viewModel.setLiquidGlassSpecular(it) },
        )
        GlassSliderRow(
            label = stringResource(Res.string.liquid_glass_refraction),
            valueLabel = "${(refraction * 100).roundToInt()} %",
            value = refraction,
            range = 0f..1f,
            steps = 19,
            onChange = { viewModel.setLiquidGlassRefraction(it) },
        )
        LiquidGlassPreview()
    }
}

@Composable
private fun GlassSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Preview en vivo: una mini lista de filas coloridas con texto nítido pasa POR
 * DETRÁS de la barra de cristal, así que cada slider (blur, opacidad, brillo,
 * refracción) se nota al instante. La barra y las lentes son los componentes
 * reales con los estilos compartidos: lo que pasa aquí pasa en el MiniPlayer.
 */
@Composable
private fun LiquidGlassPreview() {
    val colorScheme = MaterialTheme.colorScheme
    val previewHaze = rememberHazeState()
    // Valores explícitos (no el CompositionLocal): así el preview refleja los
    // sliders mueva lo que mueva el resto de la app.
    val viewModel: AppearanceSettingsViewModel = koinInject()
    val blur by viewModel.liquidGlassBlur.collectAsState()
    val baseAlpha by viewModel.liquidGlassBaseAlpha.collectAsState()
    val specular by viewModel.liquidGlassSpecular.collectAsState()
    val refraction by viewModel.liquidGlassRefraction.collectAsState()
    val barStyle = rememberLiquidGlassStyle(
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surfaceContainer,
        blurDp = blur,
        baseAlpha = baseAlpha,
        specular = specular,
        refraction = refraction,
    )
    // Contraste máximo a propósito: si el esmerilado flojea, aquí se ve.
    val rowColors = remember {
        listOf(
            Color(0xFFE53935),
            Color(0xFF1E88E5),
            Color(0xFF43A047),
            Color(0xFFFB8C00),
            Color(0xFF8E24AA),
            Color(0xFF00ACC1),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(260.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colorScheme.primary,
                        colorScheme.tertiary,
                        colorScheme.surface,
                    ),
                ),
            )
            .hazeSource(previewHaze),
    ) {
        // Mini lista: filas con portada de color + texto blanco nítido. La
        // última fila queda justo detrás de la barra de cristal.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rowColors.forEach { cover ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cover),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.95f)),
                        )
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.6f)),
                        )
                    }
                }
            }
        }

        // Lente suelta sobre la zona ocupada: muestra la refracción de borde.
        GlassIconButton(
            onClick = {},
            imageVector = Icons.Rounded.Shuffle,
            contentDescription = null,
            hazeState = previewHaze,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
            blurDp = blur,
            baseAlpha = baseAlpha,
            specular = specular,
            refraction = refraction,
            size = 44.dp,
            iconSize = 24.dp,
        )

        // Mini barra de cristal con los componentes reales.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .hazeGlass(
                    input = HazeInput.Sources(previewHaze),
                    style = barStyle,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassIconButton(
                    onClick = {},
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    hazeState = previewHaze,
                    blurDp = blur,
                    baseAlpha = baseAlpha,
                    specular = specular,
                    refraction = refraction,
                    size = 44.dp,
                    iconSize = 26.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colorScheme.onSurface.copy(alpha = 0.85f)),
                    )
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
                    )
                }
                GlassIconButton(
                    onClick = {},
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    hazeState = previewHaze,
                    blurDp = blur,
                    baseAlpha = baseAlpha,
                    specular = specular,
                    refraction = refraction,
                    size = 40.dp,
                    iconSize = 24.dp,
                )
            }
        }
    }
}
