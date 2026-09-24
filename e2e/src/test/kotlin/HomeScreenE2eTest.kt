import android.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.phonetemp.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import kotlin.math.cos
import kotlin.math.sin

/** Launches the real app at a range of battery temperatures and checks what it draws. */
@RunWith(ApkTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HomeScreenE2eTest {
    private fun launch(tempC: Double): MainActivity {
        RuntimeEnvironment.getApplication().setBattery(tempC)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        advance(4000)   // first battery poll + the ring's sweep animation
        return activity
    }

    @Test fun launchesAndShowsTheLiveReading() {
        val a = launch(38.5)
        val root = a.composeRoot()
        val ring = root.nodeWithText("Phone temperature 38.5")
        assertTrue(ring.texts().single().contains("Normal"))
        assertTrue(root.allText().contains("NORMAL"))
        a.screenshot("home-38.5C")
    }

    @Test fun everyThermalStateRenders() {
        mapOf(24.0 to "Cool", 38.5 to "Normal", 42.0 to "Getting warm", 45.0 to "Hot", 49.0 to "Critical").forEach { (t, label) ->
            val a = launch(t)
            val ring = a.composeRoot().nodeWithText("Phone temperature ${"%.1f".format(t)}")
            assertTrue("$t °C -> ${ring.texts()}", ring.texts().single().contains(label))
            a.screenshot("home-${t}C")
        }
    }

    /** The fill's leading edge takes the colour of the temperature: blue when cool, red-orange when hot. */
    @Test fun ringTipColourFollowsTemperature() {
        val hues = listOf(24.0, 38.5, 45.0, 49.0).associateWith { t -> tipHue(launch(t), t) }
        println("ring tip hues: $hues")
        assertTrue("24 °C tip should be blue: ${hues[24.0]}", hues.getValue(24.0) in 180f..235f)
        assertTrue("38.5 °C tip should be green: ${hues[38.5]}", hues.getValue(38.5) in 60f..130f)
        assertTrue("45 °C tip should be orange: ${hues[45.0]}", hues.getValue(45.0) in 5f..45f)
        assertTrue("49 °C tip should be red: ${hues[49.0]}", hues.getValue(49.0) < 15f || hues.getValue(49.0) > 340f)
    }

    private fun tipHue(a: MainActivity, t: Double): Float {
        val ring = a.composeRoot().nodeWithText("Phone temperature").boundsInWindow
        val density = a.resources.displayMetrics.density
        val radius = ring.width / 2 - (9 + 6.5f) * density        // GradientRing: 9 dp inset + half the 13 dp stroke
        val sweep = ((t - 20) / 30 * 300).coerceIn(8.0, 300.0)
        val angle = Math.toRadians(120 + sweep - 4)                // just inside the round cap
        val bmp = a.screenshot("ring-tip-${t}C")
        val px = bmp.getPixel((ring.center.x + radius * cos(angle)).toInt(), (ring.center.y + radius * sin(angle)).toInt())
        val hsv = FloatArray(3).also { Color.colorToHSV(px, it) }
        assertTrue("tip pixel should be saturated, got #${Integer.toHexString(px)}", hsv[1] > 0.35f)
        return hsv[0]
    }

    @Test fun typographyUsesTheSystemFont() {
        val type = Class.forName("com.phonetemp.app.ui.theme.TypeKt")
        assertSame(FontFamily.Default, type.getMethod("getDisplay").invoke(null))
        assertSame(FontFamily.Default, type.getMethod("getMono").invoke(null))
    }
}
