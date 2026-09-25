package com.phonetemp.app.ohaptics

import com.phonetemp.app.ohaptics.Prim.CLICK
import com.phonetemp.app.ohaptics.Prim.LOW_TICK
import com.phonetemp.app.ohaptics.Prim.QUICK_FALL
import com.phonetemp.app.ohaptics.Prim.SLOW_RISE
import com.phonetemp.app.ohaptics.Prim.THUD
import com.phonetemp.app.ohaptics.Prim.TICK

enum class Scene(val label: String, val hint: String) {
    SNAP("Snap", "Tap to assemble the dial"),
    KNOB("Knob", "Turn it, or flick it and let it coast"),
    DROP("Drop", "Tap to drop the ball into its ring"),
    ROLL("Roll", "Drag the ball, or tap to send it rolling"),
    BUBBLES("Bubbles", "Tap bubbles to pop them"),
    BALLOONS("Balloons", "Pop the balloons; the big one bursts"),
}

/**
 * Haptic patterns measured from the OnePlus O-Haptics intro video. OnePlus plays its haptics in
 * sync with the sound design, so each audio onset gives an event's timing, and its pitch and ring
 * time give its character: short high clicks, long low thuds. Scales follow the measured loudness
 * (-35 dB -> 0.3, -11 dB -> 1.0).
 */
object OHaptics {
    /**
     * Knob detent: 15 ms, ~7.5 kHz in the video. A TICK primitive is too faint on many motors even at
     * full scale, so each notch is a crisp CLICK instead (0.70 -> 0.98 after HapticGain).
     */
    val detent = steps(Step(CLICK, 0.70f))

    /**
     * Parts snapping together: 25-125 ms, 3.3-5.3 kHz. A two-stage click (catch, then seat) at
     * near-full strength; the measured loudness still orders them (0.36 -> 0.87, 0.70 -> 0.94).
     */
    fun snap(scale: Float) = steps(Step(CLICK, 0.8f + 0.2f * scale), Step(TICK, 0.6f + 0.4f * scale, 14))

    /** Dial settling onto its base: two 600-760 Hz thuds 75 ms apart, ringing ~450 ms. */
    val settle = steps(Step(THUD, 0.70f), Step(THUD, 0.75f, 75))

    /** Notch lining up after the settle. */
    val lockIn = steps(Step(CLICK, 0.47f), Step(CLICK, 0.66f, 70))

    /** Ball popping up out of the ring (2.9 kHz, -15 dB). */
    val dropLift = steps(Step(CLICK, 0.60f))

    /** Ball landing in the ring (1.2 kHz, -14 dB): the heaviest single hit in the video. */
    val dropImpact = steps(Step(THUD, 0.92f))

    /** Small bounce 295 ms later (610 Hz, -21 dB). */
    val dropBounce = steps(Step(LOW_TICK, 0.70f))

    /** Rolling texture: faint low ticks whose strength follows the speed. */
    fun roll(speed: Float) = steps(Step(LOW_TICK, (0.18f + 0.45f * speed).coerceIn(0.18f, 0.65f)))

    /** Ball hitting the end of the slider (1.6 kHz, -12.5 dB). */
    val rollHit = steps(Step(CLICK, 0.96f), Step(THUD, 0.55f, 20))

    /** Soft bubble pop: 500-800 Hz, 100-150 ms. Bigger bubbles pop lower and stronger. */
    fun bubblePop(size: Float) = steps(Step(LOW_TICK, (0.5f + 0.4f * size).coerceAtMost(0.9f)), Step(QUICK_FALL, 0.35f, 10))

    /** Two bubbles merging into one: slow swell then a low thud (549 Hz, 335 ms ring). */
    val bubbleMerge = steps(Step(SLOW_RISE, 0.45f), Step(THUD, 0.77f, 20))

    /** Balloon pop: sharp, 2.6-2.8 kHz, -12 dB, 85 ms. */
    val balloonPop = steps(Step(CLICK, 1.0f), Step(QUICK_FALL, 0.6f, 10))

    /** Big balloon burst: a crack, then four hits fading over ~370 ms (2.8 kHz sliding to 1.5 kHz). */
    val balloonBurst = steps(
        Step(CLICK, 0.89f), Step(THUD, 0.92f, 80), Step(LOW_TICK, 0.78f, 120),
        Step(LOW_TICK, 0.71f, 70), Step(LOW_TICK, 0.65f, 100),
    )

    /** Snap-scene choreography: (ms after tap, pattern). Offsets are the video's, from 1.465 s. */
    val assembly: List<Pair<Int, List<Step>>> = listOf(
        0 to snap(0.36f), 85 to snap(0.48f), 245 to snap(0.49f), 340 to snap(0.70f),
        870 to snap(0.48f), 985 to snap(0.54f),
        1935 to settle, 2825 to lockIn,
    )

    /** One reel cue: at [atMs] play [pattern] and show [scene]. */
    data class Cue(val atMs: Int, val scene: Scene, val pattern: List<Step>)

    /**
     * The whole intro, event for event: 0 ms here is 1.465 s into the video. Knob ticks are the
     * measured decelerating train (80 ms apart slowing to 325 ms); the roll texture fills the
     * 1.1 s rumble before the end hit.
     */
    val reel: List<Cue> = buildList {
        assembly.forEach { (t, p) -> add(Cue(t, Scene.SNAP, p)) }
        listOf(3675, 3755, 3840, 3910, 3990, 4060, 4130, 4220, 4365, 4515, 4645, 4785, 4940, 5210, 5535)
            .forEach { add(Cue(it, Scene.KNOB, detent)) }
        add(Cue(6185, Scene.DROP, dropLift))
        add(Cue(6755, Scene.DROP, dropImpact))
        add(Cue(7050, Scene.DROP, dropBounce))
        (0 until 16).forEach { i -> add(Cue(7300 + i * 70, Scene.ROLL, roll(0.25f + i / 30f))) }
        add(Cue(8465, Scene.ROLL, rollHit))
        add(Cue(11510, Scene.BUBBLES, bubbleMerge))
        add(Cue(11680, Scene.BUBBLES, steps(Step(THUD, 0.90f))))
        add(Cue(12590, Scene.BUBBLES, bubblePop(0.45f)))
        add(Cue(12705, Scene.BUBBLES, bubblePop(0.10f)))
        add(Cue(13060, Scene.BUBBLES, bubblePop(0.40f)))
        add(Cue(13625, Scene.BUBBLES, bubblePop(0.05f)))
        add(Cue(13965, Scene.BUBBLES, bubblePop(0.30f)))
        add(Cue(16130, Scene.BALLOONS, balloonPop))
        add(Cue(16955, Scene.BALLOONS, balloonPop))
        add(Cue(17530, Scene.BALLOONS, balloonPop))
        add(Cue(18925, Scene.BALLOONS, balloonBurst))
    }.sortedBy { it.atMs }

    /** When each scene takes the stage during the reel (it switches ahead of its first cue). */
    val reelScenes: List<Pair<Int, Scene>> = listOf(
        0 to Scene.SNAP, 3300 to Scene.KNOB, 5850 to Scene.DROP, 7200 to Scene.ROLL,
        9400 to Scene.BUBBLES, 15700 to Scene.BALLOONS,
    )

    const val REEL_MS = 19800
}
