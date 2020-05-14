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

import org.jetbrains.gradle.ext.*

plugins {
    `java-library`
    idea
    id("org.jetbrains.gradle.plugin.idea-ext")
}

val ideName = "DataStax junit-python ${rootProject.version.toString().replace(Regex("^([0-9.]+).*"), "$1")}"
idea {
    module {
        name = ideName
        isDownloadSources = true // this is the default BTW
        inheritOutputDirs = true
    }

    project {
        withGroovyBuilder {
            "settings" {
                val copyright: CopyrightConfiguration = getProperty("copyright") as CopyrightConfiguration
                val encodings: EncodingConfiguration = getProperty("encodings") as EncodingConfiguration
                val delegateActions: ActionDelegationConfig = getProperty("delegateActions") as ActionDelegationConfig

                delegateActions.testRunner = ActionDelegationConfig.TestRunner.CHOOSE_PER_TEST

                encodings.encoding = "UTF-8"
                encodings.properties.encoding = "UTF-8"

                copyright.useDefault = "Apache"
                copyright.profiles.create("Apache") {
                    notice = file("gradle/license.txt").readText()
                }
            }
        }
    }
}
// There's no proper way to set the name of the IDEA project (when "just importing" or syncing the Gradle project)
val ideaDir = projectDir.resolve(".idea")
if (ideaDir.isDirectory)
    ideaDir.resolve(".name").writeText(ideName)

extra.set("gradleTestAgentVersion", "1.0")
extra.set("dockerRepos", listOf("", "snazy/"))
extra.set("dockerImageTags",
        if (project.hasProperty("tagDockerAsLatest"))
            listOf(extra["gradleTestAgentVersion"], "latest")
        else
            listOf(extra["gradleTestAgentVersion"])
)

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.ALL
}
