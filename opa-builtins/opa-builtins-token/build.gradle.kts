plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation("com.nimbusds:nimbus-jose-jwt:10.5")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.82")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    // RegoValueModule provides Jackson (de)serialization for RegoObject/RegoArray/etc.
    // Discovered automatically via Jackson's findAndRegisterModules() SPI.
    runtimeOnly(project(":opa-jackson"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}
