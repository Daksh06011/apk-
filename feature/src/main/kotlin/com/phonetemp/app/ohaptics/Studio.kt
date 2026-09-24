package com.phonetemp.app.ohaptics

import android.os.SystemClock
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonetemp.app.ui.theme.PtColors
import com.phonetemp.app.ui.theme.PtType
import com.phonetemp.app.ui.theme.Radius
import com.phonetemp.app.ui.theme.Space
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** One tour cue as delivered to the scenes, stamped with when it was sent. */
class CueSignal(val cue: OHaptics.Cue, val sentAt: Long) {
    /** Replayed cues (a scene composed just after its first cue) count only while still fresh. */
    val fresh: Boolean get() = SystemClock.uptimeMillis() - sentAt < 250
}

/**
 * Tour cues reach the scenes as events, not as composition state: a cue starts or advances a
 * scene's animation and nothing recomposes. (As state, every cue - every 70 ms during Roll - made
 * the whole card recompose, and the dropped frames showed as stutter in the tour only.) Replay of
 * one covers a scene that composes a frame after its first cue.
 */
typealias Cues = SharedFlow<CueSignal>

/** Runs [handle] for each fresh cue addressed to [scene], for as long as the scene is on stage. */
@Composable
internal fun OnCue(cues: Cues, scene: Scene, handle: (OHaptics.Cue) -> Unit) {
    val current by rememberUpdatedState(handle)
    LaunchedEffect(cues) {
        cues.collect { sig -> if (sig.cue.scene == scene && sig.fresh) current(sig.cue) }
    }
}

/**
 * Tacta haptic studio: the Pulse Lab card with six tactile scenes and a timed tour. Called from
 * PulseLabScreen's examples column (patched in by build.sh) with the screen's View.
 */
@Composable
fun OHapticsStudio(view: View, still: Boolean) {
    val colors = AppUi.colors(currentComposer)
    val player = remember(view) { HapticPlayer(view) }
    var scene by remember { mutableStateOf(Scene.KNOB) }
    val readout = remember { mutableStateOf("Touch the stage") }   // read only by HapticReadout
    var reelRun by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    val progress = remember { mutableFloatStateOf(0f) }   // read only while drawing the bar
    val cues = remember { MutableSharedFlow<CueSignal>(replay = 1, extraBufferCapacity = 64) }
    val playingNow by rememberUpdatedState(playing)

    // Scene gestures fire through here; while the reel runs, the reel alone drives the motor.
    val fire: (List<Step>) -> Unit = remember(player) {
        { pattern -> if (!playingNow) { player.play(pattern); readout.value = describe(pattern) } }
    }

    LaunchedEffect(reelRun) {
        if (reelRun == 0) return@LaunchedEffect
        playing = true
        val start = SystemClock.uptimeMillis()
        val switches = OHaptics.reelScenes.toMutableList()
        for (cue in OHaptics.reel) {
            awaitUptime(start + cue.atMs)
            while (switches.isNotEmpty() && switches.first().first <= cue.atMs) scene = switches.removeAt(0).second
            player.play(cue.pattern)
            readout.value = describe(cue.pattern)
            cues.tryEmit(CueSignal(cue, SystemClock.uptimeMillis()))
        }
        awaitUptime(start + OHaptics.REEL_MS)
        playing = false
    }
    LaunchedEffect(playing) {
        if (!playing) { progress.floatValue = 0f; return@LaunchedEffect }
        val start = withFrameMillis { it }
        while (playing) {
            val now = withFrameMillis { it }
            progress.floatValue = ((now - start).toFloat() / OHaptics.REEL_MS).coerceIn(0f, 1f)
        }
    }

    val cardShape = RoundedCornerShape(Radius.md)
    Column(
        AppUi.glass(Modifier.fillMaxWidth(), colors, cardShape).padding(Space.l),
    ) {
        Text("TACTA // HAPTIC STUDIO", color = colors.textTertiary, style = PtType.labelMono)
        Spacer(Modifier.height(Space.xs))
        Text("Touch that talks back", color = colors.textPrimary, style = PtType.body)
        AppUi.cardCaption("Six tactile scenes and a 20-second tour, tuned hit by hit.", currentComposer)
        Spacer(Modifier.height(Space.m))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Scene.entries.forEach { s ->
                SceneChip(s.label, s == scene, colors) { if (!playing) scene = s }
            }
        }
        Spacer(Modifier.height(Space.m))

        val stageShape = RoundedCornerShape(Radius.md)
        Box(
            AppUi.recessed(Modifier.fillMaxWidth().height(260.dp), colors, stageShape)
                .clip(stageShape)
                .semantics { contentDescription = "Tacta stage: ${scene.label}" },
        ) {
            Crossfade(targetState = scene, animationSpec = tween(260), label = "scene") { s ->
                when (s) {
                    Scene.SNAP -> SnapScene(fire, cues, colors)
                    Scene.KNOB -> KnobScene(fire, cues, colors)
                    Scene.DROP -> DropScene(fire, cues, colors)
                    Scene.ROLL -> RollScene(fire, cues, colors)
                    Scene.BUBBLES -> BubbleScene(fire, cues, colors, still)
                    Scene.BALLOONS -> BalloonScene(fire, cues, colors, still)
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        Text(scene.hint, color = colors.textSecondary, style = PtType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Space.m))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ReelButton(playing, colors) {
                if (playing) { reelRun = 0; playing = false } else reelRun++
            }
            Spacer(Modifier.width(Space.m))
            HapticReadout(readout, colors, Modifier.weight(1f))
        }
        // The tour bar always keeps its slot (and only redraws, never recomposes the card).
        Spacer(Modifier.height(Space.s))
        val barAlpha by animateFloatAsState(if (playing) 1f else 0f, tween(200), label = "bar")
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            if (barAlpha == 0f) return@Canvas
            drawRoundRect(colors.track.copy(alpha = barAlpha), cornerRadius = CornerRadius(size.height))
            drawRoundRect(
                OneBlue.copy(alpha = barAlpha), size = Size(size.width * progress.floatValue, size.height),
                cornerRadius = CornerRadius(size.height),
            )
        }
    }
}

