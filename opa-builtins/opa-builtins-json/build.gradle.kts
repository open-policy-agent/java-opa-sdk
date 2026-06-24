plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation("com.networknt:json-schema-validator:3.0.5")
    implementation(platform("tools.jackson:jackson-bom:3.2.0"))
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    // RegoValueModule (auto-registered via Jackson SPI) provides (de)serializers for the AST
    // types so they don't need to carry annotations.
    runtimeOnly(project(":opa-jackson"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
