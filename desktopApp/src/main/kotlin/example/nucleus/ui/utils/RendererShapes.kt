package example.nucleus.ui.utils

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import example.nucleus.platform.Platform

/**
 * Instancia estable de la variante "redondeada pero circular".
 *
 * Antes `isCircleLikeShape` reconocía esa forma comparando contra un radio mágico (12.dp).
 * Al volver los radios dinámicos (CornerStyle) la comparación se rompía: con "Muy
 * redondeadas" o "Rectas" el token ya no valía 12.dp y las carátulas redondeadas dejaban de
 * reconocerse como circulares. Se reconoce ahora por IDENTIDAD, que no depende de la escala.
 */
private val CircleLikeRounded = RoundedCornerShape(12.dp)

fun circleAwareShape(corner: Dp = 12.dp): Shape {
    return if (isOpenGlRenderer()) {
        if (corner == 12.dp) CircleLikeRounded else RoundedCornerShape(corner)
    } else {
        CircleShape
    }
}

fun isCircleLikeShape(shape: Shape, corner: Dp = 12.dp): Boolean {
    return shape === CircleLikeRounded || shape == CircleShape || shape == RoundedCornerShape(corner)
}

private fun isOpenGlRenderer(): Boolean {
    // Cuando el renderApi está fijado, se respeta. Cuando no está definido (DIRECTX/"auto"),
    // skiko usa OpenGL por defecto en Linux — así que Linux sin elección explícita también se trata como OpenGL.
    val prop = System.getProperty("skiko.renderApi")
    return if (prop != null) prop.equals("OPENGL", ignoreCase = true) else Platform.isLinux
}
