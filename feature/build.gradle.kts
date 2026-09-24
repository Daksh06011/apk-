// O-Haptics Studio: new Pulse Lab feature, compiled against the original APK's classes and shipped
// as an extra dex (classes10.dex) that build.sh adds to the patched APK.
plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
}

extra["apkFile"] = rootProject.file("original/PhoneTemp-1.0.0-debug.apk")
apply(from = rootProject.file("gradle/apk-classes.gradle.kts"))

val dx by configurations.creating
dependencies {
    compileOnly(files(layout.buildDirectory.file("app/app-api.jar")))  // app + non-Compose androidx
    compileOnly(files(configurations["composeCompile"]))                    // Compose 1.7 + coroutines APIs
    compileOnly("org.robolectric:android-all:14-robolectric-10818077") // android.jar
    dx("com.jakewharton.android.repackaged:dalvik-dx:16.0.1")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        // dx predates invokedynamic lambdas; keep everything as plain classes.
        freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class", "-Xstring-concat=inline")
    }
}
tasks.compileJava { options.release.set(8) }
tasks.compileKotlin { dependsOn("appApiJar") }

// Only this module's own classes are dexed (tasks.jar); the stdlib is already inside the APK.
tasks.register<JavaExec>("dex") {
    val jar = tasks.jar
    dependsOn(jar)
    val out = layout.buildDirectory.file("dex/classes10.dex")
    inputs.files(jar)
    outputs.file(out)
    classpath = dx
    mainClass.set("com.android.dx.command.Main")
    doFirst { out.get().asFile.parentFile.mkdirs() }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--dex", "--min-sdk-version=26", "--output=${out.get().asFile}", jar.get().archiveFile.get().asFile.path)
    })
}
