import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

subprojects {
    afterEvaluate {
        if (plugins.hasPlugin("java") || plugins.hasPlugin("java-library")) {
            apply(plugin = "checkstyle")
            apply(plugin = "pmd")

            configure<CheckstyleExtension> {
                toolVersion = "10.21.4"
                configFile = rootProject.file("config/checkstyle/checkstyle.xml")
                isIgnoreFailures = false
            }

            configure<PmdExtension> {
                toolVersion = "7.14.0"
                isConsoleOutput = true
                isIgnoreFailures = false
                ruleSets = emptyList()
                ruleSetFiles = rootProject.files("config/pmd/ruleset.xml")
            }

            apply(plugin = "com.vanniktech.maven.publish")

            configure<MavenPublishBaseExtension> {
                publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
                signAllPublications()
                coordinates(
                    rootProject.property("group") as String,
                    project.name,
                    rootProject.property("version") as String
                )
                pom {
                    name.set(project.name)
                    description.set("Java SDK for Open Policy Agent")
                    url.set("https://github.com/open-policy-agent/java-opa-sdk")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                    developers {
                        developer {
                            id.set("sspaink")
                            name.set("Sebastian Spaink")
                        }
                    }
                    scm {
                        url.set("https://github.com/open-policy-agent/java-opa-sdk")
                        connection.set("scm:git:git://github.com/open-policy-agent/java-opa-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com/open-policy-agent/java-opa-sdk.git")
                    }
                    issueManagement {
                        system.set("GitHub")
                        url.set("https://github.com/open-policy-agent/java-opa-sdk/issues")
                    }
                }
            }
        }
    }
}