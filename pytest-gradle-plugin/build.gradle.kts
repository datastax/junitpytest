/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.datastax.junitpytest.gradle.tasks.WriteVersionProperties
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.publish.maven.MavenPom
import java.time.Duration

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    signing
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("com.gradle.plugin-publish") version "0.12.0"
    id("de.marcphilipp.nexus-publish") version "0.4.0"
}

description = "DataStax junitpytest - plugin for Gradle"
val readableName = "DataStax junitpytest - plugin for Gradle"

repositories {
    mavenCentral()
}

java {
    @Suppress("UnstableApiUsage")
    withJavadocJar()
    @Suppress("UnstableApiUsage")
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val shadowed: Configuration by configurations.creating
configurations.named("compileOnly") {
    extendsFrom(shadowed, configurations.getByName("shadow"))
}
configurations.named("testImplementation") {
    extendsFrom(shadowed, configurations.getByName("shadow"))
}

dependencies {
    add("shadowed", project(":common"))
    add("shadowed", project(":junit-pytest-plugin"))
    add("shadowed", "org.ow2.asm:asm:8.0.1")
}

sourceSets {
    main {
        resources.srcDir(buildDir.resolve("generated/sources/version"))
    }
}

val configurePom = Action<MavenPom> {
    name.set(readableName)
    description.set(project.description)
    inceptionYear.set("2020")
    url.set("https://github.com/datastax/junitpytest/")
    licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
    developers {
        developer {
            id.set("snazy")
            name.set("Robert Stupp")
            email.set("snazy@snazy.de")
        }
    }
    scm {
        connection.set("https://github.com/datastax/junitpytest.git")
        developerConnection.set("https://github.com/datastax/junitpytest.git")
        url.set("https://github.com/datastax/junitpytest/")
    }
}

publishing {
    publications {
        afterEvaluate {
            named<MavenPublication>("pluginMaven") {
                artifactId = "pytest-gradle-plugin"

                pom(configurePom)
            }
            named<MavenPublication>("pytestPluginMarkerMaven") {
                pom(configurePom)
            }
        }
    }
}

signing {
    useGpgCmd()
    afterEvaluate {
        sign(publishing.publications["pluginMaven"])
        sign(publishing.publications["pytestPluginMarkerMaven"])
    }
    if (!project.hasProperty("signing.gnupg.keyName"))
        isRequired = false
}
tasks.withType<Sign> {
    onlyIf { project.hasProperty("signing.gnupg.keyName") }
}

nexusPublishing {
    clientTimeout.set(Duration.ofMinutes(3))
    repositories {
        sonatype()
    }
}

tasks.withType<GenerateModuleMetadata> {
    // module metadata doesn't contain the shadowJar, so disable it here
    enabled = false
}

val generateVersionProperties by tasks.registering(WriteVersionProperties::class)

tasks.named("compileJava") {
    dependsOn(generateVersionProperties)
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadowed)
    relocate("org.objectweb.asm", "com.datastax.junitpytest.gradleplugin.deps.org.objectweb.asm")
}

tasks.named<Jar>("jar") {
    enabled = false
    dependsOn(shadowJar)
}

configurations {
    configureEach {
        outgoing {
            val removed = artifacts.removeIf { it.classifier.isNullOrEmpty() }
            if (removed) {
                artifact(tasks.shadowJar) {
                    classifier = ""
                }
            }
        }
    }
    // used by plugin-publish plugin
    archives {
        outgoing {
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))
        }
    }
}

gradlePlugin {
    plugins {
        create("pytest") {
            id = "com.datastax.junitpytest"
            implementationClass = "com.datastax.junitpytest.gradleplugin.PytestPlugin"
            description = project.description
        }
    }
}

pluginBundle {
    website = "https://github.com/datastax/junitpytest/"
    vcsUrl = "https://github.com/datastax/junitpytest/"
    description = project.description
    tags = listOf("jvm", "java", "junit", "pytest", "python")

    plugins {
        named("pytest") {
            displayName = readableName
        }
    }
}
