package example.nucleus.ui.themes

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import example.nucleus.utils.LocalAnimationsEnabled

/**
 * Single source of UI motion for PaltaSound (Material 3 Expressive on desktop).
 *
 * Prefer these helpers over ad-hoc `tween(220/300)` / local springs so nav, mini player,
 * tabs, and chrome stay consistent. Always pair with [LocalAnimationsEnabled] via
 * [uiSpring] / [uiTween] / [rememberUiSpring] so reduce-motion snaps instantly.
 *
 * Motion language: **smooth ease, no bounce**. Springs use critically-damped ratios so
 * chrome settles without overshoot; fades/layout prefer [expressiveEasing] tweens.
 *
 * | Spec | When |
 * |---|---|
 * | [expressiveSpring] | Layout emphasis: rail, mini player show/hide (no bounce) |
 * | [interactionSpring] | Hover/press, thumb grow — snappy, critically damped |
 * | [contentSpring] | Hierarchy (lyrics line, title weight/scale) |
 * | [expressiveFadeTween] / [expressiveTween] | Crossfades, alpha, color scheme bloom |
 */

/**
 * Material-like emphasized decelerate — settles quickly without elastic rebound.
 * Control points tuned for desktop UI (slightly softer than mobile MD emphasized).
 */
val expressiveEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/** Standard emphasized accelerate for exits / dismiss. */
val expressiveAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

/**
 * Soft layout spring — critically damped (no overshoot).
 * Prefer for expand/collapse and large chrome moves when a spring feel is desired.
 */
fun <T> expressiveSpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 380f,
)

/**
 * Fast interaction spring — no bounce, high stiffness for thumbs/hover morphs.
 */
fun <T> interactionSpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh,
)

/**
 * Soft content spring for hierarchy — active lyric line, weight, scale.
 */
fun <T> contentSpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** Duración corta para fundidos que acompañan al motion (240ms). */
const val expressiveFadeDuration = 240

/** Slightly longer fade for color-scheme / seed transitions. */
const val expressiveColorDuration = 300

/** Smooth scroll / longer content motion (synced lyrics). */
const val expressiveScrollDuration = 480

/** Default duration for layout morphs that use tween instead of spring. */
const val expressiveLayoutDuration = 320

fun expressiveFadeTween(): FiniteAnimationSpec<Float> =
    tween(durationMillis = expressiveFadeDuration, easing = expressiveEasing)

fun <T> expressiveTween(
    durationMillis: Int = expressiveFadeDuration,
    delayMillis: Int = 0,
    easing: Easing = expressiveEasing,
): FiniteAnimationSpec<T> = tween(
    durationMillis = durationMillis,
    delayMillis = delayMillis,
    easing = easing,
)

/** Layout morph tween (expand/slide) — preferred over bouncy springs. */
fun <T> expressiveLayoutTween(
    durationMillis: Int = expressiveLayoutDuration,
    delayMillis: Int = 0,
): FiniteAnimationSpec<T> = expressiveTween(durationMillis, delayMillis, expressiveEasing)

/**
 * Spring or [snap] depending on [animationsEnabled].
 * Use at non-composable call sites that already read the preference.
 */
fun <T> uiSpring(
    animationsEnabled: Boolean,
    spec: () -> SpringSpec<T> = { expressiveSpring() },
): FiniteAnimationSpec<T> = if (animationsEnabled) spec() else snap()

/**
 * Spec espacial oficial de M3 Expressive (`MaterialTheme.motionScheme`), o [snap] con
 * reduce-motion. Para dar a un componente el ritmo del sistema en vez de una curva elegida
 * a mano. Los helpers de arriba son la vía del chrome propio, que busca "sin rebote";
 * este es el de los componentes M3 que ya traen el suyo.
 *
 * @param fast Ritmo `fastSpatialSpec`: menús y elementos que aparecen de golpe.
 * @param slow Ritmo `slowSpatialSpec`: algo que entra con calma desde el fondo.
 */
