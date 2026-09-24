package com.phonetemp.app.ohaptics

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import com.phonetemp.app.ui.theme.PtColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

// ---------------------------------------------------------------- shared drawing

private fun DrawScope.blueDial(center: Offset, r: Float, notchDeg: Float) {
    drawCircle(Color.Black.copy(alpha = 0.16f), r * 1.02f, center + Offset(0f, r * 0.10f))
    drawCircle(
        Brush.radialGradient(listOf(OneBlueLight, OneBlue, OneBlueDeep), center + Offset(-r * 0.35f, -r * 0.4f), r * 1.6f),
        r, center,
    )
    drawCircle(Color.White.copy(alpha = 0.18f), r * 0.93f, center, style = Stroke(r * 0.04f))
    val a = Math.toRadians(notchDeg.toDouble())
    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
    drawLine(OneBlueDeep, center + dir * (r * 0.58f), center + dir * (r * 0.80f), r * 0.10f, StrokeCap.Round)
}

private fun DrawScope.tickRing(center: Offset, r: Float, colors: PtColors, litDeg: Float?, count: Int = 30) {
    for (i in 0 until count) {
        val deg = i * 360f / count
        val a = Math.toRadians(deg.toDouble())
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        val near = litDeg != null && abs(((deg - litDeg) % 360 + 540) % 360 - 180) < 360f / count / 2
        drawLine(
            if (near) OneBlue else colors.textFaint,
            center + dir * r, center + dir * (r * 1.09f),
            if (near) 5f else 3f, StrokeCap.Round,
        )
    }
}

private fun DrawScope.sphere(center: Offset, r: Float, base: Color, light: Color = Color.White) {
    drawCircle(Color.Black.copy(alpha = 0.14f), r, center + Offset(r * 0.18f, r * 0.28f))
    drawCircle(Brush.radialGradient(listOf(light, base), center + Offset(-r * 0.4f, -r * 0.45f), r * 1.5f), r, center)
}

// ---------------------------------------------------------------- knob

@Composable
internal fun KnobScene(fire: (List<Step>) -> Unit, signal: CueSignal, colors: PtColors) {
    var angle by remember { mutableFloatStateOf(-90f) }
    var detentIdx by remember { mutableStateOf(floor(-90f / DETENT).toInt()) }
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(-90f) }

    fun moved(to: Float) {
        angle = to
        val idx = floor(to / DETENT).toInt()
        if (idx != detentIdx) { detentIdx = idx; fire(OHaptics.detent) }
    }

    // Tour: one detent per measured tick. Launched in the scene's scope so the next cue (70-80 ms
    // later) retargets the spin instead of cancelling it mid-motion.
    var tourTarget by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(signal.id) {
        if (signal.cue?.scene != Scene.KNOB) { tourTarget = null; return@LaunchedEffect }
        val target = (tourTarget ?: angle) + DETENT
        tourTarget = target
        scope.launch {
            spin.animateTo(target, tween(90, easing = EaseOut)) { angle = value }
            detentIdx = floor(angle / DETENT).toInt()
        }
    }

    StageCanvas(
        // Raw pointer tracking (no touch slop): the dial follows the finger from the first pixel.
        // Fling speed comes from the events' own timestamps over the last 100 ms.
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scope.launch { spin.stop() }
                val c = Offset(size.width / 2f, size.height / 2f)
                val deadZone = min(size.width, size.height) * 0.06f
                var last = c.angleTo(down.position)
                val trail = ArrayDeque<Pair<Long, Float>>()
                trail.addLast(down.uptimeMillis to angle)
                var releasedAt = down.uptimeMillis
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    releasedAt = change.uptimeMillis
                    if (!change.pressed) break
                    change.consume()
                    if ((change.position - c).getDistance() < deadZone) continue   // angle is unstable at the hub
                    val a = c.angleTo(change.position)
                    var d = a - last
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    last = a
                    moved(angle + d)
                    trail.addLast(change.uptimeMillis to angle)
                    while (trail.size > 2 && change.uptimeMillis - trail.first().first > 100) trail.removeFirst()
                }
                val first = trail.first()
                val lastPoint = trail.last()
                val dt = (lastPoint.first - first.first).coerceAtLeast(1)
                val fresh = releasedAt - lastPoint.first < 80   // a pause before lifting cancels the fling
                val v = if (fresh) (lastPoint.second - first.second) / dt * 1000f else 0f
                if (abs(v) > 120f) scope.launch {   // flick: coast down, ticks slowing like the video
                    spin.snapTo(angle)
                    spin.animateDecay(v.coerceIn(-2000f, 2000f), exponentialDecay(frictionMultiplier = 1.3f)) { moved(value) }
                }
            }
        },
    ) {
        val c = center
        val r = min(size.width, size.height) * 0.30f
        tickRing(c, r * 1.30f, colors, angle)
        drawCircle(colors.plate, r * 1.14f, c)
        drawCircle(colors.line, r * 1.14f, c, style = Stroke(2f))
        blueDial(c, r, angle)
    }
}

