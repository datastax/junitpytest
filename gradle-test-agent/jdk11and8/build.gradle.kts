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
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.datastax.junitpytest.gradle.withProjectProperty

plugins {
    `java-base`
    id("com.bmuschko.docker-java-application") version "6.4.0"
}

val dockerRepos: List<String> = rootProject.extra.get("dockerRepos") as List<String>
val imageTags: List<String> = rootProject.extra.get("dockerImageTags") as List<String>
val gradleTestAgentVersion = rootProject.extra["gradleTestAgentVersion"]
val jdk8esum = "f39b523c724d0e0047d238eb2bb17a9565a60574cf651206c867ee5fc000ab43"
val jdk8url = "https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u242-b08/OpenJDK8U-jdk_x64_linux_hotspot_8u242b08.tar.gz"

val createDockerfile by tasks.registering(Dockerfile::class) {
    group = "docker"

    from("gradle/gradle-enterprise-test-distribution-agent:$gradleTestAgentVersion")

    arg("ESUM=$jdk8esum")
    arg("BINARY_URL=$jdk8url")

    instructionsFromTemplate(file("Dockerfile"))
}

val buildDockerImage by tasks.registering(DockerBuildImage::class) {
    dependsOn(createDockerfile)

    val imgs = dockerRepos.flatMap {  repo ->
        imageTags.map { t -> "${repo}gradle-test-distribution-agent-jdk11and8:$t" }
    }

    remove.set(true)
    images.set(imgs)
}

val pushDockerImage by tasks.registering(DockerPushImage::class) {
    group = "publishing"
    description = "Push Gradle Enterprise test-agent Docker image for jdk11+8"

    dependsOn(buildDockerImage)

    val imgs = dockerRepos.filter { repo -> repo.isNotEmpty() }.flatMap {  repo ->
        imageTags.map { t -> "${repo}gradle-test-distribution-agent-jdk11and8:$t" }
    }

    images.set(imgs)
    withProjectProperty("junitpython.dockerhub.username") { v -> registryCredentials.username.set(v.toString()) }
    withProjectProperty("junitpython.dockerhub.password") { v -> registryCredentials.password.set(v.toString()) }
}

tasks.named("assemble") {
    dependsOn(buildDockerImage)
}

tasks.register("publish") {
    dependsOn(pushDockerImage)
}
