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

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

val junitPlatformVersion by extra("1.6.2")
val junitJupiterVersion by extra("5.6.2")

dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.platform:junit-platform-launcher:${junitPlatformVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitJupiterVersion}")
    testImplementation("org.assertj:assertj-core:3.15.0")
    testImplementation(project(":common"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitJupiterVersion}")
}

val prjGradlePlugin = evaluationDependsOn(":pytest-gradle-plugin")
val prjJUnitEngine = evaluationDependsOn(":pytest-junit-engine")
val prjPytestPlugin = evaluationDependsOn(":junit-pytest-plugin")

tasks.named<Test>("test") {
    dependsOn(prjGradlePlugin.tasks.named("shadowJar"),
            prjJUnitEngine.tasks.named("shadowJar"),
            prjPytestPlugin.tasks.named("jar"))

    inputs.dir(project.file("test-project"))

    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
    setForkEvery(0)
    systemProperty("testProject", project.file("test-project").relativeTo(projectDir))
    systemProperty("rootProject", rootProject.projectDir.relativeTo(projectDir))
    systemProperty("projectVersion", project.version)
    systemProperty("projectPyVersion", project.version.toString().replace("-SNAPSHOT", ".dev0"))
}
