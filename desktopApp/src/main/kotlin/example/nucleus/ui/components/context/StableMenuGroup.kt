package example.nucleus.ui.components.context

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import example.nucleus.ui.themes.AppShapes

/**
 * Formas de menu que no pueden interpolarse, porque en Material3 1.12.0-alpha03 hacerlo tira la
 * ventana.
 *
 * EL BUG (esta vez con el fuente de M3 delante, no de memoria)
 * ------------------------------------------------------------
 * `DropdownMenuGroup` hace esto al pintar:
 *
 * ```kotlin
 * var hasBeenHovered by remember { mutableStateOf(false) }   // se queda en true para siempre
 * val shape = if (hasBeenHovered && !hovered) shapes.inactiveShape else shapes.shape
 * val morphed = rememberAnimatedShape(shape, MotionSchemeKeyTokens.FastSpatial)
 * ```
 *
 * y `AnimatedShapeState` interpola con
 *
 * ```kotlin
 * morphed = Interpolatable.lerp(startShape, targetShape, progress)   // progress, no un 0..1 fijo
 * ```
 *
 * `FastSpatial` es un **spring**, y un spring rebota: `progress` se sale de `[0, 1]`. Al salir del
 * hover el target pasa de la forma activa a `inactiveShape`, que es mas pequena, y en el rebote el
 * radio cruza el cero y sale **negativo**. `CornerBasedShape.createOutline` lanza entonces
 *
 *     IllegalArgumentException: Corner size in Px can't be negative
 *
 * y se cae la ventana. Encima `animateToShape` congela el estado con
 * `lerp(start, target, progress)` usando un `progress` **ya fuera de rango**, asi que cada
 * entrada/salida de hover compone el error y bastan dos seguidas para llegar a negativo. Por eso
 * `topStart` salia exactamente 2x `bottomStart` en cada crash: solo una interpolacion proporcional
 * mantiene ese ratio mientras las dos esquinas caen a la vez.
 *
 * Solo crashea en los grupos leading y trailing, nunca en los del medio: los tokens de M3 dan
 * `inactiveShape = CornerSmall` para los tres, pero la forma activa del grupo del medio tambien es
 * `CornerSmall`, o sea identica y sin nada que interpolar.
 *
 * EL ARREGLO
 * ----------
 * No se cambia el `spec` (no es configurable) ni se quitan las formas. Se pasan las dos formas
 * IGUALES: con `inactiveShape == shape`, `shapeByInteraction` siempre elige `shapes.shape` y
 * `animateToShape` sale por su guarda `if (targetShape == newTarget) return`. Interpolacion cero,
 * para siempre, y el radio no puede salirse.
 *
 * Esto NO quita la animacion del menu: la de apertura (escala 0.8 -> 1 y alpha 0 -> 1) vive en
 * `ContextMenuPopup` y sigue intacta. Lo unico que se pierde es el rebote de la FORMA del
 * contenedor al pasar el raton, que es justo lo que crashaba.
 *
 * Si una version futura de Material3 clampea `progress` a `[0, 1]` (el fix upstream natural), este
 * fichero se puede borrar y volver a `MenuDefaults.groupShape(index, count)`.
 */

/**
 * [DropdownMenuGroup] con la forma del contenedor blindada contra el crash de radios negativos.
 *
 * @param groupIndex posicion de ESTE grupo entre los grupos del menu (0, 1, 2...).
 * @param groupCount cuantos grupos tiene el menu en total. Es lo que decide si el grupo es
 *   leading, middle o trailing, igual que en `MenuDefaults.groupShape`.
 */
@Composable
fun StableDropdownMenuGroup(
    groupIndex: Int,
    groupCount: Int,
    content: @Composable () -> Unit,
) {
    val shape = groupContainerShape(groupIndex, groupCount)
    // La clave del fix: inactiva == activa, asi que no hay morph. Ver la nota de arriba.
    DropdownMenuGroup(shapes = MenuGroupShapes(shape = shape, inactiveShape = shape)) {
        content()
    }
}

/**
 * Forma del CONTENEDOR de un grupo, replicando `MenuDefaults.groupShape` pero con los radios de
 * [AppShapes], para que los menus sigan la preferencia de esquinas del usuario.
 *
 * Con escala SOFT esto es identico a M3: `large` = 16dp = `CornerValueLarge` y
 * `small` = 8dp = `CornerValueSmall`.
 */
@Composable
private fun groupContainerShape(groupIndex: Int, groupCount: Int): Shape {
    // No hace falta pasar los radios en dp: `RoundedCornerShape.topStart` ya es un `CornerSize`,
    // que es justo lo que acepta el constructor de cuatro esquinas.
    val outer = AppShapes.large.topStart
    val inner = AppShapes.small.topStart

    return when {
        groupCount <= 1 -> RoundedCornerShape(outer)
        groupIndex == 0 -> RoundedCornerShape(
            topStart = outer, topEnd = outer,
            bottomStart = inner, bottomEnd = inner,
        )
        groupIndex == groupCount - 1 -> RoundedCornerShape(
            topStart = inner, topEnd = inner,
            bottomStart = outer, bottomEnd = outer,
        )
        else -> RoundedCornerShape(inner)
    }
}

/**
 * Forma de un ITEM dentro de su grupo. El item no puede crashear porque
 * `DropdownMenuItem` solo interpola cuando `selected` cambia, y estos items no son seleccionables
 * ([MenuItemShapes.selectedShape] nunca llega a activarse), pero se deja aqui para que radios y
 * contenedor salgan de la misma fuente y los menus respeten la preferencia de esquinas.
 *
 * @param itemIndex posicion del item DENTRO de su grupo.
 * @param itemCount cuantos items tiene el grupo.
 */
@Composable
fun groupItemShapeOf(itemIndex: Int, itemCount: Int): Shape {
    val outer = AppShapes.large.topStart
    val inner = AppShapes.extraSmall.topStart

    return when {
        itemCount <= 1 -> RoundedCornerShape(inner)
        itemIndex == 0 -> RoundedCornerShape(
            topStart = outer, topEnd = outer,
            bottomStart = inner, bottomEnd = inner,
        )
        itemIndex == itemCount - 1 -> RoundedCornerShape(
            topStart = inner, topEnd = inner,
            bottomStart = outer, bottomEnd = outer,
        )
        else -> RoundedCornerShape(inner)
    }
}