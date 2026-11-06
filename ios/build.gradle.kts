import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

// iOS build of Desk Racer LAN. Compiles the SAME Kotlin sources as the Android app (game sim,
// netcode, renderer, Compose UI) against a small Android/Java compatibility layer (src/iosMain),
// so both platforms race with identical rules and can play each other over LAN.
plugins {
    kotlin("multiplatform") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.8.2"
}

val appSrc = rootDir.resolve("../app/src/main/java")
val appRes = rootDir.resolve("../app/src/main/res")
val genDir = layout.buildDirectory.dir("generated/drshim/kotlin")

// Android files whose job is platform glue; iOS has its own versions (named *Ios.kt) in src/iosMain.
val androidOnly = listOf(
    "com/example/MainActivity.kt",
    "com/example/render/MsaaConfigChooser.kt",
    "com/example/game/HardwareInput.kt",
    "com/example/net/NetUtils.kt",
    "com/example/ui/PhotoSaver.kt",
    "com/example/ui/controls/ButtonHaptics.kt",
)

val jvmCompatTemplate = """
@file:Suppress("unused", "NOTHING_TO_INLINE", "FunctionName")

package PKG

import kotlinx.coroutines.IO as coroutinesIO

/** Dispatchers.IO is a member on the JVM but an import-only extension on Kotlin/Native. */
internal val kotlinx.coroutines.Dispatchers.IO: kotlinx.coroutines.CoroutineDispatcher
    get() = kotlinx.coroutines.Dispatchers.coroutinesIO

internal typealias System = drshim.JSystem
internal typealias Math = drshim.JMath
internal typealias Thread = drshim.JThread
internal typealias Runnable = drshim.JRunnable
internal typealias InterruptedException = drshim.InterruptedExceptionShim
internal typealias OutOfMemoryError = drshim.OutOfMemoryErrorShim
internal typealias Volatile = kotlin.concurrent.Volatile
internal typealias Synchronized = drshim.SynchronizedNoop
internal typealias Charsets = drshim.JCharsets

internal inline fun <R> synchronized(lock: Any, block: () -> R): R = drshim.globalSynchronized(block)
internal fun String.format(vararg args: Any?): String = drshim.jformat(this, args)
internal fun String.format(locale: java.util.Locale?, vararg args: Any?): String = drshim.jformat(this, args)
internal fun String.Companion.format(format: String, vararg args: Any?): String = drshim.jformat(format, args)
internal fun String.Companion.format(locale: java.util.Locale?, format: String, vararg args: Any?): String = drshim.jformat(format, args)
internal fun String(bytes: ByteArray, charset: java.nio.charset.Charset): String = drshim.decodeBytes(bytes, 0, bytes.size, charset)
internal fun String(bytes: ByteArray, offset: Int, length: Int, charset: java.nio.charset.Charset): String = drshim.decodeBytes(bytes, offset, length, charset)
internal fun String(bytes: ByteArray): String = bytes.decodeToString()
internal fun String.toByteArray(charset: java.nio.charset.Charset): ByteArray = drshim.encodeString(this, charset)
internal fun String.toByteArray(): ByteArray = encodeToByteArray()
"""

val generateAndroidR by tasks.registering {
    val stringsXml = appRes.resolve("values/strings.xml")
    val fontDir = appRes.resolve("font")
    inputs.file(stringsXml)
    inputs.dir(fontDir)
    outputs.dir(genDir)
    doLast {
        val out = genDir.get().asFile.resolve("com/example")
        out.mkdirs()
        val strings = Regex("""<string\s+name="([a-zA-Z0-9_]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(stringsXml.readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val fonts = fontDir.listFiles()!!.filter { it.extension == "ttf" || it.extension == "otf" }.sortedBy { it.name }
        fun unescape(s: String) = s.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        fun kq(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("$", "\\$") + "\""
        val sb = StringBuilder()
        sb.append("// Generated from app/src/main/res by :generateAndroidR. Do not edit.\n")
        sb.append("package com.example\n\n")
        sb.append("object R {\n    object string {\n")
        strings.forEachIndexed { i, (n, _) -> sb.append("        const val $n = ${0x7f100000 + i}\n") }
        sb.append("    }\n    object font {\n")
        fonts.forEachIndexed { i, f -> sb.append("        const val ${f.nameWithoutExtension} = ${0x7f090000 + i}\n") }
        sb.append("    }\n}\n\n")
        sb.append("internal object RTables {\n    val strings: Map<Int, String> = mapOf(\n")
        strings.forEachIndexed { i, (_, v) -> sb.append("        ${0x7f100000 + i} to ${kq(unescape(v.trim()))},\n") }
        sb.append("    )\n    val fonts: Map<Int, String> = mapOf(\n")
        fonts.forEachIndexed { i, f -> sb.append("        ${0x7f090000 + i} to ${kq(f.name)},\n") }
        sb.append("    )\n}\n\n")
        sb.append("fun drshimInstallResources() {\n    drshim.Res.strings = RTables.strings\n    drshim.Res.fonts = RTables.fonts\n}\n")
        out.resolve("R.kt").writeText(sb.toString())

        // Per-package JVM idioms (System, Math, Thread, @Volatile, synchronized, String.format ...) that
        // Kotlin/JVM resolves through java.lang / kotlin.jvm default imports.
        val pkgs = appSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .mapNotNull { f -> f.useLines { ls -> ls.firstOrNull { it.startsWith("package ") }?.removePrefix("package ")?.trim() } }
            .toSortedSet()
        val compatDir = genDir.get().asFile.resolve("pkgcompat")
        compatDir.deleteRecursively()
        compatDir.mkdirs()
        for (p in pkgs) {
            compatDir.resolve(p.replace('.', '_') + "_JvmCompat.kt").writeText(jvmCompatTemplate.replace("PKG", p))
        }
    }
}

kotlin {
    val iosTargets = listOf(iosArm64(), iosSimulatorArm64())
    iosTargets.forEach { t ->
        t.binaries.framework {
            baseName = "DeskRacerKit"
            isStatic = false
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlinx.cinterop.BetaInteropApi")
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("kotlin.native.runtime.NativeRuntimeApi")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.foundation.ExperimentalFoundationApi")
                optIn("androidx.compose.foundation.layout.ExperimentalLayoutApi")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            }
        }
        iosMain {
            kotlin.srcDir(appSrc)
            kotlin.srcDir(genDir)
            kotlin.exclude(androidOnly)
            kotlin.exclude("**/*.java")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateAndroidR)
    compilerOptions {
        freeCompilerArgs.addAll("-nowarn", "-Xexpect-actual-classes")
    }
}
