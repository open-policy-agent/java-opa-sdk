import com.google.protobuf.gradle.id
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    `java-library`
    id("com.google.protobuf") version "0.10.0"
}

repositories {
    mavenCentral()
}

// protobuf-java 4.x is required: the vendored schemas use edition 2023, which
// needs protoc >= 27 (the 4.27+ line) and a matching runtime.
val protobufVersion = "4.35.1"

dependencies {
    api(project(":opa-evaluator"))
    api("com.google.protobuf:protobuf-java:$protobufVersion")

    testImplementation(project(":opa-jackson"))
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}

// Re-vendors the OPA .proto schemas from the OPA version pinned in
// tools/generate-compliance-tests/go.mod (the source of truth). Wired ahead of
// protobuf codegen and keyed on go.mod/go.sum, so simply building the module
// re-vendors automatically whenever the pinned OPA version changes — and stays
// UP-TO-DATE (no network) when it hasn't. Requires the Go toolchain; when Go is
// unavailable the task is skipped and the committed schemas are used as-is.
//
// Implemented natively (via the injected ExecOperations service) rather than by
// shelling out, so it is OS-agnostic and configuration-cache compatible.
abstract class VendorProtoSchemas @Inject constructor(private val exec: ExecOperations) :
    DefaultTask() {

    @get:InputFile
    abstract val goMod: RegularFileProperty

    @get:InputFile
    abstract val goSum: RegularFileProperty

    // Absolute path to the resolved go/go.exe, or empty when Go is not installed.
    @get:Input
    abstract val goExecutable: Property<String>

    // The protobuf source root the schemas are copied into. Not declared as an @OutputDirectory
    // because it doubles as protobuf codegen input; the stamp below carries up-to-date state.
    @get:Internal
    abstract val protoRoot: DirectoryProperty

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun vendor() {
        val goDir = goMod.get().asFile.parentFile

        val version = capture("list", "-m", "-f", "{{.Version}}", MODULE).trim()
        exec.exec {
            commandLine(go(), "mod", "download", MODULE)
            workingDir = goDir
        }
        val moduleCache = capture("env", "GOMODCACHE").trim()

        val src = File(moduleCache, "github.com/open-policy-agent/opa@$version")
        val root = protoRoot.get().asFile
        for (rel in listOf("v1/ir/plan.proto", "v1/bundle/manifest.proto")) {
            val dest = File(root, rel)
            File(src, rel).copyTo(dest, overwrite = true)
            dest.setWritable(true) // module cache files are read-only
            logger.lifecycle("vendored $rel from opa@$version")
        }
        stamp.get().asFile.writeText("re-vendored github.com/open-policy-agent/opa@$version\n")
    }

    private fun go() = goExecutable.get()

    private fun capture(vararg args: String): String {
        val out = ByteArrayOutputStream()
        exec.exec {
            commandLine(listOf(go()) + args)
            workingDir = goMod.get().asFile.parentFile
            standardOutput = out
        }
        return out.toString()
    }

    companion object {
        const val MODULE = "github.com/open-policy-agent/opa"
    }
}

// Resolve the Go executable at configuration time (OS-aware), or "" when absent.
val resolvedGoExecutable =
    providers.environmentVariable("PATH").zip(providers.systemProperty("os.name")) { path, os ->
        val names = if (os.lowercase().contains("win")) listOf("go.exe", "go") else listOf("go")
        path.split(File.pathSeparator)
            .asSequence()
            .flatMap { dir -> names.asSequence().map { File(dir, it) } }
            .firstOrNull { it.canExecute() }
            ?.absolutePath
            ?: ""
    }

val vendorProtoSchemas by tasks.registering(VendorProtoSchemas::class) {
    description = "Re-vendor OPA .proto schemas from the version pinned in go.mod"
    group = "build"

    goMod.set(rootProject.layout.projectDirectory.file("tools/generate-compliance-tests/go.mod"))
    goSum.set(rootProject.layout.projectDirectory.file("tools/generate-compliance-tests/go.sum"))
    goExecutable.set(resolvedGoExecutable)
    protoRoot.set(layout.projectDirectory.dir("src/main/proto"))
    stamp.set(layout.buildDirectory.file("vendor-protos.stamp"))

    // Skip (leaving the committed schemas as-is) when Go isn't installed, so pure-Java builds work.
    onlyIf { goExecutable.get().isNotEmpty() }
}

tasks.named("generateProto") {
    dependsOn(vendorProtoSchemas)
}

// Generated protobuf sources live under opa.ir.v1 / opa.bundle.v1 (from the
// vendored schemas). They are machine-generated, so exempt them from the code
// quality gates applied to hand-written sources.
tasks.withType<Checkstyle>().configureEach {
    exclude("**/opa/ir/v1/**", "**/opa/bundle/v1/**")
}
tasks.withType<Pmd>().configureEach {
    exclude("**/opa/ir/v1/**", "**/opa/bundle/v1/**")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
