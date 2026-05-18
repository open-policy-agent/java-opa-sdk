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
    // modules through SPIs:
    //   - PolicyReader (io.github.open_policy_agent.opa.ir)
    //   - BundleParser (io.github.open_policy_agent.opa.bundle)
    //   - AnnotationIntrospector (io.github.open_policy_agent.opa.mapper)
    // The opa-jackson module supplies a Jackson-backed implementation of all three.

    testImplementation(project(":opa-jackson"))
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation("org.skyscreamer:jsonassert:1.5.3")
    testImplementation("org.mockito:mockito-core:5.16.1")
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