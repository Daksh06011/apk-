// End-to-end tests for the patched PhoneTemp APK.
//
// There is no app source, so the APK itself is the test subject: its dex files are converted to
// JVM bytecode with dex2jar and run under Robolectric (real Android framework + native graphics),
// launching MainActivity, feeding it battery broadcasts and rendering real frames.
plugins { kotlin("jvm") }

val apkPath = providers.gradleProperty("apk").orElse("../dist/PhoneTemp-1.0.1-debug.apk")
val apk = file(apkPath.get())
val originalApk = file("../original/PhoneTemp-1.0.0-debug.apk")

extra["apkFile"] = apk
apply(from = rootProject.file("gradle/apk-classes.gradle.kts"))
val appJar = tasks.named("appJar")
val appClasses = files(layout.buildDirectory.file("app/app.jar"))

// androidx.test (monitor + espresso-idling-resource) is only published on Google Maven. Where that
// host is unreachable, pass -PandroidxTestSrc=<android/android-test checkout> to build it from source.
val androidxTestSrc = providers.gradleProperty("androidxTestSrc").orNull?.let(::file)
val androidxTestDirs = listOf("runner/monitor/java", "espresso/idling_resource/java",
    "espresso/idling_resource/concurrent/java", "espresso/idling_resource/net/java")
val hiddenApi = "androidx/test/internal/runner/hidden/ExposedInstrumentationApi"

// Stage the sources the way upstream's Bazel build does: compile against the "hidden" API stub,
// then ship the runtime variant of that class (below). The app's older androidx.tracing lacks
// Trace.forceEnableAppTracing(), so that call is dropped (tracing is irrelevant here).
val stageAndroidxTest by tasks.registering(Sync::class) {
    androidxTestSrc?.let { src -> androidxTestDirs.forEach { from(src.resolve(it)) } }
    exclude("androidx/test/internal/runner/runtime/**")
    filesMatching("**/AndroidXTracer.java") {
        filter { it.replace("import static androidx.tracing.Trace.forceEnableAppTracing;", "")
                   .replace("forceEnableAppTracing();", "") }
    }
    into(layout.buildDirectory.dir("androidx-test-src"))
}
val androidxTest: SourceSet? = androidxTestSrc?.let {
    sourceSets.create("androidxTest") {
        java.setSrcDirs(listOf(stageAndroidxTest))
        kotlin.setSrcDirs(listOf(stageAndroidxTest))
    }
}
val androidxTestRuntimeApi by tasks.registering(JavaCompile::class) {
    source(androidxTestSrc?.resolve("runner/monitor/java/androidx/test/internal/runner/runtime") ?: files())
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("androidx-test-runtime-api"))
    options.compilerArgs.add("-proc:none")
}
if (androidxTest != null) {
    androidxTestRuntimeApi.configure { classpath = androidxTest.compileClasspath; dependsOn(appJar) }
}

dependencies {
    testCompileOnly(files(layout.buildDirectory.file("app/app-api.jar")))
    testCompileOnly(files(configurations["composeCompile"]))
    testRuntimeOnly(appClasses)
    testImplementation("org.robolectric:robolectric:4.14.1") {
        if (androidxTest != null) { exclude(group = "androidx.test"); exclude(group = "androidx.test.espresso") }
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:android-all:14-robolectric-10818077") // plays the role of android.jar
    if (androidxTest != null) {
        "androidxTestCompileOnly"("org.robolectric:android-all:14-robolectric-10818077")
        "androidxTestCompileOnly"(appClasses) // androidx.annotation, androidx.tracing
        "androidxTestCompileOnly"("com.google.errorprone:error_prone_annotations:2.23.0")
        testImplementation(files(androidxTestRuntimeApi)) // must precede androidxTest.output
        testImplementation(androidxTest.output)
    }
}

kotlin { jvmToolchain(21) }

// Robolectric binary-resources config: real resources.arsc from the APK + decoded manifest.
val robolectricConfig by tasks.registering {
    val out = layout.buildDirectory.dir("robolectric-config")
    outputs.dir(out)
    inputs.file(apk)
    doLast {
        val f = out.get().asFile.resolve("com/android/tools/test_config.properties")
        f.parentFile.mkdirs()
        f.writeText(
            "android_merged_manifest=${file("AndroidManifest.xml").absolutePath}\n" +
            "android_resource_apk=${apk.absolutePath}\n" +
            "android_custom_package=com.phonetemp.app\n"
        )
    }
}

sourceSets.test { resources.srcDir(robolectricConfig) }
tasks.compileTestKotlin { dependsOn("appApiJar") }
tasks.matching { it.name == "compileAndroidxTestKotlin" || it.name == "compileAndroidxTestJava" }.configureEach { dependsOn(appJar) }

tasks.test {
    dependsOn(appJar)
    systemProperty("apk.path", apk.absolutePath)
    systemProperty("apk.original", originalApk.absolutePath)
    systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    systemProperty("robolectric.graphicsMode", "NATIVE")
    // Robolectric fetches android-all jars itself; point it at the same Maven Central mirror.
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
    maxHeapSize = "4g"
    // dex2jar emits Java 8 class files without StackMapTable frames; the JVM verifier (unlike ART's,
    // which the APK integrity tests cover at the dex level) would reject them.
    jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:-BytecodeVerificationRemote", "-XX:-BytecodeVerificationLocal")
    testLogging { events("passed", "failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; showStandardStreams = true }
}
