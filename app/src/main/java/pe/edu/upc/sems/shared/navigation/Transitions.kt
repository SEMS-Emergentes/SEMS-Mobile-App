package pe.edu.upc.sems.shared.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import pe.edu.upc.sems.shared.designsystem.SemsMotion

// Screen transitions: fade with the SEMS curve, 300 ms; instant when the system removes animations.

@Composable
internal fun semsEnterTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition {
    val duration = SemsMotion.duration(SemsMotion.MEDIUM_MS)
    return { fadeIn(tween(duration, easing = SemsMotion.Emphasized)) }
}

@Composable
internal fun semsExitTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition {
    val duration = SemsMotion.duration(SemsMotion.MEDIUM_MS)
    return { fadeOut(tween(duration, easing = SemsMotion.Emphasized)) }
}
