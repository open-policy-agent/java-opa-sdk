plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":opa-evaluator"))
    implementation(project(":opa-services"))
    implementation(project(":opa-jackson"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.apache.commons:commons-compress:1.28.0")

    runtimeOnly(project(":opa-builtins"))
    runtimeOnly(project(":opa-proto"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
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
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

// Regenerates the checked-in capabilities.json describing the builtins this SDK implements.
// The file is consumed by downstream tooling (e.g. OPA's builtin docs, which report which
// implementations support each builtin), and CI fails if it drifts from the registered builtins.
tasks.register<JavaExec>("generateCapabilities") {
    group = "build"
    description = "Regenerates capabilities.json from the currently registered builtins."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass = application.mainClass
    args("eval", "--capabilities-current")

    val outputFile = rootProject.layout.projectDirectory.file("capabilities.json").asFile
    outputs.file(outputFile)

    doFirst {
        standardOutput = outputFile.outputStream().buffered()
    }
    doLast {
        standardOutput.close()
    }
}

// Snapshots the current capabilities.json into capabilities/<version>.json, keeping one immutable
// record per release the way OPA's own capabilities/ directory does. Downstream tooling reads the
// directory to report the release a builtin became available in, so this runs as part of preparing
// a release, once the version in gradle.properties has been bumped. Published snapshots are never
// regenerated: they describe what that release exposed, not what the current tree does.
tasks.register<Copy>("snapshotCapabilities") {
    group = "build"
    description = "Copies capabilities.json to capabilities/<version>.json for a release."

    dependsOn("generateCapabilities")

    val version = rootProject.property("version") as String
    val snapshot = rootProject.layout.projectDirectory.file("capabilities/${version}.json").asFile

    // A published snapshot describes what that release exposed. Overwriting it would rewrite
    // history, which is easy to do by accident because the version stays at the released value
    // until the next release bumps it.
    doFirst {
        if (snapshot.exists()) {
            throw GradleException(
                "capabilities/${version}.json already exists and published snapshots are " +
                    "immutable. Bump the version in gradle.properties before snapshotting."
            )
        }
    }

    from(rootProject.layout.projectDirectory.file("capabilities.json"))
    into(rootProject.layout.projectDirectory.dir("capabilities"))
    rename { "${version}.json" }
}
