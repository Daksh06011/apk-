import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.BatteryManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyKey
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Duration

fun <T> SemanticsConfiguration.getOrNull(key: SemanticsPropertyKey<T>): T? = if (contains(key)) get(key) else null

/** Sticky ACTION_BATTERY_CHANGED, which is what BatteryRepository polls every 1.5 s. */
fun Context.setBattery(tempC: Double, level: Int = 55, plugged: Int = BatteryManager.BATTERY_PLUGGED_AC) {
    sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).apply {
        putExtra(BatteryManager.EXTRA_TEMPERATURE, Math.round(tempC * 10).toInt())
        putExtra(BatteryManager.EXTRA_LEVEL, level)
        putExtra(BatteryManager.EXTRA_SCALE, 100)
        putExtra(BatteryManager.EXTRA_VOLTAGE, 4190)
        putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
        putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
        putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
        putExtra(BatteryManager.EXTRA_TECHNOLOGY, "Li-ion")
    })
}

/** Runs the paused main looper (frames, animations, the app's polling coroutine) for [ms]. */
fun advance(ms: Long) {
    val looper = shadowOf(Looper.getMainLooper())
    var left = ms
    while (left > 0) {
        val step = minOf(16L, left)
        looper.idleFor(Duration.ofMillis(step))
        left -= step
    }
}

fun Activity.composeRoot(): RootForTest {
    fun find(v: View): RootForTest? = when {
        v is RootForTest -> v
        v is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
        else -> null
    }
    return find(window.decorView) ?: error("No Compose root in ${this::class.simpleName}")
}

fun RootForTest.nodes(): List<SemanticsNode> {
    val out = mutableListOf<SemanticsNode>()
    fun walk(n: SemanticsNode) { out += n; n.children.forEach(::walk) }
    walk(semanticsOwner.unmergedRootSemanticsNode)
    return out
}

fun SemanticsNode.texts(): List<String> = textsOf(this)
private fun textsOf(n: SemanticsNode): List<String> = n.run {
    (config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) +
        (config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList()) }

fun RootForTest.allText(): List<String> = buildList { for (n in nodes()) addAll(textsOf(n)) }

fun RootForTest.nodeWithText(sub: String): SemanticsNode =
    nodes().firstOrNull { n -> n.texts().any { it.contains(sub, ignoreCase = true) } }
        ?: error("No node containing '$sub'. On screen: ${allText()}")

/** Clicks the nearest clickable node at or above the node containing [sub]. */
fun RootForTest.click(sub: String) {
    var n: SemanticsNode? = nodeWithText(sub)
    while (n != null && n.config.getOrNull(SemanticsActions.OnClick) == null) n = n.parent
    val action = n?.config?.getOrNull(SemanticsActions.OnClick)?.action ?: error("'$sub' is not clickable")
    action.invoke()
}

fun Activity.screenshot(name: String): Bitmap {
    val root = window.decorView
    val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bmp))
    System.getProperty("screenshots.dir")?.let { dir ->
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return bmp
}

val SemanticsNode.bounds: Rect get() = boundsInRoot