private const val DETENT = 12f   // 30 notches, as on the video's dial

// ---------------------------------------------------------------- drop

@Composable
internal fun DropScene(fire: (List<Step>) -> Unit, signal: CueSignal, colors: PtColors) {
    val height = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Choreography matches the video: lift, impact 570 ms later, bounce 295 ms after that.
    suspend fun drop(haptics: Boolean) = coroutineScope {
        busy = true
        // Haptics run on their own clock so frame timing can never push a hit late:
        // lift at 0, impact at 570 ms, bounce at 865 ms (the video's 6.185 / 6.755 / 7.050 s).
        if (haptics) launch {
            val start = SystemClock.uptimeMillis()
            for ((at, p) in listOf(0 to OHaptics.dropLift, 570 to OHaptics.dropImpact, 865 to OHaptics.dropBounce)) {
                awaitUptime(start + at)
                fire(p)
            }
        }
        val t0 = SystemClock.uptimeMillis()
        height.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
        awaitUptime(t0 + 280)   // falls so the ball lands on the 570 ms impact
        height.animateTo(0f, tween(290, easing = EaseIn))
        height.animateTo(0.18f, tween(140, easing = EaseOut))
        height.animateTo(0f, tween(155, easing = EaseIn))
        busy = false
    }

    // Tour: start on the lift cue only; the impact/bounce cues must not restart (and freeze) it.
    LaunchedEffect(signal.id) {
        if (signal.cue?.scene == Scene.DROP && signal.cue.pattern == OHaptics.dropLift && !busy) scope.launch { drop(haptics = false) }
    }

    StageCanvas(Modifier.pointerInput(Unit) { detectTapGestures { if (!busy) scope.launch { drop(true) } } }) {
        val c = center + Offset(0f, size.height * 0.06f)
        val r = min(size.width, size.height) * 0.26f
        tickRing(c, r * 1.25f, colors, null, 36)
        drawCircle(Porcelain, r * 1.08f, c, style = Stroke(r * 0.22f))
        drawCircle(colors.line, r * 0.94f, c, style = Stroke(2f))
        val h = height.value
        drawCircle(Color.Black.copy(alpha = 0.18f * (1f - h * 0.7f)), r * 0.78f * (1f - h * 0.25f), c + Offset(0f, r * 0.12f))
        sphere(c + Offset(0f, -h * r * 1.2f), r * 0.78f * (1f + h * 0.35f), OneBlue, OneBlueLight)
    }
}

// ---------------------------------------------------------------- roll

