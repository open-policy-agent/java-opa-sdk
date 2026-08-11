plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation("dk.brics.automaton:automaton:1.11-8")

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
