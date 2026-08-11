plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation("com.github.java-json-tools:json-patch:1.13")
    implementation("com.networknt:json-schema-validator:2.0.4")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.1"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // RegoValueModule (auto-registered via Jackson SPI) provides (de)serializers for the AST
    // types so they don't need to carry annotations.
    runtimeOnly(project(":opa-jackson"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.test {
    useJUnitPlatform()
}