@Composable
internal fun RollScene(fire: (List<Step>) -> Unit, signal: CueSignal, colors: PtColors) {
    val x = remember { Animatable(0f) }
    var lastStep by remember { mutableStateOf(0) }
    var atEdge by remember { mutableStateOf(true) }
    var lastT by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    fun travel(to: Float, speed: Float, haptics: Boolean) {
        val step = floor(to / GROOVE).toInt()
        if (step != lastStep) { lastStep = step; if (haptics) fire(OHaptics.roll(speed)) }
        val edge = to <= 0f || to >= 1f
        if (edge && !atEdge && haptics) fire(OHaptics.rollHit)
        atEdge = edge
    }

    suspend fun rollAcross(haptics: Boolean) {
        val target = if (x.value > 0.5f) 0f else 1f
        val from = x.value
        var prev = from
        x.animateTo(target, tween(1100, easing = EaseIn)) {
            val speed = (abs(value - prev) * 20f).coerceIn(0f, 1f)
            prev = value
            travel(value, speed, haptics)
        }
        if (!haptics) { atEdge = true; lastStep = floor(target / GROOVE).toInt() }
    }

    // Tour: start on the first roll cue only; the texture cues every 70 ms must not restart it.
    LaunchedEffect(signal.id) {
        val cue = signal.cue
        if (cue?.scene == Scene.ROLL && cue.atMs == OHaptics.reel.first { it.scene == Scene.ROLL }.atMs) scope.launch { rollAcross(haptics = false) }
    }

    StageCanvas(
        Modifier
            .pointerInput(Unit) { detectTapGestures { scope.launch { rollAcross(true) } } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { scope.launch { x.stop() }; lastT = SystemClock.uptimeMillis() },
                    onDrag = { change, drag ->
                        val track = size.width * TRACK
                        val to = (x.value + drag.x / track).coerceIn(0f, 1f)
                        val now = SystemClock.uptimeMillis()
                        val speed = (abs(drag.x) / (now - lastT).coerceAtLeast(1) / 2f).coerceIn(0f, 1f)
                        lastT = now
                        scope.launch { x.snapTo(to) }
                        travel(to, speed, true)
                        change.consume()
                    },
                )
            },
    ) {
        val track = size.width * TRACK
        val left = (size.width - track) / 2f
        val y = size.height * 0.55f
        val th = 30f.coerceAtMost(size.height * 0.12f)
        drawRoundRect(
            Brush.horizontalGradient(listOf(OneBlueDeep, OneBlue)),
            Offset(left - th / 2, y - th / 2), Size(track + th, th), CornerRadius(th / 2),
        )
        for (i in 1 until (1f / GROOVE).toInt()) {
            val gx = left + track * i * GROOVE
            drawLine(Color.White.copy(alpha = 0.10f), Offset(gx, y - th * 0.3f), Offset(gx, y + th * 0.3f), 2f)
        }
        sphere(Offset(left + track * x.value, y), th * 0.85f, Color(0xFFE6EAF0))
    }
}

private const val GROOVE = 0.07f
private const val TRACK = 0.74f

// ---------------------------------------------------------------- bubbles

private class Bubble(var x: Float, var y: Float, var r: Float, val phase: Float, val mint: Boolean)
private class Ripple(val x: Float, val y: Float, val r: Float, val born: Long)

