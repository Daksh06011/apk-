import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.internal.bytecode.InstrumentationConfiguration

/**
 * Robolectric instruments (rewrites) every class it loads from instrumented packages, androidx
 * included. On the dex2jar'd app code, its rewritten constructors can trip HotSpot's oop-map builder
 * ("Illegal class file ... in method <init>"). The app and Compose need no framework shadows, so
 * they're loaded as-is.
 */
class ApkTestRunner(klass: Class<*>) : RobolectricTestRunner(klass) {
    override fun createClassLoaderConfig(method: FrameworkMethod): InstrumentationConfiguration =
        InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
            .doNotInstrumentPackage("com.phonetemp.app")
            .doNotInstrumentPackage("androidx.compose")
            .build()
}
