package com.phonetemp.app.ohaptics

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
    val spin = remember { Animatable(0f) }

    fun moved(to: Float) {
        angle = to
        val idx = floor(to / DETENT).toInt()
        if (idx != detentIdx) { detentIdx = idx; fire(OHaptics.detent) }
    }

    LaunchedEffect(signal.id) {
        if (signal.cue?.scene == Scene.KNOB) {   // reel: one detent per measured tick
            spin.snapTo(angle)
            spin.animateTo(angle + DETENT, tween(60, easing = EaseOut)) { angle = value }
            detentIdx = floor(angle / DETENT).toInt()
        }
    }

    StageCanvas(
        Modifier.pointerInput(Unit) {
            val trail = ArrayDeque<Pair<Long, Float>>()
            var last = 0f
            detectDragGestures(
                onDragStart = { p ->
                    scope.launch { spin.stop() }
                    last = Offset(size.width / 2f, size.height / 2f).angleTo(p)
                    trail.clear()
                },
                onDragEnd = {
                    val now = SystemClock.uptimeMillis()
                    val recent = trail.filter { now - it.first < 120 }
                    val v = if (recent.size >= 2) {
                        val dt = (recent.last().first - recent.first().first).coerceAtLeast(1)
                        (recent.last().second - recent.first().second) / dt * 1000f
                    } else 0f
                    if (abs(v) > 90f) scope.launch {   // flick: coast down, ticks slowing like the video
                        spin.snapTo(angle)
                        spin.animateDecay(v.coerceIn(-1800f, 1800f), exponentialDecay(frictionMultiplier = 1.4f)) { moved(value) }
                    }
                },
                onDrag = { change, _ ->
                    val a = Offset(size.width / 2f, size.height / 2f).angleTo(change.position)
                    var d = a - last
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    last = a
                    moved(angle + d)
                    trail.addLast(SystemClock.uptimeMillis() to angle)
                    if (trail.size > 12) trail.removeFirst()
                },
            )
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
    suspend fun drop(haptics: Boolean) {
        busy = true
        if (haptics) fire(OHaptics.dropLift)
        height.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
        delay(80)
        height.animateTo(0f, tween(290, easing = EaseIn))
        if (haptics) fire(OHaptics.dropImpact)
        height.animateTo(0.18f, tween(140, easing = EaseOut))
        height.animateTo(0f, tween(155, easing = EaseIn))
        if (haptics) fire(OHaptics.dropBounce)
        busy = false
    }

    LaunchedEffect(signal.id) {
        if (signal.cue?.scene == Scene.DROP && signal.cue.pattern == OHaptics.dropLift) drop(haptics = false)
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

    LaunchedEffect(signal.id) {
        val cue = signal.cue
        if (cue?.scene == Scene.ROLL && cue.atMs == OHaptics.reel.first { it.scene == Scene.ROLL }.atMs) rollAcross(haptics = false)
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
    val bubbles = remember { mutableStateListOf<Bubble>().apply { repeat(7) { add(newBubble(it, spread = true)) } } }
    val ripples = remember { mutableStateListOf<Ripple>() }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(still) {
        var prev = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = (now - prev).coerceAtMost(50) / 1000f
            prev = now
            if (!still) bubbles.forEach { b ->
                b.y -= dt * (0.05f + 0.10f * (1f - b.r))
                if (b.y < -0.2f) { b.y = 1.2f; b.x = 0.1f + 0.8f * ((b.phase * 7.3f) % 1f) }
            }
            ripples.removeAll { now - it.born > 450 }
            if (bubbles.size < 4) bubbles.add(newBubble(bubbles.size + ripples.size + now.toInt()))
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
            val tint = if (b.mint) Color(0xFF55D6B0) else Color(0xFFF2D25A)
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.15f), tint.copy(alpha = 0.55f)), c, r), r, c)
            drawCircle(Color.White.copy(alpha = 0.55f), r, c, style = Stroke(1.5f))
            drawCircle(Color.White.copy(alpha = 0.7f), r * 0.18f, c + Offset(-r * 0.4f, -r * 0.4f))
        }
        ripples.forEach { rp ->
            val t = ((frame - rp.born) / 450f).coerceIn(0f, 1f)
            val c = Offset(rp.x * size.width, rp.y * size.height)
            drawCircle(Color.White.copy(alpha = 0.6f * (1 - t)), unit * (0.4f + rp.r) * (1 + t * 0.6f), c, style = Stroke(3f * (1 - t) + 0.5f))
        }
    }
}

