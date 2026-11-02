package pe.edu.upc.sems.shared.designsystem

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** SEMS motion: curve cubic-bezier(0.2, 0, 0, 1). */
object SemsMotion {
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Micro-interactions. */
    const val SHORT_MS = 150

    /** Modals and bottom sheets. */
    const val MEDIUM_MS = 300

    /** Long transitions. */
    const val LONG_MS = 500

    /** Returns [durationMs], or 0 when the user disabled animations in the system settings. */
    @Composable
    fun duration(durationMs: Int): Int = if (rememberReduceMotion()) 0 else durationMs
}

/** True when the system "Remove animations" option is on (animator duration scale = 0). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
