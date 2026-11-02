package pe.edu.upc.sems.shared.util

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SlowNotice {
    /** After this long, submit buttons show "Conectando…" (a Render cold start can take over a minute). */
    const val THRESHOLD_MS = 4_000L
}

/** Runs [block] and calls [onSlow] if it is still running after [SlowNotice.THRESHOLD_MS]. */
suspend fun <T> withSlowNotice(onSlow: () -> Unit, block: suspend () -> T): T = coroutineScope {
    val timer = launch {
        delay(SlowNotice.THRESHOLD_MS)
        onSlow()
    }
    try {
        block()
    } finally {
        timer.cancel()
    }
}
