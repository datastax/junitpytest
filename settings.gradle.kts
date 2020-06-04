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

pluginManagement {
    repositories {
        gradlePluginPortal()
//        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
//        maven { url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates-local") }
    }
    // No plugin marker for plugin RC - can be removed when going to a final version
//    resolutionStrategy {
//        eachPlugin {
//            if (requested.id.id == "com.gradle.enterprise") {
//                useModule("com.gradle:gradle-enterprise-gradle-plugin:${requested.version}")
//            }
//            if (requested.id.id == "com.gradle.enterprise.test-distribution") {
//                useModule("com.gradle.enterprise.test-distribution:com.gradle.enterprise.test-distribution.gradle.plugin:${requested.version}")
//            }
//        }
//    }

    plugins.id("com.gradle.enterprise.test-distribution") version "1.0.1"
    plugins.id("org.jetbrains.gradle.plugin.idea-ext") version "0.7"
}

plugins {
    id("com.gradle.enterprise") version "3.3"
}

val versionFromFile = file("gradle/version.txt").readText().trim()

val gradleEnterpriseUrl: String? = System.getProperty("gradle.enterprise.url")
if (gradleEnterpriseUrl != null) {
    try {
        val gradleEnterpriseUri = uri(gradleEnterpriseUrl.trim())

        // The Gradle Enterprise server might be only accessible via a VPN, so try to resolve the hostname
        // and if that fails, just don't use it.
        java.net.InetAddress.getByName(gradleEnterpriseUri.host)

        gradleEnterprise {
            server = gradleEnterpriseUri.toString()
            // authentication: see https://docs.gradle.com/enterprise/gradle-plugin/#authenticating_with_gradle_enterprise
            buildScan {
                tag("JUNIT_PYTEST")
                tag("JUNIT_PYTEST_${versionFromFile.replace("-SNAPSHOT", "").replace('.', '_').replace('-', '_')}")
                if (System.getenv().containsKey("JENKINS_SERVER_COOKIE")) {
                    tag("CI")
                    val jobNameLabel = "ci_job_name"
                    val jobName = System.getenv()["JOB_NAME"] as String
                    val buildNumberLabel = "ci_build_number"
                    val buildNumber = System.getenv()["BUILD_NUMBER"] as String
                    value(jobNameLabel, jobName)
                    value("ci_job_base_name", System.getenv()["JOB_BASE_NAME"])
                    value(buildNumberLabel, buildNumber)
                    value("ci_stage_name", System.getenv()["STAGE_NAME"])
                    link("CI build", System.getenv()["BUILD_URL"])
                    link("Scans from same build", customValueSearchUrl(mapOf(jobNameLabel to jobName, buildNumberLabel to buildNumber)))
                    link("Scans from same job", customValueSearchUrl(mapOf(jobNameLabel to jobName)))
                }
                if (java.lang.Boolean.getBoolean("idea.active")) {
                    tag("IDEA")
                    value("idea_version", System.getProperty("idea.version"))
                    if (java.lang.Boolean.getBoolean("idea.sync.active")) {
                        tag("IDEA_SYNC")
                    }
                }
                publishAlways()
                isCaptureTaskInputFiles = true
                background {
                    val gitCommitId = execAndGetStdout(listOf("git", "rev-parse", "--verify", "HEAD"))
                    if (gitCommitId.isNotBlank()) {
                        val commitIdLabel = "git-commit-id"
                        value(commitIdLabel, gitCommitId)
                        link("Git commit id", customValueSearchUrl(mapOf(commitIdLabel to gitCommitId)))
                    }

                    val gitBranchName = execAndGetStdout(listOf("git", "rev-parse", "--abbrev-ref", "HEAD"))
                    if (gitBranchName.isNotBlank()) {
                        tag(gitBranchName)
                        value("git-branch", gitBranchName)

                        var gitRemote = execAndGetStdout(listOf("git", "config", "branch.${gitBranchName}.remote"))
                        if (gitRemote.isBlank())
                            gitRemote = "origin"
                        var gitRemoteUrl = execAndGetStdout(listOf("git", "config", "remote.${gitRemote}.url"))
                        if (gitRemoteUrl.isNotBlank()) {
                            if (gitRemoteUrl.startsWith("git@github.com:"))
                                gitRemoteUrl = "https://github.com/${gitRemoteUrl.substring("git@github.com:".length)}"
                            if (gitRemoteUrl.endsWith(".git"))
                                gitRemoteUrl = gitRemoteUrl.substring(0, gitRemoteUrl.length - ".git".length)
                            link("GitHub tree", "${gitRemoteUrl}/commits/${gitBranchName}")
                            link("GitHub commit", "${gitRemoteUrl}/commit/${gitCommitId}")
                        }
                    }

                    val gitStatus = execAndGetStdout(listOf("git", "status", "--porcelain=2", "--branch"))
                    if (gitStatus.isNotBlank()) {
                        val gitStatusLines = gitStatus.lines()
                        if (gitStatusLines.any { l -> !l.startsWith('#') })
                            tag("Dirty")
                        value("git-status", gitStatus)
                    }
                }
            }
            allowUntrustedServer = true
        }
    } catch (e: java.io.IOException) {
        logger.warn("Gradle Enterprise build scans not available, failed to resolve the host running Gradle Enterprise: $e")
    }
}