@Composable
internal fun BubbleScene(fire: (List<Step>) -> Unit, signal: CueSignal, colors: PtColors, still: Boolean) {
    // Plain lists mutated on the frame loop; `frame` (read only while drawing) is the one state
    // write per frame, so the scene redraws without any recomposition or snapshot churn.
    val bubbles = remember { ArrayList<Bubble>().apply { repeat(7) { add(newBubble(spread = true)) } } }
    val ripples = remember { ArrayList<Ripple>() }
    var frame by remember { mutableLongStateOf(0L) }
    var lastSpawn by remember { mutableLongStateOf(0L) }

    LaunchedEffect(still) {
        var prev = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = (now - prev).coerceAtMost(50) / 1000f
            prev = now
            if (!still) bubbles.forEach { b ->
                b.y -= dt * (0.05f + 0.10f * (1f - b.r))
                if (b.y < -0.25f) { b.y = 1.15f + 0.3f * bubbleRandom.nextFloat(); b.x = 0.12f + 0.76f * bubbleRandom.nextFloat() }
            }
            if (ripples.isNotEmpty()) ripples.removeAll { now - it.born > 450 }
            if (bubbles.size < 5 && now - lastSpawn > 450) { bubbles.add(newBubble()); lastSpawn = now }
            frame = now
        }
    }

    fun pop(b: Bubble) {
        bubbles.remove(b)
        ripples.add(Ripple(b.x, b.y, b.r, SystemClock.uptimeMillis()))
    }

    LaunchedEffect(signal.id) {
        val cue = signal.cue ?: return@LaunchedEffect
        if (cue.scene != Scene.BUBBLES || bubbles.isEmpty()) return@LaunchedEffect
        if (cue.pattern == OHaptics.bubbleMerge && bubbles.size >= 2) {
            val big = bubbles.maxBy { it.r }
            val other = bubbles.filter { it !== big }.minBy { hypot(it.x - big.x, it.y - big.y) }
            bubbles.remove(other)
            big.r = (big.r * 1.25f).coerceAtMost(1f)
        } else pop(bubbles.maxBy { it.r })
    }

    StageCanvas(
        Modifier.pointerInput(Unit) {
            detectTapGestures { p ->
                val unit = min(size.width, size.height) * 0.16f
                val hit = bubbles.lastOrNull { b ->
                    hypot(p.x - b.x * size.width, p.y - b.y * size.height) <= unit * (0.4f + b.r)
                }
                if (hit != null) { pop(hit); fire(OHaptics.bubblePop(hit.r)) }
            }
        },
    ) {
        frame // redraw every frame
        val unit = min(size.width, size.height) * 0.16f
        drawRect(Brush.verticalGradient(listOf(Color(0x1AF5E9A8), Color.Transparent)))
        bubbles.forEach { b ->
            val c = Offset(b.x * size.width + sin(frame / 700f + b.phase * 6f) * 6f, b.y * size.height)
            val r = unit * (0.4f + b.r)
            val tint = if (b.mint) Color(0xFF4FD8B4) else Color(0xFFF4D35E)
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.06f), tint.copy(alpha = 0.22f), tint.copy(alpha = 0.62f)), c, r), r, c)
            drawCircle(
                Brush.sweepGradient(listOf(Color(0x99FFFFFF), tint.copy(alpha = 0.7f), Color(0x66B8A4FF), tint.copy(alpha = 0.7f), Color(0x99FFFFFF)), c),
                r, c, style = Stroke(r * 0.05f + 1f),
            )
            drawArc(Color.White.copy(alpha = 0.55f), 200f, 60f, false, c - Offset(r * 0.72f, r * 0.72f), Size(r * 1.44f, r * 1.44f), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            drawCircle(Color.White.copy(alpha = 0.8f), r * 0.10f, c + Offset(-r * 0.42f, -r * 0.46f))
        }
        ripples.forEach { rp ->
            val t = ((frame - rp.born) / 450f).coerceIn(0f, 1f)
            val c = Offset(rp.x * size.width, rp.y * size.height)
            val rr = unit * (0.4f + rp.r)
            drawCircle(Color.White.copy(alpha = 0.5f * (1 - t)), rr * (1 + t * 0.5f), c, style = Stroke(2.5f * (1 - t) + 0.5f))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0 + rp.r * 90)
                val d = rr * (0.9f + t * 0.8f)
                drawCircle(Color.White.copy(alpha = 0.7f * (1 - t)), 2.5f * (1 - t) + 1f, c + Offset(cos(a).toFloat() * d, sin(a).toFloat() * d))
            }
        }
    }
}

private val bubbleRandom = java.util.Random()

/** A bubble at a random column; [spread] scatters it over the stage instead of below it. */
private fun newBubble(spread: Boolean = false): Bubble {
    val rnd = bubbleRandom
    return Bubble(
        x = 0.12f + 0.76f * rnd.nextFloat(),
        y = if (spread) 0.15f + 0.8f * rnd.nextFloat() else 1.1f + 0.3f * rnd.nextFloat(),
        r = 0.15f + 0.6f * rnd.nextFloat(),
        phase = rnd.nextFloat(),
        mint = rnd.nextInt(3) != 0,
    )
}

// ---------------------------------------------------------------- balloons

private class Balloon(val x: Float, val y: Float, val r: Float, val color: Color, val big: Boolean, val phase: Float)
private class Burst(val x: Float, val y: Float, val color: Color, val born: Long, val big: Boolean)

