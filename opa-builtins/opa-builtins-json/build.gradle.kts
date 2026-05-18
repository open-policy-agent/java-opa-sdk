plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation("com.github.java-json-tools:json-patch:1.13")
    implementation("com.networknt:json-schema-validator:1.5.5")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // RegoValueModule (auto-registered via Jackson SPI) provides (de)serializers for the AST
    // types so they don't need to carry annotations.
    runtimeOnly(project(":opa-jackson"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}
