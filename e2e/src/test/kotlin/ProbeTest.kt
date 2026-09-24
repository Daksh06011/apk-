import org.robolectric.RuntimeEnvironment
import com.phonetemp.app.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ProbeTest {
    @Test fun probe() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.setBattery(38.5)
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        advance(4000)
        val root = a.composeRoot()
        root.nodes().forEach { println("NODE ${it.bounds} ${it.texts()} click=${it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) != null}") }
        a.screenshot("probe")
    }
}
