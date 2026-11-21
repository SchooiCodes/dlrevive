plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.revanced.patcher)
    implementation(
        files("../integrations/build/libs/integrations-all.jar")
            .builtBy(":integrations:fatJar"),
    )
    testImplementation(libs.junit)
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("dlrevive-patches")
}
