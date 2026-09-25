import android.os.SystemClock
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.phonetemp.app.ohaptics.HapticLog
import com.phonetemp.app.ohaptics.Prim
import com.phonetemp.app.ohaptics.PulseExtras
import com.phonetemp.app.ohaptics.Step
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** The rebuilt "Rise and fall" and "Drag threshold" examples, driven by touch. */
@RunWith(ApkTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PulseExtrasE2eTest {
    private val fired = mutableListOf<List<Step>>()
    private lateinit var activity: ComponentActivity

    @Before fun setUp() {
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java)).setSupportedPrimitives((1..8).toList())
        HapticLog.listener = { p, _ -> fired += p }
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    }

    @After fun tearDown() { HapticLog.listener = null }

    private fun area(desc: String): Rect { advance(500); fired.clear(); return activity.composeRoot().nodeWithText(desc).boundsInWindow }
    private fun line(from: Offset, to: Offset, n: Int = 40) = (0..n).map { i -> Offset(from.x + (to.x - from.x) * i / n, from.y + (to.y - from.y) * i / n) }
    private fun prims() = fired.map { it.first().prim }

    @Test fun movingUpRisesStrongerAsItClimbs() {
        ExampleHost.riseFall(activity)
        val pad = area("Rise and fall pad")
        activity.drag(line(Offset(pad.center.x, pad.bottom - 30f), Offset(pad.center.x, pad.top + 60f)), stepMs = 20, release = false)
        activity.screenshot("rise-fall-top")
        val rises = fired.filter { it.first().prim == Prim.QUICK_RISE }.map { it.first().scale }
        println("rise scales: $rises")
        assertTrue("rising pulses: $rises", rises.size >= 4)
        assertTrue("stronger as it climbs: $rises", rises.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(Prim.QUICK_FALL !in prims())
    }

    @Test fun movingDownFallsAndSidewaysTicks() {
        ExampleHost.riseFall(activity)
        val pad = area("Rise and fall pad")
        activity.drag(line(Offset(pad.center.x, pad.top + 60f), Offset(pad.center.x, pad.bottom - 60f)), stepMs = 20)
        val falls = fired.filter { it.first().prim == Prim.QUICK_FALL }.map { it.first().scale }
        println("fall scales: $falls")
        assertTrue("falling pulses: $falls", falls.size >= 4)
        assertTrue("fading as it drops: $falls", falls.zipWithNext().all { (a, b) -> b <= a })
        fired.clear()
        activity.drag(line(Offset(pad.left + 80f, pad.center.y), Offset(pad.right - 80f, pad.center.y)), stepMs = 20)
        assertTrue("sideways ticks: ${prims()}", prims().count { it == Prim.TICK } >= 4)
        assertTrue(prims().none { it == Prim.QUICK_RISE || it == Prim.QUICK_FALL })
    }

    @Test fun hittingTheTopBumps() {
        ExampleHost.riseFall(activity)
        val pad = area("Rise and fall pad")
        activity.drag(line(Offset(pad.center.x, pad.center.y), Offset(pad.center.x, pad.top + 2f), 20), stepMs = 20)
        assertTrue("top bump: $fired", fired.contains(PulseExtras.topBump))
    }

    @Test fun playRisesThenFalls() {
        ExampleHost.riseFall(activity)
        area("Rise and fall pad")
        activity.composeRoot().click("Play rise and fall")
        advance(300)
        activity.screenshot("rise-fall-play-rising")
        advance(1500)
        assertEquals(listOf(PulseExtras.riseAndFall), fired)
    }

    @Test fun crossingTheLineLatchesAndReleasingLocks() {
        ExampleHost.dragThreshold(activity)
        val track = area("Drag threshold track")
        val knobX = track.left + track.height / 2f
        activity.drag(line(Offset(knobX, track.center.y), Offset(track.right - track.height / 2f, track.center.y), 50), stepMs = 20)
        advance(400)
        activity.screenshot("threshold-activated")
        val kinds = prims()
        println("threshold: $kinds")
        val latchAt = fired.indexOf(PulseExtras.latch)
        assertTrue("latch fired: $kinds", latchAt > 0)
        val tension = fired.take(latchAt).map { it.single().scale }
        assertTrue("tension builds toward the line: $tension", tension.size >= 3 && tension.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(PulseExtras.lockIn, fired.last())
        assertTrue(activity.composeRoot().allText().any { it.contains("Activated") })
    }

    @Test fun backingOffUnlatchesAndSpringsHome() {
        ExampleHost.dragThreshold(activity)
        val track = area("Drag threshold track")
        val knobX = track.left + track.height / 2f
        val past = track.left + track.width * 0.85f
        activity.drag(line(Offset(knobX, track.center.y), Offset(past, track.center.y), 30) + line(Offset(past, track.center.y), Offset(knobX + 20f, track.center.y), 30), stepMs = 20)
        advance(800)
        assertTrue(fired.contains(PulseExtras.latch))
        assertTrue(fired.contains(PulseExtras.unlatch))
        assertTrue(fired.indexOf(PulseExtras.unlatch) > fired.indexOf(PulseExtras.latch))
        assertEquals(PulseExtras.springHome, fired.last())
    }
}
