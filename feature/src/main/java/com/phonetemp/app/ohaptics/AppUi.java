package com.phonetemp.app.ohaptics;

import androidx.compose.runtime.Composer;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.graphics.Shape;
import com.phonetemp.app.ui.components.HomePartsKt;
import com.phonetemp.app.ui.theme.DepthKt;
import com.phonetemp.app.ui.theme.PtColors;
import com.phonetemp.app.ui.theme.PtTheme;

/**
 * Bridges to the app's top-level Kotlin helpers. The APK carries no META-INF/*.kotlin_module, so
 * Kotlin can't see its top-level functions; Java calls the file-facade classes directly.
 */
public final class AppUi {
    private AppUi() {}

    /**
     * PtTheme.colors is a @Composable getter, compiled to getColors(Composer, int). The APK keeps no
     * CLASS-retention annotations, so Kotlin sees a plain property and would call a getColors() that
     * doesn't exist (NoSuchMethodError at runtime). Pass the composer explicitly instead.
     */
    public static PtColors colors(Composer composer) {
        return PtTheme.INSTANCE.getColors(composer, 6);
    }

    public static Modifier glass(Modifier m, PtColors colors, Shape shape) {
        return DepthKt.glass(m, colors, shape);
    }

    public static Modifier recessed(Modifier m, PtColors colors, Shape shape) {
        return DepthKt.recessed(m, colors, shape);
    }

    public static Modifier raised(Modifier m, PtColors colors, Shape shape) {
        return DepthKt.raised(m, colors, shape);
    }

    /** Pulse Lab's caption text; call from a composable with its current composer. */
    public static void cardCaption(String text, Composer composer) {
        HomePartsKt.CardCaption(text, null, composer, 0, 2);
    }
}
