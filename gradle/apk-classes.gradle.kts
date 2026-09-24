// Converts an APK's dex files into one jar of JVM classes (app + androidx + kotlinx), so the app's
// code can be compiled against (feature/) and run under Robolectric (e2e/).
// Set extra["apkFile"] before applying. Produces:
//   appJar    -> build/app/app.jar      everything, for running
//   appApiJar -> build/app/app-api.jar  minus androidx.compose/kotlinx.coroutines, for compiling
//   configuration `composeCompile`       what to compile Compose/coroutines calls against instead
//
// Why compile against JetBrains' desktop build of the same Compose 1.7 sources: the APK lacks
// META-INF/*.kotlin_module, so Kotlin can't see top-level functions (Column, remember, Offset(x, y),
// launch...), and its dex2jar'd inline bodies aren't fit for inlining. At runtime the app's own
// Compose 1.7.5 runs. Gradle puts file dependencies first, hence the stripped app-api.jar.
//
// Two dex2jar 2.4.38 defects are fixed on the way (tools/dex2jar-fix):
//  - IR2JConverter: `if-gt 0, vX` (zero as FIRST operand) was emitted as `ifgt vX`, inverting
//    loops such as Compose's MutableVector.contains. Patched copy of upstream (commit ecdd1b5).
//  - FixInterfaceCalls: dex call sites don't say whether an invoke-static/-super owner is an
//    interface; this ASM pass emits InterfaceMethodref where the JVM requires it.
val apk = extra["apkFile"] as File

val dex2jar = configurations.create("dex2jar")
val androidAll = configurations.create("androidAllLookup")
val composeCompile = configurations.create("composeCompile") { isTransitive = false }
dependencies {
    listOf(
        "runtime:runtime", "runtime:runtime-saveable", "ui:ui", "ui:ui-graphics", "ui:ui-geometry",
        "ui:ui-unit", "ui:ui-text", "ui:ui-util", "foundation:foundation", "foundation:foundation-layout",
        "animation:animation", "animation:animation-core", "material3:material3",
    ).forEach { add("composeCompile", "org.jetbrains.compose.${it.substringBefore(':')}:${it.substringAfter(':')}-desktop:1.7.1") }
    // Same version as the APK bundles (META-INF/kotlinx_coroutines_core.version).
    add("composeCompile", "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")
    add("dex2jar", "de.femtopedia.dex2jar:dex-tools:2.4.38")
    add("androidAllLookup", "org.robolectric:android-all:14-robolectric-10818077") { isTransitive = false }
}

val dex2jarFix = tasks.register<JavaCompile>("dex2jarFix") {
    source(rootProject.fileTree("tools/dex2jar-fix"))
    classpath = dex2jar
    destinationDirectory.set(layout.buildDirectory.dir("dex2jar-fix"))
    options.release.set(11)
    options.compilerArgs.add("-nowarn")
}

val extractDex = tasks.register<Sync>("extractDex") {
    from(zipTree(apk)) { include("classes*.dex", "META-INF/services/**") }
    into(layout.buildDirectory.dir("app/unzipped"))
}

tasks.register("appJar") {
    dependsOn(extractDex, dex2jarFix)
    inputs.file(apk)
    inputs.files(dex2jarFix, androidAll)
    val out = layout.buildDirectory.file("app/app.jar")
    outputs.file(out)
    doLast {
        val dir = layout.buildDirectory.dir("app").get().asFile
        val jars = dir.resolve("jars").apply { deleteRecursively(); mkdirs() }
        dir.resolve("unzipped").listFiles { f -> f.name.endsWith(".dex") }!!.sorted().forEach { dex ->
            project.javaexec {
                classpath = files(dex2jarFix) + dex2jar
                mainClass.set("com.googlecode.dex2jar.tools.Dex2jarCmd")
                args("-f", "-n", "--dont-sanitize-names", "-o", jars.resolve(dex.name + ".jar").path, dex.path)
            }
        }
        val raw = dir.resolve("app-raw.jar")
        ant.withGroovyBuilder {
            "jar"("destfile" to raw.path) {
                jars.listFiles()!!.forEach { "zipfileset"("src" to it.path) {
                    "exclude"("name" to "META-INF/MANIFEST.MF")
                    // The app's bundled Kotlin stdlib: the real kotlin-stdlib jar is used instead (the
                    // dex2jar'd inline-function bodies crash the Kotlin compiler when inlined).
                    "exclude"("name" to "kotlin/**")
                } }
                "fileset"("dir" to dir.resolve("unzipped").path, "includes" to "META-INF/services/**")
            }
        }
        project.javaexec {
            classpath = files(dex2jarFix) + dex2jar
            mainClass.set("FixInterfaceCalls")
            args(raw.path, out.get().asFile.path, *androidAll.files.map { it.path }.toTypedArray())
        }
    }
}

tasks.register<Jar>("appApiJar") {
    dependsOn("appJar")
    from(zipTree(layout.buildDirectory.file("app/app.jar"))) { exclude("androidx/compose/**", "kotlinx/coroutines/**") }
    archiveFileName.set("app-api.jar")
    destinationDirectory.set(layout.buildDirectory.dir("app"))
}
