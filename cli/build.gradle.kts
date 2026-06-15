plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":opa-evaluator"))
    implementation(project(":opa-services"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.apache.commons:commons-compress:1.28.0")

    runtimeOnly(project(":opa-builtins"))
    runtimeOnly(project(":opa-jackson"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    mainClass = "io.github.open_policy_agent.opa.cli.Regoj"
    applicationName = "regoj"
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}
