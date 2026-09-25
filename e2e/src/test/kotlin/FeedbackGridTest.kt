import com.phonetemp.app.data.haptics.FeedbackId
import com.phonetemp.app.ohaptics.FeedbackGrid
import com.phonetemp.app.ohaptics.HapticGain
import com.phonetemp.app.ohaptics.OHaptics
import com.phonetemp.app.ohaptics.Prim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** The de-duplicated Feedback grid and the stronger Snap/Knob, as shipped. */
class FeedbackGridTest {
    private val ids = FeedbackId.values().map { it.name }

    @Test fun everyCardIsEitherKeptOrReplaced() {
        assertEquals(ids.toSet(), FeedbackGrid.kept + FeedbackGrid.replaced.keys)
        assertTrue((FeedbackGrid.kept intersect FeedbackGrid.replaced.keys).isEmpty())
        assertEquals(7, FeedbackGrid.kept.size)
        assertEquals(12, FeedbackGrid.replaced.size)
    }

    @Test fun noTwoReplacementsFeelAlike() {
        val shapes = FeedbackGrid.replaced.values.map { p -> p.steps.map { Triple(it.prim, it.scale, it.delayMs) } }
        assertEquals("patterns must all differ", shapes.size, shapes.toSet().size)
        val names = FeedbackGrid.replaced.values.map { it.label } + FeedbackId.values().filter { it.name in FeedbackGrid.kept }.map { it.label }
        assertEquals("card names must all differ", names.size, names.toSet().size)
    }

    @Test fun keptCardsKeepTheirSystemNames() {
        FeedbackId.values().filter { it.name in FeedbackGrid.kept }.forEach {
            assertEquals(it.label, FeedbackGrid.label(it)); assertEquals(it.technical, FeedbackGrid.technical(it))
        }
    }

    @Test fun snapAndKnobHitHard() {
        assertEquals(Prim.CLICK, OHaptics.detent.single().prim)
        assertTrue(HapticGain.apply(OHaptics.detent.single().scale) > 0.95f)
        val faintest = OHaptics.snap(0.36f)
        assertTrue(HapticGain.apply(faintest.first().scale) > 0.95f)
        assertEquals(listOf(Prim.CLICK, Prim.TICK), faintest.map { it.prim })
    }

    @Test fun gridIsPatchedToUseIt() {
        ZipFile(File(System.getProperty("apk.path"))).use { z ->
            val dex5 = String(z.getInputStream(z.getEntry("classes5.dex")).readBytes(), Charsets.ISO_8859_1)
            assertTrue(dex5.contains("Lcom/phonetemp/app/ohaptics/FeedbackGrid;"))
            assertTrue(dex5.contains("One of each system feel Android offers"))
        }
    }
}
