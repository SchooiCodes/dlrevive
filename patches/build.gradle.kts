import java.net.URL
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
    // Patcher 22 classes in CLI 6.0.0 were built with a Kotlin pre-release compiler.
    compilerOptions {
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

repositories {
    flatDir {
        dirs(rootProject.layout.buildDirectory.dir("cli"))
    }
}

val cliJar = rootProject.layout.buildDirectory.file("cli/revanced-cli-6.0.0-all.jar")

// ReVanced Manager 2.6.0 loads patch bundles with Patcher 22, which is bundled inside ReVanced
// CLI 6.0.0 (the standalone Patcher 22 artifact is not published). The jar is gitignored; fetch
// it once when missing.
val downloadCli by tasks.registering {
    outputs.file(cliJar)
    doLast {
        val target = cliJar.get().asFile
        if (!target.exists()) {
            target.parentFile.mkdirs()
            val url = "https://github.com/ReVanced/revanced-cli/releases/download/v6.0.0/revanced-cli-6.0.0-all.jar"
            try {
                URL(url).openStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                throw GradleException(
                    "Could not download $url. Place revanced-cli-6.0.0-all.jar in build/cli manually. " +
                        "Cause: ${e.message}",
                    e,
                )
            }
        }
    }
}

dependencies {
    compileOnly(files(cliJar).builtBy(downloadCli))
    implementation(files("../integrations/build/libs/integrations-all.jar").builtBy(":integrations:fatJar"))
    testImplementation(libs.junit)
}

val generatedResources = layout.buildDirectory.dir("generated/resources")

tasks.register<Copy>("copyIntegrationsDex") {
    dependsOn(":integrations:dexIntegrations")
    from("../integrations/build/dex/classes.dex") {
        rename { "dlrevive-classes.dex" }
    }
    into(generatedResources)
}

sourceSets.main {
    resources.srcDir(generatedResources)
}

tasks.named("processResources") {
    dependsOn("copyIntegrationsDex")
}

// ReVanced Manager on Android reads patches from a classes.dex DEX container inside the bundle
// JAR. JVM .class files alone produce EmptyMultiDexContainerException.
val sdkRoot: String? =
    System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
            Properties().apply { file.inputStream().use { load(it) } }.getProperty("sdk.dir")
        }

fun findAndroidJar(): File {
    val root = sdkRoot?.let { File(it) }
        ?: throw GradleException("Android SDK not found; set ANDROID_HOME or sdk.dir in local.properties")
    return root.resolve("platforms")
        .listFiles { dir -> dir.isDirectory && dir.name.startsWith("android-") }
        ?.maxByOrNull { it.name.removePrefix("android-").toIntOrNull() ?: 0 }
        ?.resolve("android.jar")
        ?.takeIf { it.exists() }
        ?: throw GradleException("No android.jar found under ${root.resolve("platforms")}")
}

fun findD8(): File {
    val root = sdkRoot?.let { File(it) }
        ?: throw GradleException("Android SDK not found; set ANDROID_HOME or sdk.dir in local.properties")
    return root.resolve("build-tools")
        .listFiles { dir -> dir.isDirectory }
        ?.sortedByDescending { it.name }
        ?.firstNotNullOfOrNull { dir ->
            listOf("d8.bat", "d8").map { dir.resolve(it) }.firstOrNull { it.exists() }
        }
        ?: throw GradleException("d8 not found under ${root.resolve("build-tools")}")
}

val patchClassesJar = tasks.register<Jar>("patchClassesJar") {
    archiveFileName.set("patch-classes.jar")
    from(sourceSets.main.get().output.classesDirs)
    destinationDirectory.set(layout.buildDirectory.dir("tmp"))
}

val dexPatches by tasks.registering(Exec::class) {
    dependsOn(patchClassesJar, downloadCli)
    val outputDir = layout.buildDirectory.dir("patch-dex")
    outputs.dir(outputDir)
    commandLine(
        findD8().absolutePath,
        "--lib", findAndroidJar().absolutePath,
        "--classpath", cliJar.get().asFile.absolutePath,
        "--min-api", "26",
        "--output", outputDir.get().asFile.absolutePath,
        "${layout.buildDirectory.get().asFile.absolutePath}/tmp/patch-classes.jar",
    )
}

tasks.named<Jar>("jar") {
    dependsOn(dexPatches)
    from(layout.buildDirectory.file("patch-dex/classes.dex"))
    archiveBaseName.set("dlvrevive-patches-manager")
    // ReVanced Manager's PatchBundle loader reads these main attributes.
    manifest.attributes(
        mapOf(
            "Name" to "dlrevive patches",
            "Version" to "1.0.0",
            "Description" to "Restore YouTube Music downloads without Premium via in-app audio resolution.",
            "Source" to "https://github.com/Schooi/dlrevive",
            "Author" to "dlrevive",
            "Contact" to "https://github.com/Schooi/dlrevive/issues",
            "Website" to "https://github.com/Schooi/dlrevive",
            "License" to "MIT",
        ),
    )
}
