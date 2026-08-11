plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-builtins:opa-builtins-time"))
    api(project(":opa-builtins:opa-builtins-token"))
    api(project(":opa-builtins:opa-builtins-regex"))
    api(project(":opa-builtins:opa-builtins-semver"))
    api(project(":opa-builtins:opa-builtins-net"))
    api(project(":opa-builtins:opa-builtins-crypto"))
    api(project(":opa-builtins:opa-builtins-json"))
    api(project(":opa-builtins:opa-builtins-providers-aws"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