@Composable
internal fun BalloonScene(fire: (List<Step>) -> Unit, signal: CueSignal, colors: PtColors, still: Boolean) {
    val balloons = remember { ArrayList<Balloon>().apply { addAll(balloonSet()) } }   // see BubbleScene
    val bursts = remember { ArrayList<Burst>() }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        coroutineScope {
            while (true) {
                frame = withFrameMillis { it }
                if (bursts.isNotEmpty()) bursts.removeAll { frame - it.born > 520 }
                if (balloons.isEmpty() && bursts.isEmpty()) { awaitUptime(SystemClock.uptimeMillis() + 700); balloons.addAll(balloonSet()) }
            }
        }
    }

    fun pop(b: Balloon) {
        balloons.remove(b)
        bursts.add(Burst(b.x, b.y, b.color, SystemClock.uptimeMillis(), b.big))
    }

    LaunchedEffect(signal.id) {
        val cue = signal.cue ?: return@LaunchedEffect
        if (cue.scene != Scene.BALLOONS || balloons.isEmpty()) return@LaunchedEffect
        val target = if (cue.pattern == OHaptics.balloonBurst) balloons.firstOrNull { it.big } else balloons.firstOrNull { !it.big }
        (target ?: balloons.first()).let(::pop)
    }

    StageCanvas(
        Modifier.pointerInput(Unit) {
            detectTapGestures { p ->
                val unit = min(size.width, size.height)
                val hit = balloons.lastOrNull { b ->
                    hypot(p.x - b.x * size.width, p.y - b.y * size.height) <= unit * b.r * 1.1f
                }
                if (hit != null) { pop(hit); fire(if (hit.big) OHaptics.balloonBurst else OHaptics.balloonPop) }
            }
        },
    ) {
        frame
        val unit = min(size.width, size.height)
        drawRect(Brush.radialGradient(listOf(Color(0x33F7C6B8), Color.Transparent), center, size.maxDimension * 0.6f))
        balloons.forEach { b ->
            val bob = if (still) 0f else sin(frame / 600f + b.phase * 5f) * unit * 0.012f
            val c = Offset(b.x * size.width, b.y * size.height + bob)
            val r = unit * b.r
            drawLine(colors.textFaint, c + Offset(0f, r * 1.1f), c + Offset(r * 0.1f, r * 2.2f), 2f)
            drawOval(Brush.radialGradient(listOf(b.color.copy(alpha = 0.55f).compositeWhite(), b.color), c + Offset(-r * 0.35f, -r * 0.45f), r * 1.6f),
                Offset(c.x - r * 0.9f, c.y - r), Size(r * 1.8f, r * 2.1f))
            drawCircle(b.color, r * 0.12f, c + Offset(0f, r * 1.1f))
        }
        val cs = unit * 0.05f
        translate(center.x, center.y) {
            listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { d ->
                drawLine(colors.textFaint, d * cs * 0.5f, d * cs * 1.4f, 2f)
            }
        }
        bursts.forEach { bu ->
            val t = ((frame - bu.born) / 520f).coerceIn(0f, 1f)
            val c = Offset(bu.x * size.width, bu.y * size.height)
            val n = if (bu.big) 14 else 9
            for (i in 0 until n) rotate(i * 360f / n, c) {
                val d = unit * (0.05f + t * (if (bu.big) 0.28f else 0.16f))
                drawLine(bu.color.copy(alpha = 1f - t), c + Offset(d, 0f), c + Offset(d + unit * 0.035f, 0f), 5f * (1 - t) + 1f, StrokeCap.Round)
            }
        }
    }
}

private fun Color.compositeWhite() = Color(red * 0.5f + 0.5f, green * 0.5f + 0.5f, blue * 0.5f + 0.5f, 1f)

private fun balloonSet() = listOf(
    Balloon(0.25f, 0.40f, 0.10f, Color(0xFF8A5CF0), false, 0.1f),
    Balloon(0.72f, 0.30f, 0.09f, Color(0xFF3F9A76), false, 0.5f),
    Balloon(0.80f, 0.68f, 0.08f, Color(0xFFF08A3C), false, 0.8f),
    Balloon(0.45f, 0.62f, 0.15f, Color(0xFFF2A6B0), true, 0.3f),
)

