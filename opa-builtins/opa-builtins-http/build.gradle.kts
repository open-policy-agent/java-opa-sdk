plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    // Response bodies are decoded with json.unmarshal / yaml.unmarshal so http.send agrees with
    // those builtins on number handling and parse errors.
    implementation(project(":opa-builtins:opa-builtins-json"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.test {
    useJUnitPlatform()
}
