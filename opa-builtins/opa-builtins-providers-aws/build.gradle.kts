plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
