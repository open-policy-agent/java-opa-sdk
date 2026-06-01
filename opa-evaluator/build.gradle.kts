plugins {
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // The evaluator has no direct dependency on a JSON library. JSON IO is provided by external
    // modules through SPIs (see Engine javadoc); opa-jackson is one such implementation, used here
    // for testing.
    testImplementation(project(":opa-jackson"))
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")
    testImplementation("org.json:json:20260522")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.skyscreamer:jsonassert:1.5.3")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation(project(":opa-builtins"))
}

tasks.test {
    useJUnitPlatform()
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}