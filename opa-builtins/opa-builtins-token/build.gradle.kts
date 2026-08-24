plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation("com.nimbusds:nimbus-jose-jwt:10.9.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    // RegoValueModule provides Jackson (de)serialization for RegoObject/RegoArray/etc.
    // Discovered automatically via Jackson's findAndRegisterModules() SPI.
    runtimeOnly(project(":opa-jackson"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
