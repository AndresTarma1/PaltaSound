package example.nucleus.ui.themes

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import example.nucleus.data.repository.CornerStyle
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * El error queouni2NegativeCorner was IllegalArgumentException: "Corner size in Px can't be
 * negative (topStart = -0.98...)". Este test imprime los radios reales de cada escala para
 * localizar de dónde sale el negativo, en vez de suponerlo.
 */
class AppShapeScaleProbeTest {

    private fun RoundedCornerShape.cornersPx(density: Density): List<Float> {
        val size = Size(100f, 100f)
        return listOf(
            topStart.toPx(size, density), topEnd.toPx(size, density),
            bottomEnd.toPx(size, density), bottomStart.toPx(size, density),
        )
    }

    @Test
    fun imprimeRadiosDeCadaEscala() {
        val density = Density(density = 1.089f)
        for (style in CornerStyle.entries) {
            val scale = AppShapeScale.forCornerStyle(style)
            val roles = listOf(
                "extraSmall" to scale.extraSmall,
                "small" to scale.small,
                "medium" to scale.medium,
                "large" to scale.large,
                "xLarge" to scale.xLarge,
                "extraLarge" to scale.extraLarge,
                "largeIncreased" to scale.largeIncreased,
                "extraLargeIncreased" to scale.extraLargeIncreased,
                "extraExtraLarge" to scale.extraExtraLarge,
            )
            println("=== ${style.name} (scale=${style.scale}) ===")
            for ((name, shape) in roles) {
                val px = shape.cornersPx(density)
                val negative = px.any { it < 0f }
                println(
                    "  %-20s %s  %s".format(
                        name,
                        px.joinToString(prefix = "[", postfix = "]") { "%.4f".format(it) },
                        if (negative) "  <-- NEGATIVO" else "",
                    ),
                )
            }
        }
    }

    @Test
    fun ningunaEscalaProduceRadiosNegativos() {
        for (style in CornerStyle.entries) {
            val scale = AppShapeScale.forCornerStyle(style)
            val all = listOf(
                scale.extraSmall, scale.small, scale.medium, scale.large, scale.xLarge,
                scale.extraLarge, scale.largeIncreased, scale.extraLargeIncreased,
                scale.extraExtraLarge,
            )
            for (shape in all) {
                for (px in shape.cornersPx(Density(1f))) {
                    assertTrue(
                        px >= 0f,
                        "${style.name} produjo una esquina negativa: $px",
                    )
                }
            }
        }
    }
}