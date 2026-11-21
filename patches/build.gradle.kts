plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.revanced.patcher)
    // revanced-patcher's compile variant omits smali, but its public API exposes dexlib2 types.
    implementation("com.android.tools.smali:smali:3.0.3")
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
    archiveBaseName.set("dlrevive-patches")
}