fun customValueSearchUrl(search: Map<String, String>): String {
    val query = search.map { e -> "search.names=${encodeURL(e.key)}&search.values=${encodeURL(e.value)}" }.joinToString("&")
    return "${appendIfMissing(gradleEnterprise.buildScan.server, "/")}scans?$query#selection.buildScanB=%7BSCAN_ID%7D"
}

fun encodeURL(url: String): String {
    return java.net.URLEncoder.encode(url, "UTF-8")
}

fun appendIfMissing(str: String, suffix: String): String {
    return if (str.endsWith(suffix)) str else (str + suffix)
}

fun execAndGetStdout(args: List<String>): String {
    val stdout = java.io.ByteArrayOutputStream()
    val stderr = java.io.ByteArrayOutputStream()
    val execResult = exec {
        commandLine(args)
        isIgnoreExitValue = true
        standardOutput = stdout
        errorOutput = stderr
    }
    if (execResult.exitValue != 0) {
        logger.info("Exec $args failed: \n$stdout\n$stderr")
    }
    return trimAtEnd(stdout.toString())
}

fun trimAtEnd(str: String): String {
    return "x$str".trim().substring(1)
}

buildCache {
    local {
        isEnabled = true
    }
}
val remoteBuildCacheUrl: String? = System.getProperty("gradle.cache.url")
if (remoteBuildCacheUrl != null) {
    try {
        // The Gradle Build Cache server might be only accessible via a VPN, so try to resolve the hostname
        // and if that fails, just don't use it.
        val buildCacheUri = uri(remoteBuildCacheUrl.trim())
        java.net.InetAddress.getByName(buildCacheUri.host)

        buildCache {
            remote(HttpBuildCache::class) {
                url = buildCacheUri
                isPush = java.lang.Boolean.getBoolean("gradle.build-cache.push")
                isAllowUntrustedServer = true
            }
        }
    } catch (e: java.io.IOException) {
        logger.warn("Remote Gradle cache not available, failed to resolve the host running Gradle Build Cache: $e")
    }
}

rootProject.name = "junit-pytest"

gradle.beforeProject {
    group = "com.datastax.junitpytest"
    version = versionFromFile
}

includeBuild("buildSrcComposite")

include("pytest-gradle-plugin")
include("pytest-junit-engine")
include("common")
include("junit-pytest-plugin")
include("integration-test")
include("gradle-test-agent:jdk11and8")
include("gradle-test-agent:python3")
