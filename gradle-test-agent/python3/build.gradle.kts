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
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.datastax.junitpytest.gradle.withProjectProperty

plugins {
    `java-base`
    id("com.bmuschko.docker-java-application") version "6.4.0"
    id("com.datastax.junitpytest.gradle.writeversion")
}

val dockerRepos: List<String> = rootProject.extra.get("dockerRepos") as List<String>
val imageTags: List<String> = rootProject.extra.get("dockerImageTags") as List<String>
val gradleTestAgentVersion = rootProject.extra["gradleTestAgentVersion"]
val prjJdk11and8 = evaluationDependsOn(":gradle-test-agent:jdk11and8")

val createDockerfile by tasks.registering(Dockerfile::class) {
    group = "docker"

    from("gradle-test-distribution-agent-jdk11and8:$gradleTestAgentVersion")

    instructionsFromTemplate(file("Dockerfile"))
}

val buildDockerImage by tasks.registering(DockerBuildImage::class) {
    dependsOn(createDockerfile, prjJdk11and8.tasks.named(name))

    val imgs = dockerRepos.flatMap {  repo ->
        imageTags.map { t -> "${repo}gradle-test-distribution-agent-python3:$t" }
    }

    remove.set(true)
    images.set(imgs)
}

val pushDockerImage by tasks.registering(DockerPushImage::class) {
    group = "publishing"
    description = "Push Gradle Enterprise test-agent Docker image for python3 (and ant)"

    dependsOn(buildDockerImage, prjJdk11and8.tasks.named(name))

    val imgs = dockerRepos.filter { repo -> repo.isNotEmpty() }.flatMap {  repo ->
        imageTags.map { t -> "${repo}gradle-test-distribution-agent-python3:$t" }
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
