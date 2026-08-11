plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.1"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.apache.commons:commons-compress:1.28.0")
    // opa-jackson provides the PolicyReader/BundleParser SPI implementations and the
    // RegoValueModule. Test code in this module bridges JsonNode <-> RegoObject via that module.
    runtimeOnly(project(":opa-jackson"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation(project(":opa-jackson"))
    // opa-proto provides the ProtoBundleDecoder SPI used to read proto-format bundles.
    testImplementation(project(":opa-proto"))
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}