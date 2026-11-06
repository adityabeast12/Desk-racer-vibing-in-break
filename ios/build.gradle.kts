// iOS framework for the Swift app shell. All game code lives in :shared; only Entry.kt is exported
// to Objective-C/Swift, which keeps the generated header tiny (the Android API shims have
// members like `int`/`float` that are not valid Objective-C names).
plugins {
    kotlin("multiplatform") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { t ->
        t.binaries.framework {
            baseName = "DeskRacerKit"
            isStatic = false
        }
    }
    sourceSets {
        iosMain.dependencies {
            implementation(project(":shared"))
        }
    }
}
