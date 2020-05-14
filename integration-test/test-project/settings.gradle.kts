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
        mavenLocal()
    }
    plugins.id("com.datastax.junitpytest") version file("../../gradle/version.txt").readText().trim()
    plugins.id("com.gradle.enterprise.test-distribution") version "1.0.1"
}

plugins {
    id("com.gradle.enterprise") version "3.3"
}

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
            allowUntrustedServer = true
        }
    } catch (e: java.io.IOException) {
        logger.warn("Gradle Enterprise build scans not available, failed to resolve the host running Gradle Enterprise: $e")
    }
}

buildCache {
    local {
        isEnabled = false
    }
}

rootProject.name = "junit-pytest"

gradle.beforeProject {
    group = "com.datastax.junitpytest"
    version = "42.42"
}
