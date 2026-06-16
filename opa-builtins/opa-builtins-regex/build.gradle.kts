plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation("dk.brics.automaton:automaton:1.11-8")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
