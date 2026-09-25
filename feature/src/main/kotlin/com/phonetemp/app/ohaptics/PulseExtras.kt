package com.phonetemp.app.ohaptics

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.phonetemp.app.ohaptics.Prim.CLICK
import com.phonetemp.app.ohaptics.Prim.LOW_TICK
import com.phonetemp.app.ohaptics.Prim.QUICK_FALL
import com.phonetemp.app.ohaptics.Prim.QUICK_RISE
import com.phonetemp.app.ohaptics.Prim.SLOW_RISE
import com.phonetemp.app.ohaptics.Prim.THUD
import com.phonetemp.app.ohaptics.Prim.TICK
import com.phonetemp.app.ui.theme.PtColors
import com.phonetemp.app.ui.theme.PtType
import com.phonetemp.app.ui.theme.Radius
import com.phonetemp.app.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Two Pulse Lab examples rebuilt around direct manipulation (patched in by build.sh in place of the
 * app's "Rise and fall" sequence card and its "Drag threshold" example).
 */
object PulseExtras {
    /** Height of the ball on the pad, 0 = bottom, 1 = top, sets how strong a pulse is. */
    fun riseStep(height: Float) = steps(Step(QUICK_RISE, 0.35f + 0.65f * height.coerceIn(0f, 1f)))
    fun fallStep(height: Float) = steps(Step(QUICK_FALL, 0.35f + 0.65f * height.coerceIn(0f, 1f)))
    val sideStep = steps(Step(TICK, 0.9f))
    val topBump = steps(Step(THUD, 1f))
    val floorBump = steps(Step(LOW_TICK, 1f), Step(THUD, 0.5f, 15))
    /** The Play gesture: a slow rise, then a quick fall, as one composition. */
    val riseAndFall = steps(Step(SLOW_RISE, 1f), Step(QUICK_FALL, 1f))

    /** Drag-threshold tension: faint while far, firmer as the knob nears the line. */
    fun tension(progressToLine: Float) = steps(Step(LOW_TICK, 0.25f + 0.55f * progressToLine.coerceIn(0f, 1f)))
    val latch = steps(Step(QUICK_RISE, 0.9f), Step(THUD, 1f, 10))
    val unlatch = steps(Step(THUD, 0.8f), Step(QUICK_FALL, 1f, 40))
    val lockIn = steps(Step(CLICK, 1f), Step(CLICK, 0.7f, 70))
    val springHome = steps(Step(LOW_TICK, 0.6f))

    const val STEP_DP = 18f         // distance between motion pulses
    const val MIN_GAP_MS = 45L      // never re-trigger faster than a pulse can be felt
    const val THRESHOLD = 0.62f     // where the line sits on the track
    const val HYSTERESIS = 0.03f    // no chatter when resting on the line
}

@Composable
private fun ExampleShell(title: String, caption: String, colors: PtColors, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(AppUi.glass(Modifier.fillMaxWidth(), colors, RoundedCornerShape(Radius.md)).padding(Space.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, style = PtType.body)
                AppUi.cardCaption(caption, currentComposer)
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(Space.m))
        content()
    }
}

// ------------------------------------------------------------------ Rise and fall

