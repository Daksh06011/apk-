import android.view.View;
import androidx.activity.ComponentActivity;
import androidx.activity.compose.ComponentActivityKt;
import com.phonetemp.app.ohaptics.PulseExtrasKt;
import com.phonetemp.app.ui.theme.ThemeKt;
import kotlin.Unit;

/** Hosts one of the rebuilt Pulse Lab examples in the app's theme (see StudioHost for why). */
public final class ExampleHost {
    private ExampleHost() {}

    public static void riseFall(ComponentActivity activity) {
        View view = activity.getWindow().getDecorView();
        ComponentActivityKt.setContent(activity, null, (composer, changed) -> {
            ThemeKt.PhoneTempTheme(null, (c, ch) -> { PulseExtrasKt.RiseFallExample(view, false, c, 0); return Unit.INSTANCE; }, composer, 0, 1);
            return Unit.INSTANCE;
        });
    }

    public static void dragThreshold(ComponentActivity activity) {
        View view = activity.getWindow().getDecorView();
        ComponentActivityKt.setContent(activity, null, (composer, changed) -> {
            ThemeKt.PhoneTempTheme(null, (c, ch) -> { PulseExtrasKt.DragThresholdPad(view, c, 0); return Unit.INSTANCE; }, composer, 0, 1);
            return Unit.INSTANCE;
        });
    }
}
