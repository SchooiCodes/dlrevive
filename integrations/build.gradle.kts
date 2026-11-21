import java.util.Properties

plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Android framework classes are provided by the target APK at runtime.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val androidSdkDir = localProperties.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: error("Android SDK not found. Set sdk.dir in local.properties or ANDROID_HOME.")

val androidPlatform = File(androidSdkDir, "platforms").listFiles()
    ?.filter { it.isDirectory && File(it, "android.jar").exists() }
    ?.maxByOrNull { it.name }
    ?: error("No Android platform with android.jar found under $androidSdkDir/platforms")
val androidJar = File(androidPlatform, "android.jar")

val buildToolsDir = File(androidSdkDir, "build-tools").listFiles()
    ?.filter { it.isDirectory && File(it, "d8.bat").exists() || it.isDirectory && File(it, "d8").exists() }
    ?.maxByOrNull { it.name }
    ?: error("No Android build-tools with d8 found under $androidSdkDir/build-tools")
val d8 = File(
    buildToolsDir,
    if (System.getProperty("os.name").lowercase().contains("windows")) "d8.bat" else "d8",
)

dependencies {
    compileOnly(files(androidJar))
    implementation(libs.newpipe.extractor)
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(files(androidJar))
}

/**
 * A fat JAR of the integrations code plus all runtime dependencies. This is what gets
 * dexed and merged into YouTube Music by the ReVanced patch.
 */
val fatJar = tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().map { entry ->
            if (entry.isDirectory) entry else zipTree(entry)
        }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**")
}

/** Convert the fat JAR into an Android DEX that the patch can merge into the APK. */
val dexIntegrations = tasks.register<Exec>("dexIntegrations") {
    dependsOn(fatJar)
    val outputDir = layout.buildDirectory.dir("dex").get().asFile
    inputs.file(fatJar.flatMap { it.archiveFile })
    outputs.dir(outputDir)
    doFirst { outputDir.mkdirs() }
    commandLine(
        d8.absolutePath,
        "--lib", androidJar.absolutePath,
        "--min-api", "26",
        "--output", outputDir.absolutePath,
        fatJar.get().archiveFile.get().asFile.absolutePath,
    )
}

tasks.named("build") {
    dependsOn(dexIntegrations)
}