@Composable
private fun SceneChip(label: String, selected: Boolean, colors: PtColors, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(animateColorAsState(if (selected) OneBlue else colors.plate, tween(180), label = "chip").value)
            .semantics { this.selected = selected; role = Role.Tab }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = animateColorAsState(if (selected) Color.White else colors.textSecondary, tween(180), label = "chipText").value,
            style = PtType.caption,
        )
    }
}

@Composable
private fun ReelButton(playing: Boolean, colors: PtColors, onClick: () -> Unit) {
    Box(
        AppUi.raised(Modifier, colors, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(if (playing) colors.plate else OneBlue)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            if (playing) "■  Stop tour" else "▶  Play Tacta Tour",
            color = if (playing) colors.textPrimary else Color.White,
            style = PtType.caption,
        )
    }
}

/** Its own recompose scope: a new readout (every fired pattern) redraws just this text. */
@Composable
private fun HapticReadout(text: State<String>, colors: PtColors, modifier: Modifier) {
    val t = text.value
    Text(
        t,
        color = colors.textTertiary,
        style = PtType.mono,
        modifier = modifier.semantics { contentDescription = "Haptic readout: $t" },
        // Fixed two-line slot: a readout that wraps must never change the card's height.
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun describe(pattern: List<Step>): String =
    pattern.joinToString("  ") { s ->
        val d = if (s.delayMs > 0) "+${s.delayMs}ms " else ""
        "$d${s.prim.name} ${"%.2f".format(s.scale)}"
    }

@Composable
internal fun StageCanvas(modifier: Modifier, onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) =
    Canvas(modifier.fillMaxSize(), onDraw)

internal val OneBlue = Color(0xFF2F7FEA)
internal val OneBlueLight = Color(0xFF7DB8FF)
internal val OneBlueDeep = Color(0xFF1B55B8)
internal val Porcelain = Color(0xFFF1F4F8)

internal fun Offset.angleTo(p: Offset): Float =
    Math.toDegrees(kotlin.math.atan2((p.y - y).toDouble(), (p.x - x).toDouble())).toFloat()
