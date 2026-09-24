import com.phonetemp.app.ohaptics.OHaptics
import com.phonetemp.app.ohaptics.Prim
import com.phonetemp.app.ohaptics.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tour script and the scene patterns as shipped (read from the APK's own classes). */
class TourDataTest {
    @Test fun tourIsOrderedAndFitsItsLength() {
        val times = OHaptics.reel.map { it.atMs }
        assertEquals(times.sorted(), times)
        assertTrue(times.last() < OHaptics.REEL_MS)
        // snap 8 + knob 15 + drop 3 + roll 16 texture + 1 hit + bubbles 7 + balloons 4
        assertEquals(54, OHaptics.reel.size)
    }

    @Test fun everySceneAppearsBeforeItsCues() {
        OHaptics.reel.forEach { cue ->
            val showing = OHaptics.reelScenes.last { it.first <= cue.atMs }.second
            assertEquals("cue @${cue.atMs}", cue.scene, showing)
        }
    }

    @Test fun knobTrainSlowsDown() {
        val ticks = OHaptics.reel.filter { it.scene == Scene.KNOB }.map { it.atMs }
        val gaps = ticks.zipWithNext { a, b -> b - a }
        assertEquals(15, ticks.size)
        assertTrue("$gaps", gaps.first() <= 85 && gaps.last() >= 300)
    }

    @Test fun popsAreSharpAndBubblesAreSoft() {
        assertEquals(listOf(Prim.CLICK, Prim.QUICK_FALL), OHaptics.balloonPop.map { it.prim })
        assertEquals(listOf(Prim.CLICK, Prim.THUD, Prim.LOW_TICK, Prim.LOW_TICK, Prim.LOW_TICK), OHaptics.balloonBurst.map { it.prim })
        assertTrue(OHaptics.balloonBurst.zipWithNext().drop(1).all { (a, b) -> b.scale < a.scale })   // tail fades
        val small = OHaptics.bubblePop(0.1f); val big = OHaptics.bubblePop(0.9f)
        assertEquals(listOf(Prim.LOW_TICK, Prim.QUICK_FALL), small.map { it.prim })
        assertTrue("bigger bubbles pop stronger", big[0].scale > small[0].scale)
    }
}
