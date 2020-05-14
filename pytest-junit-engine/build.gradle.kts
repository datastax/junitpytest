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

import java.time.Duration
import com.datastax.junitpytest.gradle.tasks.WriteVersionProperties
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("de.marcphilipp.nexus-publish") version "0.4.0"
}

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

val junitPlatformVersion by extra("1.6.2")
val junitJupiterVersion by extra("5.6.2")
val readableName = "DataStax junitpytest - JUnit 5 pytest engine"
description = "DataStax junitpytest - JUnit 5 pytest engine"

val shadowed: Configuration by configurations.creating
configurations.named("compileOnly") {
    extendsFrom(shadowed, configurations.getByName("shadow"))
}
configurations.named("testImplementation") {
    extendsFrom(shadowed, configurations.getByName("shadow"))
}

dependencies {
    shadow("org.junit.platform:junit-platform-launcher:${junitPlatformVersion}")
    shadow("org.junit.jupiter:junit-jupiter-api:${junitJupiterVersion}")

    add("shadowed", project(":junit-pytest-plugin"))
    add("shadowed", project(":common"))

    testImplementation("org.assertj:assertj-core:3.15.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitJupiterVersion}")
}

sourceSets {
    main {
        resources.srcDir(buildDir.resolve("generated/sources/version"))
    }
}

publishing {
    publications {
        register<MavenPublication>("main") {
            shadow.component(this)
            artifact(tasks.getByName("sourcesJar"))
            artifact(tasks.getByName("javadocJar"))

            pom {
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
        }
    }
}

signing {
    useGpgCmd()
    afterEvaluate {
        sign(publishing.publications["main"])
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

val generateVersionProperties by tasks.registering(WriteVersionProperties::class)

tasks.named("compileJava") {
    dependsOn(generateVersionProperties)
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
}

tasks.named<Jar>("jar") {
    enabled = false
    archiveClassifier.set("plain")
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadowed)
    archiveClassifier.set("")
    from()
}
