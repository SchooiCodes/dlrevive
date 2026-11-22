import java.net.URL

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
    // The Patcher 22 classes inside CLI 6.0.0 were built with a Kotlin pre-release compiler.
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

// ReVanced Manager 2.6.0 loads patch bundles built with Patcher 22. The matching patcher
// classes are bundled inside ReVanced CLI 6.0.0 (the standalone Patcher 22 artifact is not
// published to Maven Central). The jar is gitignored; fetch it once when missing.
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
    implementation(
        files("../integrations/build/libs/integrations-all.jar")
            .builtBy(":integrations:fatJar"),
    )
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

tasks.withType<Jar>().configureEach {
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
