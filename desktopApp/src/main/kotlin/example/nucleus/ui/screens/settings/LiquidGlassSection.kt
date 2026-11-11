package example.nucleus.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsGroup
import example.nucleus.generated.resources.Res
import example.nucleus.generated.resources.liquid_glass_beta
import example.nucleus.generated.resources.liquid_glass_blur
import example.nucleus.generated.resources.liquid_glass_opacity
import example.nucleus.generated.resources.liquid_glass_refraction
import example.nucleus.generated.resources.liquid_glass_specular
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
        // Sin preview: el usuario ya ve el cristal en vivo en el MiniPlayer.
        // El preview en Settings nunca renderizó (no se depurará más aquí).
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
