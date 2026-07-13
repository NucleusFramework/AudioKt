import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.nucleus)
}

kotlin {
    jvmToolchain(17)

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":rodio"))
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.nucleus.application)
            // Tao windowing backend: nucleusApplication picks it up automatically
            // (Tao wins over AWT when present) and renders custom chrome.
            implementation(libs.decorated.window.tao)
            // Material 2 decorated window + title bar (derives chrome from MaterialTheme.colors).
            implementation(libs.decorated.window.material2)
            // Native Open/Save dialogs.
            implementation(libs.filekit.dialogs)
        }
    }
}

// GraalVM native image is driven entirely by the Nucleus application DSL: the
// plugin analyses the classpath, merges rodio's shipped reachability metadata
// (JNI callbacks + RodioException) and bundles the native libraries itself.
nucleus.application {
    mainClass = "MainKt"

    graalvm {
        isEnabled = true
        imageName = "sample"
    }

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        packageName = "sample"
        packageVersion = "1.0.0"
    }
}