/** Drag the ball: up rises (stronger the higher it is), down falls, sideways ticks; Play lifts and drops it. */
@Composable
fun RiseFallExample(view: View, still: Boolean) {
    val colors = AppUi.colors(currentComposer)
    val player = remember(view) { HapticPlayer(view) }
    val scope = rememberCoroutineScope()
    val pos = remember { Animatable(Offset(0.5f, 0.85f), Offset.VectorConverter) }
    var playing by remember { mutableStateOf(false) }

    ExampleShell(
        "Rise and fall", "Move the ball: up rises, down falls, sideways ticks", colors,
        trailing = {
            Text(
                if (playing) "…" else "Play",
                color = colors.accent,
                style = PtType.body,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .semantics { role = Role.Button; contentDescription = "Play rise and fall" }
                    .clickable(enabled = !playing) {
                        scope.launch {
                            playing = true
                            val (rise, fall) = player.durationsMs(SLOW_RISE, QUICK_FALL)
                            player.play(PulseExtras.riseAndFall)
                            pos.animateTo(Offset(0.5f, 0.12f), tween(rise.toInt(), easing = CubicBezierEasing(0.4f, 0f, 0.9f, 1f)))
                            pos.animateTo(Offset(0.5f, 0.88f), tween(fall.toInt(), easing = CubicBezierEasing(0.5f, 0f, 1f, 0.6f)))
                            pos.animateTo(Offset(0.5f, 0.85f), spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                            playing = false
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        },
    ) {
        Box(
            AppUi.recessed(Modifier.fillMaxWidth().height(190.dp), colors, RoundedCornerShape(Radius.md))
                .clip(RoundedCornerShape(Radius.md))
                .semantics { contentDescription = "Rise and fall pad" }
                .pointerInput(Unit) {
                    val stepPx = PulseExtras.STEP_DP * density
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (playing) return@awaitEachGesture
                        fun toUnit(p: Offset) = Offset((p.x / size.width).coerceIn(0.06f, 0.94f), (p.y / size.height).coerceIn(0.1f, 0.9f))
                        var last = down.position
                        var accX = 0f
                        var accY = 0f
                        var lastPulse = 0L
                        var atEdge = false
                        scope.launch { pos.snapTo(toUnit(down.position)) }
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val p = change.position
                            accX += p.x - last.x
                            accY += p.y - last.y
                            last = p
                            val u = toUnit(p)
                            scope.launch { pos.snapTo(u) }
                            val height = 1f - (u.y - 0.1f) / 0.8f
                            val now = change.uptimeMillis
                            val edge = u.y <= 0.1001f || u.y >= 0.8999f
                            if (edge && !atEdge) {
                                player.play(if (u.y < 0.5f) PulseExtras.topBump else PulseExtras.floorBump)
                                lastPulse = now; accX = 0f; accY = 0f
                            } else if (now - lastPulse >= PulseExtras.MIN_GAP_MS) {
                                when {
                                    accY <= -stepPx -> { player.play(PulseExtras.riseStep(height)); lastPulse = now; accY = 0f; accX = 0f }
                                    accY >= stepPx -> { player.play(PulseExtras.fallStep(height)); lastPulse = now; accY = 0f; accX = 0f }
                                    abs(accX) >= stepPx -> { player.play(PulseExtras.sideStep); lastPulse = now; accX = 0f }
                                }
                            }
                            atEdge = edge
                        }
                        scope.launch { pos.animateTo(Offset(pos.value.x, 0.85f), spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // height guide: the rise gets stronger toward the top
                for (i in 0..8) {
                    val y = size.height * (0.1f + 0.8f * i / 8f)
                    val w = 10f + (8 - i) * 3f
                    drawLine(colors.textFaint.copy(alpha = 0.25f + 0.07f * (8 - i)), Offset(18f, y), Offset(18f + w, y), 2f + (8 - i) * 0.3f, StrokeCap.Round)
                }
                val c = Offset(pos.value.x * size.width, pos.value.y * size.height)
                val r = min(size.width, size.height) * 0.085f
                val lift = 1f - (pos.value.y - 0.1f) / 0.8f
                drawCircle(Color.Black.copy(alpha = 0.25f * (1f - lift * 0.6f)), r * (1.1f - lift * 0.3f), Offset(c.x, size.height * 0.93f))
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), colors.accent), c + Offset(-r * 0.35f, -r * 0.4f), r * 1.5f), r, c)
            }
        }
    }
}

// ------------------------------------------------------------------ Drag threshold

/** Drag the knob across the line: tension builds, crossing latches, letting go past it locks. */
@Composable
fun DragThresholdPad(view: View) {
    val colors = AppUi.colors(currentComposer)
    val player = remember(view) { HapticPlayer(view) }
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }      // knob is past the line right now
    var locked by remember { mutableStateOf(false) }     // released past the line
    val glow = remember { Animatable(0f) }

    ExampleShell("Drag threshold", if (locked) "Activated — tap to reset" else "Pull past the line to activate", colors) {
        Box(
            AppUi.recessed(Modifier.fillMaxWidth().height(84.dp), colors, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .semantics {
                    contentDescription = "Drag threshold track"
                    stateDescription = when { locked -> "activated"; armed -> "armed"; else -> "idle" }
                }
                .pointerInput(Unit) {
                    val stepPx = PulseExtras.STEP_DP * density
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (locked) {   // tap to reset
                            locked = false; armed = false
                            scope.launch { glow.animateTo(0f, tween(250)) }
                            scope.launch { x.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)); player.play(PulseExtras.springHome) }
                            return@awaitEachGesture
                        }
                        val knob = size.height / 2f
                        val travel = size.width - 2 * knob
                        var last = down.position.x
                        var acc = 0f
                        var lastPulse = 0L
                        scope.launch { x.stop() }
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val dx = change.position.x - last
                            last = change.position.x
                            val to = (x.value + dx / travel).coerceIn(0f, 1f)
                            scope.launch { x.snapTo(to) }
                            acc += dx
                            val now = change.uptimeMillis
                            if (!armed && to >= PulseExtras.THRESHOLD + PulseExtras.HYSTERESIS) {
                                armed = true; player.play(PulseExtras.latch); lastPulse = now; acc = 0f
                                scope.launch { glow.animateTo(1f, tween(120)) }
                            } else if (armed && to <= PulseExtras.THRESHOLD - PulseExtras.HYSTERESIS) {
                                armed = false; player.play(PulseExtras.unlatch); lastPulse = now; acc = 0f
                                scope.launch { glow.animateTo(0f, tween(200)) }
                            } else if (!armed && abs(acc) >= stepPx && now - lastPulse >= PulseExtras.MIN_GAP_MS) {
                                player.play(PulseExtras.tension(to / PulseExtras.THRESHOLD)); lastPulse = now; acc = 0f
                            }
                        }
                        if (armed) {
                            locked = true
                            scope.launch { x.animateTo(1f, tween(160)); player.play(PulseExtras.lockIn) }
                        } else {
                            scope.launch { x.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)); player.play(PulseExtras.springHome) }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val knob = size.height / 2f
                val travel = size.width - 2 * knob
                val lineX = knob + travel * PulseExtras.THRESHOLD
                val g = glow.value
                // filled progress behind the knob
                val kx = knob + travel * x.value
                drawRoundRect(colors.accent.copy(alpha = 0.10f + 0.25f * g), Offset(0f, 0f), Size(kx + knob, size.height), CornerRadius(knob))
                // the line
                drawLine(
                    if (g > 0f) colors.accent.copy(alpha = 0.5f + 0.5f * g) else colors.textFaint,
                    Offset(lineX, size.height * 0.25f), Offset(lineX, size.height * 0.75f), 4f + 3f * g, StrokeCap.Round,
                )
                // knob
                val r = knob * 0.72f * (1f + 0.08f * g)
                drawCircle(Color.Black.copy(alpha = 0.22f), r, Offset(kx + r * 0.08f, knob + r * 0.14f))
                drawCircle(if (g > 0f) colors.accent else colors.plate, r, Offset(kx, knob))
                drawCircle(colors.line, r, Offset(kx, knob), style = Stroke(2f))
            }
        }
    }
}
