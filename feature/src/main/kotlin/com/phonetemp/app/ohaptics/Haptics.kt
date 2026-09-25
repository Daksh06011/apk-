package com.phonetemp.app.ohaptics

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The building blocks OnePlus-style haptics are made of: Android's composition primitives.
 * [fallbackMs]/[fallbackAmp] shape a one-pulse approximation for phones without primitive support.
 */
enum class Prim(val id: Int, val minSdk: Int, val fallbackMs: Long, val fallbackAmp: Int) {
    CLICK(1, 30, 12, 255),
    THUD(2, 31, 30, 255),
    SPIN(3, 31, 60, 160),
    QUICK_RISE(4, 30, 40, 200),
    SLOW_RISE(5, 30, 90, 150),
    QUICK_FALL(6, 30, 40, 180),
    TICK(7, 30, 6, 140),
    LOW_TICK(8, 31, 14, 150);

    /** Nearest primitive available on API 30 (THUD/SPIN/LOW_TICK arrived in 31). */
    val api30Substitute: Prim
        get() = when (this) {
            THUD -> CLICK
            SPIN -> QUICK_RISE
            LOW_TICK -> TICK
            else -> this
        }
}

/** One primitive at [scale] (0..1), starting [delayMs] after the previous step. */
data class Step(val prim: Prim, val scale: Float, val delayMs: Int = 0)

fun steps(vararg s: Step): List<Step> = s.toList()

/**
 * Output gain for Tacta. The patterns keep the video's relative strengths; this lifts them all so
 * even the faint ones (knob ticks at 0.48, snaps at 0.36) are clearly felt, while the strongest
 * hits saturate at full strength: 0.36 -> 0.68, 0.48 -> 0.78, 0.70 -> 0.94, >= 0.78 -> 1.0.
 */
object HapticGain {
    const val FLOOR = 0.38f
    const val SLOPE = 0.85f
    fun apply(scale: Float): Float = (FLOOR + SLOPE * scale.coerceIn(0f, 1f)).coerceAtMost(1f)
}

/** How a pattern was actually delivered (for the on-screen readout and tests). */
enum class Route { COMPOSITION, WAVEFORM, ONE_SHOT, VIEW_FEEDBACK, NONE }

/** Test/diagnostic hook: every pattern played, with the route the device took. */
object HapticLog {
    @Volatile var listener: ((List<Step>, Route) -> Unit)? = null
}

class HapticPlayer(private val view: View) {
    private val vibrator: Vibrator? = findVibrator(view.context)

    fun play(pattern: List<Step>): Route {
        if (pattern.isEmpty()) return Route.NONE
        val route = deliver(pattern)
        HapticLog.listener?.invoke(pattern, route)
        return route
    }

    private fun deliver(pattern: List<Step>): Route {
        val v = vibrator
        if (v == null || !v.hasVibrator()) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            return Route.VIEW_FEEDBACK
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= 30 && composition(v, pattern)) return Route.COMPOSITION
            if (v.hasAmplitudeControl()) {
                v.vibrate(waveform(pattern))
                Route.WAVEFORM
            } else {
                val total = pattern.sumOf { it.prim.fallbackMs }.coerceAtMost(80)
                v.vibrate(VibrationEffect.createOneShot(total, VibrationEffect.DEFAULT_AMPLITUDE))
                Route.ONE_SHOT
            }
        }.getOrElse {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            Route.VIEW_FEEDBACK
        }
    }

    private fun composition(v: Vibrator, pattern: List<Step>): Boolean {
        val resolved = pattern.map { s ->
            val p = if (Build.VERSION.SDK_INT >= s.prim.minSdk && v.areAllPrimitivesSupported(s.prim.id)) s.prim
            else s.prim.api30Substitute
            s.copy(prim = p)
        }
        if (!v.areAllPrimitivesSupported(*resolved.map { it.prim.id }.distinct().toIntArray())) return false
        val c = VibrationEffect.startComposition()
        resolved.forEach { c.addPrimitive(it.prim.id, HapticGain.apply(it.scale), it.delayMs) }
        v.vibrate(c.compose())
        return true
    }

    private fun waveform(pattern: List<Step>): VibrationEffect {
        val timings = ArrayList<Long>()
        val amps = ArrayList<Int>()
        pattern.forEach { s ->
            if (s.delayMs > 0) { timings += s.delayMs.toLong(); amps += 0 }
            val a = (255 * HapticGain.apply(s.scale) * s.prim.fallbackAmp / 255f).toInt().coerceIn(1, 255)
            when (s.prim) {
                Prim.QUICK_FALL -> { timings += 15; amps += a; timings += 25; amps += a / 2 }
                Prim.SLOW_RISE, Prim.QUICK_RISE -> { timings += s.prim.fallbackMs / 2; amps += a / 2; timings += s.prim.fallbackMs / 2; amps += a }
                else -> { timings += s.prim.fallbackMs; amps += a }
            }
        }
        return VibrationEffect.createWaveform(timings.toLongArray(), amps.toIntArray(), -1)
    }

    private companion object {
        fun findVibrator(ctx: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }
}

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

/**
 * Suspends until SystemClock.uptimeMillis() reaches [at], via Handler.postAtTime on the main
 * looper: millisecond-exact against the same clock as touch events and frames, with no drift from
 * coroutine delay() implementations. Every timed haptic sequence schedules off a fixed start, so
 * one late wake-up never shifts the hits after it.
 */
suspend fun awaitUptime(at: Long) {
    if (SystemClock.uptimeMillis() >= at) return
    suspendCancellableCoroutine<Unit> { cont ->
        val tick = Runnable { if (cont.isActive) cont.resume(Unit) }
        mainHandler.postAtTime(tick, at)
        cont.invokeOnCancellation { mainHandler.removeCallbacks(tick) }
    }
}
