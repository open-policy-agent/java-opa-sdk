plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation("org.semver4j:semver4j:5.4.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
