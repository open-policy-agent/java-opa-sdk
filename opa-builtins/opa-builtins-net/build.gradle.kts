plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":opa-evaluator"))

    implementation("com.github.seancfoley:ipaddress:5.6.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}
