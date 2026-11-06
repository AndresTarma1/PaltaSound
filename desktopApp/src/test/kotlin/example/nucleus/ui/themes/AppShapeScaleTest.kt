package example.nucleus.ui.themes

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import example.nucleus.data.repository.CornerStyle
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Las escalas de formas se convierten a px con la densidad de la ventana. Un
 * redondeo negativo lanzaba `IllegalArgumentException: Corner size in Px can't
 * be negative`; este test cubre el invariante en varias densidades.
 */
class AppShapeScaleTest {

    private fun RoundedCornerShape.cornersPx(density: Density): List<Float> {
        val size = Size(100f, 100f)
        return listOf(
            topStart.toPx(size, density),
            topEnd.toPx(size, density),
            bottomEnd.toPx(size, density),
            bottomStart.toPx(size, density),
        )
    }

    @Test
    fun ningunaEscalaProduceRadiosNegativos() {
        val densities = listOf(1f, 1.089f, 1.25f, 2f)
        for (density in densities) {
            for (style in CornerStyle.entries) {
                val scale = AppShapeScale.forCornerStyle(style)
                val shapes = listOf(
                    scale.extraSmall,
                    scale.small,
                    scale.medium,
                    scale.large,
                    scale.xLarge,
                    scale.extraLarge,
                    scale.largeIncreased,
                    scale.extraLargeIncreased,
                    scale.extraExtraLarge,
                )
                for (shape in shapes) {
                    for (px in shape.cornersPx(Density(density))) {
                        assertTrue(
                            px >= 0f,
                            "${style.name} produjo una esquina negativa ($px) a density=$density",
                        )
                    }
                }
            }
        }
    }
}