private fun newBubble(seed: Int, spread: Boolean = false): Bubble {
    val g = ((seed * 0.61803f) % 1f + 1f) % 1f
    return Bubble(0.12f + 0.76f * g, if (spread) 0.15f + 0.8f * ((seed * 0.37f) % 1f) else 1.15f, 0.15f + 0.6f * ((seed * 0.29f + 0.3f) % 1f), g, seed % 3 != 1)
}

// ---------------------------------------------------------------- balloons

private class Balloon(val x: Float, val y: Float, val r: Float, val color: Color, val big: Boolean, val phase: Float)
private class Burst(val x: Float, val y: Float, val color: Color, val born: Long, val big: Boolean)

@Composable
internal fun BalloonScene(fire: (List<Step>) -> Unit, signal: CueSignal, colors: PtColors, still: Boolean) {
    val balloons = remember { mutableStateListOf<Balloon>().apply { addAll(balloonSet()) } }
    val bursts = remember { mutableStateListOf<Burst>() }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        coroutineScope {
            while (true) {
                frame = withFrameMillis { it }
                bursts.removeAll { frame - it.born > 520 }
                if (balloons.isEmpty() && bursts.isEmpty()) { delay(700); balloons.addAll(balloonSet()) }
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
                    val wait = at - (SystemClock.uptimeMillis() - start)
                    if (wait > 0) delay(wait)
                    fire(p)
                }
            }
            t.animateTo(SNAP_END, tween(SNAP_END.toInt(), easing = LinearEasing))
        }
    }

    LaunchedEffect(signal.id) {
        if (signal.cue?.scene == Scene.SNAP && signal.cue.atMs == 0) assemble(haptics = false)
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
        val r = min(size.width, size.height) * 0.28f
        val now = t.value
        fun arrive(at: Int): Float = FastOutSlowInEasing.transform(((now - (at - 260)) / 260f).coerceIn(0f, 1f))
        // Outer ring: four quarter arcs flying in from the corners (the four first snaps).
        listOf(0, 85, 245, 340).forEachIndexed { i, at ->
            val k = arrive(at)
            val dir = Offset(cos(Math.toRadians(45.0 + 90 * i)).toFloat(), sin(Math.toRadians(45.0 + 90 * i)).toFloat())
            val off = dir * ((1 - k) * r * 1.6f)
            rotate((1 - k) * 70f * (if (i % 2 == 0) 1 else -1), c + off) {
                drawArc(
                    if (k >= 1f) Porcelain else Porcelain.copy(alpha = 0.55f + 0.45f * k),
                    90f * i + 3f, 84f, false, c + off - Offset(r * 1.15f, r * 1.15f), Size(r * 2.3f, r * 2.3f),
                    style = Stroke(r * 0.22f, cap = StrokeCap.Round),
                )
            }
        }
        // Inner collar: two blue halves (the next pair of snaps).
        listOf(870, 985).forEachIndexed { i, at ->
            val k = arrive(at)
            val off = Offset(if (i == 0) -1f else 1f, 0f) * ((1 - k) * r * 1.4f)
            drawArc(OneBlueDeep, 180f * i + 92f, 176f, false, c + off - Offset(r * 0.98f, r * 0.98f), Size(r * 1.96f, r * 1.96f), style = Stroke(r * 0.12f))
        }
        // The dial drops in (settle thuds at 1935/2010) and its notch clicks round (2825/2895).
        val drop = EaseIn.transform(((now - 1650) / 285f).coerceIn(0f, 1f))
        if (now > 1650) {
            val lift = (1 - drop) * r * 0.9f
            val wobble = if (now in 1935f..2150f) sin((now - 1935) / 25f) * r * 0.02f * (1 - (now - 1935) / 215f) else 0f
            val notch = -140f + 50f * FastOutSlowInEasing.transform(((now - 2600) / 295f).coerceIn(0f, 1f))
            blueDial(c + Offset(0f, -lift + wobble), r * (0.82f + 0.18f * (1 - drop)), notch)
        }
        if (now >= SNAP_END) tickRing(c, r * 1.42f, colors, -90f)
    }
}

private const val SNAP_END = 3000f