// ---------------------------------------------------------------- snap (assembly)

@Composable
internal fun SnapScene(fire: (List<Step>) -> Unit, signal: CueSignal, colors: PtColors) {
    val t = remember { Animatable(0f) }   // ms into the assembly
    var built by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun assemble(haptics: Boolean) {
        built = true
        t.snapTo(0f)
        coroutineScope {
            if (haptics) launch {
                val start = SystemClock.uptimeMillis()
                for ((at, p) in OHaptics.assembly) {
                    awaitUptime(start + at)
                    fire(p)
                }
            }
            t.animateTo(SNAP_END, tween(SNAP_END.toInt(), easing = LinearEasing))
        }
    }

    // Tour: start on the first cue only; the following snap cues must not restart (and freeze) it.
    LaunchedEffect(signal.id) {
        if (signal.cue?.scene == Scene.SNAP && signal.cue.atMs == 0) scope.launch { assemble(haptics = false) }
    }

    StageCanvas(
        Modifier.pointerInput(Unit) {
            detectTapGestures {
                scope.launch {
                    if (built && t.value >= SNAP_END) { t.animateTo(0f, tween(350)); built = false } else if (!built) assemble(true)
                }
            }
        },
    ) {
        val c = center
        val r = min(size.width, size.height) * 0.30f
        val now = t.value
        fun arrive(at: Int): Float = FastOutSlowInEasing.transform(((now - (at - 260)) / 260f).coerceIn(0f, 1f))
        val done = now >= SNAP_END
        if (done) tickRing(c, r * 1.30f, colors, -90f)
        // Base ring: four porcelain quarters gliding in from the corners (the first four snaps).
        listOf(0, 85, 245, 340).forEachIndexed { i, at ->
            val k = arrive(at)
            val dir = Offset(cos(Math.toRadians(45.0 + 90 * i)).toFloat(), sin(Math.toRadians(45.0 + 90 * i)).toFloat())
            val off = dir * ((1 - k) * r * 0.55f)
            rotate((1 - k) * 35f * (if (i % 2 == 0) 1 else -1), c + off) {
                drawArc(
                    colors.plate.copy(alpha = 0.35f + 0.65f * k),
                    90f * i + (1 - k) * 4f, 90f - (1 - k) * 8f, false,
                    c + off - Offset(r * 1.14f, r * 1.14f), Size(r * 2.28f, r * 2.28f),
                    style = Stroke(r * 0.20f, cap = if (k >= 1f) StrokeCap.Butt else StrokeCap.Round),
                )
            }
        }
        if (arrive(340) >= 1f) drawCircle(colors.line, r * 1.24f, c, style = Stroke(2f))
        // Collar: two blue halves sliding together (the next pair of snaps).
        listOf(870, 985).forEachIndexed { i, at ->
            val k = arrive(at)
            val off = Offset(if (i == 0) -1f else 1f, 0f) * ((1 - k) * r * 0.5f)
            drawArc(
                OneBlueDeep.copy(alpha = 0.3f + 0.7f * k), 180f * i + 90f, 180f, false,
                c + off - Offset(r * 1.0f, r * 1.0f), Size(r * 2f, r * 2f), style = Stroke(r * 0.09f),
            )
        }
        // The dial drops in (settle thuds at 1935/2010), then its notch clicks round (2825/2895).
        if (now > 1650) {
            val drop = EaseIn.transform(((now - 1650) / 285f).coerceIn(0f, 1f))
            val settle = if (now in 1935f..2200f) sin((now - 1935) / 22f) * r * 0.025f * (1 - (now - 1935) / 265f) else 0f
            val notch = -150f + 60f * FastOutSlowInEasing.transform(((now - 2600) / 295f).coerceIn(0f, 1f))
            blueDial(c + Offset(0f, -(1 - drop) * r * 0.5f + settle), r * (0.86f + 0.14f * (1 - drop)), notch)
        } else if (now == 0f) {
            drawCircle(OneBlue.copy(alpha = 0.18f), r * 0.86f, c)   // ghost of the dial to come
        }
    }
}

private const val SNAP_END = 3000f
