import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.maven.publish)
}

val publishVersion =
    providers
        .environmentVariable("GITHUB_REF")
        .orNull
        ?.removePrefix("refs/tags/v")
        ?: "1.0.0"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.core.runtime)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

// ── Native build ────────────────────────────────────────────────────────────
// The Rust `nucleus_rodio` crate (rodio + reqwest + hand-written JNI). Native
// binaries ship in src/main/resources/nucleus/native/<os-arch>/ and are
// extracted at runtime by NativeLibraryLoader (from core-runtime).

val buildNativeMacOs by tasks.registering(Exec::class) {
    description = "Compiles the Rust JNI bridge into macOS dylibs (arm64 + x86_64)"
    group = "build"
    val outputDir = file("src/main/resources/nucleus/native")
    val checkFile = File(outputDir, "darwin-aarch64/libnucleus_rodio.dylib")
    onlyIf { Os.isFamily(Os.FAMILY_MAC) && !checkFile.exists() }
    inputs.dir(file("src/main/native/src"))
    inputs.file(file("src/main/native/Cargo.toml"))
    outputs.dir(outputDir)
    workingDir(file("src/main/native/macos"))
    commandLine("bash", "build.sh")
}

val buildNativeWindows by tasks.registering(Exec::class) {
    description = "Compiles the Rust JNI bridge into Windows DLLs (x64 + ARM64)"
    group = "build"
    val outputDir = file("src/main/resources/nucleus/native")
    val checkFile = File(outputDir, "win32-x64/nucleus_rodio.dll")
    onlyIf { Os.isFamily(Os.FAMILY_WINDOWS) && !checkFile.exists() }
    inputs.dir(file("src/main/native/src"))
    inputs.file(file("src/main/native/Cargo.toml"))
    outputs.dir(outputDir)
    workingDir(file("src/main/native/windows"))
    commandLine("cmd", "/c", ".\\build.bat")
}

val buildNativeLinux by tasks.registering(Exec::class) {
    description = "Compiles the Rust JNI bridge into a Linux .so (host arch)"
    group = "build"
    val outputDir = file("src/main/resources/nucleus/native")
    val arch = System.getProperty("os.arch").lowercase()
    val archDir = if (arch.contains("aarch64") || arch.contains("arm64")) "linux-aarch64" else "linux-x64"
    val checkFile = File(outputDir, "$archDir/libnucleus_rodio.so")
    onlyIf { Os.isFamily(Os.FAMILY_UNIX) && !Os.isFamily(Os.FAMILY_MAC) && !checkFile.exists() }
    inputs.dir(file("src/main/native/src"))
    inputs.file(file("src/main/native/Cargo.toml"))
    outputs.dir(outputDir)
    workingDir(file("src/main/native/linux"))
    commandLine("bash", "build.sh")
}

tasks.processResources {
    dependsOn(buildNativeMacOs)
    dependsOn(buildNativeWindows)
    dependsOn(buildNativeLinux)
}

tasks.configureEach {
    if (name == "sourcesJar") {
        dependsOn(buildNativeMacOs)
        dependsOn(buildNativeWindows)
        dependsOn(buildNativeLinux)
    }
}

// ── Maven publication ──────────────────────────────────────────────────────

mavenPublishing {
    coordinates("dev.nucleusframework", "nucleus.rodio", publishVersion)

    pom {
        name.set("Nucleus Rodio")
        description.set("Kotlin audio playback library powered by the Rust rodio crate via hand-written JNI.")
        url.set("https://github.com/NucleusFramework/RodioKt")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("nucleusframework")
                name.set("NucleusFramework")
                url.set("https://github.com/NucleusFramework")
            }
        }

        scm {
            url.set("https://github.com/NucleusFramework/RodioKt")
            connection.set("scm:git:git://github.com/NucleusFramework/RodioKt.git")
            developerConnection.set("scm:git:ssh://git@github.com/NucleusFramework/RodioKt.git")
        }
    }

    publishToMavenCentral()
    if (project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }
}