plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))
    implementation("org.slf4j:slf4j-api:2.0.18")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
    testImplementation("org.assertj:assertj-core:3.27.7")
    // Log4j 2 as the SLF4J backend for tests
    testImplementation("org.apache.logging.log4j:log4j-core:2.26.0")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.test {
    useJUnitPlatform()
}