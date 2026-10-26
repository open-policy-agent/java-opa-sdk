plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation("com.nimbusds:nimbus-jose-jwt:10.10")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
    implementation(platform("tools.jackson:jackson-bom:3.2.4"))
    implementation("tools.jackson.core:jackson-databind")
    // RegoValueModule provides Jackson (de)serialization for RegoObject/RegoArray/etc.
    // Discovered automatically via Jackson's findAndRegisterModules() SPI.
    runtimeOnly(project(":opa-jackson"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
