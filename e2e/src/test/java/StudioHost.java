import android.view.View;
import androidx.activity.ComponentActivity;
import androidx.activity.compose.ComponentActivityKt;
import com.phonetemp.app.ohaptics.StudioKt;
import com.phonetemp.app.ui.theme.ThemeKt;
import kotlin.Unit;

/**
 * Shows O-Haptics Studio on its own, inside the app's PhoneTempTheme, in a plain ComponentActivity.
 *
 * Why not navigate to Pulse Lab: under this JVM harness, Navigation 2.8's NavHost never finishes
 * its screen transition (the new entry stays STARTED), for the original APK as much as the patched
 * one; the Pulse Lab call site is checked statically in ApkIntegrityTest instead.
 */
public final class StudioHost {
    private StudioHost() {}

    public static void show(ComponentActivity activity) {
        View view = activity.getWindow().getDecorView();
        ComponentActivityKt.setContent(activity, null, (composer, changed) -> {
            // PhoneTempTheme(prefs = default, content): bit 0 of the defaults mask = prefs omitted
            ThemeKt.PhoneTempTheme(null, (c, ch) -> {
                StudioKt.OHapticsStudio(view, false, c, 0);
                return Unit.INSTANCE;
            }, composer, 0, 1);
            return Unit.INSTANCE;
        });
    }
}