@Composable
fun <T> rememberUiSpatialSpec(fast: Boolean = false, slow: Boolean = false): FiniteAnimationSpec<T> {
    val enabled = LocalAnimationsEnabled.current
    return if (!enabled) {
        snap()
    } else {
        val scheme = MaterialTheme.motionScheme
        when {
            fast -> scheme.fastSpatialSpec()
            slow -> scheme.slowSpatialSpec()
            else -> scheme.defaultSpatialSpec()
        }
    }
}

/**
 * Spec de efectos oficial (color/size) de M3 Expressive, o [snap] con reduce-motion.
 * @param fast Ritmo `fastEffectsSpec`, el que usan los menús para su alpha.
 */
@Composable
fun <T> rememberUiEffectsSpec(fast: Boolean = false): FiniteAnimationSpec<T> {
    val enabled = LocalAnimationsEnabled.current
    return if (!enabled) snap() else MaterialTheme.motionScheme.let {
        if (fast) it.fastEffectsSpec() else it.defaultEffectsSpec()
    }
}

/**
 * Tween or [snap] depending on [animationsEnabled].
 */
fun <T> uiTween(
    animationsEnabled: Boolean,
    durationMillis: Int = expressiveFadeDuration,
    delayMillis: Int = 0,
    easing: Easing = expressiveEasing,
): FiniteAnimationSpec<T> = if (animationsEnabled) {
    expressiveTween(durationMillis, delayMillis, easing)
} else {
    snap()
}

/** Generic gate: keep [spec] when animations on, else [snap]. */
fun <T> motionSpec(
    animationsEnabled: Boolean,
    spec: AnimationSpec<T>,
): AnimationSpec<T> = if (animationsEnabled) spec else snap()

/** Reads [LocalAnimationsEnabled] — preferred inside composables. */
@Composable
fun <T> rememberUiSpring(
    spec: () -> SpringSpec<T> = { expressiveSpring() },
): FiniteAnimationSpec<T> {
    val enabled = LocalAnimationsEnabled.current
    return uiSpring(enabled, spec)
}

/** Reads [LocalAnimationsEnabled] for tweens. */
@Composable
fun <T> rememberUiTween(
    durationMillis: Int = expressiveFadeDuration,
    delayMillis: Int = 0,
    easing: Easing = expressiveEasing,
): FiniteAnimationSpec<T> {
    val enabled = LocalAnimationsEnabled.current
    return uiTween(enabled, durationMillis, delayMillis, easing)
}

/** Content hierarchy spring with reduce-motion. */
@Composable
fun <T> rememberContentSpring(): FiniteAnimationSpec<T> =
    rememberUiSpring { contentSpring() }

/** Interaction spring with reduce-motion. */
@Composable
fun <T> rememberInteractionSpring(): FiniteAnimationSpec<T> =
    rememberUiSpring { interactionSpring() }

/** Layout tween with reduce-motion — prefer for expand/slide chrome. */
@Composable
fun <T> rememberExpressiveLayout(): FiniteAnimationSpec<T> {
    val enabled = LocalAnimationsEnabled.current
    return if (enabled) expressiveLayoutTween() else snap()
}

/**
 * [MotionScheme] sin ninguna animación: todas sus ranuras devuelven [snap].
 *
 * Es la pieza que hace que la preferencia "animaciones" llegue a los componentes de
 * Material 3 por sí sola. `DropdownMenu`, `ModalBottomSheet`, `Snackbar`, `LoadingIndicator`
 * y compañía leen `MaterialTheme.motionScheme` internamente y no hay forma de pasarles
 * un spec; sustituyendo el scheme entero por este en [AppTheme], todos se vuelven
 * instantáneos de una sola vez, en lugar de tener que parchear cada llamada.
 *
 * No afecta al chrome propio, que ya consulta [LocalAnimationsEnabled] por su cuenta.
 */
object InstantMotionScheme : MotionScheme {
    @Suppress("UNCHECKED_CAST")
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    @Suppress("UNCHECKED_CAST")
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    @Suppress("UNCHECKED_CAST")
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    @Suppress("UNCHECKED_CAST")
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    @Suppress("UNCHECKED_CAST")
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    @Suppress("UNCHECKED_CAST")
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
