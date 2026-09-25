package com.phonetemp.app.ohaptics

import android.view.View
import com.phonetemp.app.data.haptics.FeedbackId
import com.phonetemp.app.ohaptics.Prim.CLICK
import com.phonetemp.app.ohaptics.Prim.LOW_TICK
import com.phonetemp.app.ohaptics.Prim.QUICK_FALL
import com.phonetemp.app.ohaptics.Prim.QUICK_RISE
import com.phonetemp.app.ohaptics.Prim.SLOW_RISE
import com.phonetemp.app.ohaptics.Prim.SPIN
import com.phonetemp.app.ohaptics.Prim.THUD
import com.phonetemp.app.ohaptics.Prim.TICK

/**
 * Pulse Lab's Feedback grid, de-duplicated.
 *
 * Android maps its 19 HapticFeedbackConstants onto only a handful of vibrator effects (tick, click,
 * heavy click, double click, texture tick), so on most phones - OnePlus included - many cards felt
 * the same. The grid keeps one system constant per distinct feel and turns every other card into its
 * own composed pattern, each different in rhythm and weight, so no two cards feel alike.
 *
 * Only the grid is affected (patched into its cell and click handler); the other Pulse Lab examples
 * still use the real system constants.
 */
object FeedbackGrid {
    class Pattern(val label: String, val technical: String, val steps: List<Step>)

    /** System constants kept as they are: one per underlying feel. */
    val kept = setOf("Confirm", "Reject", "ToggleOn", "ToggleOff", "LongPress", "ClockTick", "TextHandleMove")

    /** The cards that duplicated a kept feel, re-cut as distinct patterns (keyed by FeedbackId name). */
    val replaced: Map<String, Pattern> = mapOf(
        "KeyboardPress" to Pattern("Double tap", "CLICK · CLICK", steps(Step(CLICK, 1f), Step(CLICK, 1f, 90))),
        "KeyboardRelease" to Pattern("Heartbeat", "THUD · THUD", steps(Step(THUD, 1f), Step(THUD, 0.55f, 130))),
        "ContextClick" to Pattern("Triple knock", "LOW_TICK ×3", steps(Step(LOW_TICK, 1f), Step(LOW_TICK, 1f, 110), Step(LOW_TICK, 1f, 110))),
        "GestureStart" to Pattern("Swell", "SLOW_RISE → CLICK", steps(Step(SLOW_RISE, 1f), Step(CLICK, 0.9f, 20))),
        "GestureEnd" to Pattern("Release", "QUICK_FALL → LOW_TICK", steps(Step(QUICK_FALL, 1f), Step(LOW_TICK, 0.8f, 90))),
        "ThresholdActivate" to Pattern("Latch", "QUICK_RISE → THUD", steps(Step(QUICK_RISE, 0.9f), Step(THUD, 1f, 10))),
        "ThresholdDeactivate" to Pattern("Unlatch", "THUD → QUICK_FALL", steps(Step(THUD, 0.8f), Step(QUICK_FALL, 1f, 40))),
        "VirtualKey" to Pattern("Spin-up", "SPIN", steps(Step(SPIN, 1f))),
        "VirtualKeyRelease" to Pattern("Bounce", "THUD 1.0 · 0.5 · 0.25", steps(Step(THUD, 1f), Step(THUD, 0.5f, 180), Step(THUD, 0.25f, 140))),
        "DragStart" to Pattern("Lift off", "LOW_TICK → QUICK_RISE", steps(Step(LOW_TICK, 1f), Step(QUICK_RISE, 1f, 30))),
        "SegmentTick" to Pattern("Ripple", "TICK ramp ×5", steps(
            Step(TICK, 0.5f), Step(TICK, 0.75f, 45), Step(TICK, 1f, 45), Step(TICK, 0.75f, 45), Step(TICK, 0.5f, 45))),
        "SegmentFrequentTick" to Pattern("Alarm buzz", "CLICK ×4", steps(
            Step(CLICK, 1f), Step(CLICK, 1f, 60), Step(CLICK, 1f, 60), Step(CLICK, 1f, 60))),
    )

    @JvmStatic fun label(id: FeedbackId): String = replaced[id.name]?.label ?: id.label
    @JvmStatic fun technical(id: FeedbackId): String = replaced[id.name]?.technical ?: id.technical

    /** Plays the card's own pattern; false means "a kept system constant, play it as before". */
    @JvmStatic
    fun play(view: View, id: FeedbackId): Boolean {
        val p = replaced[id.name] ?: return false
        HapticPlayer(view).play(p.steps)
        return true
    }
}
