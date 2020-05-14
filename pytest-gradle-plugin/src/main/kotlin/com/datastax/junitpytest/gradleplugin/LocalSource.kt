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

package com.datastax.junitpytest.gradleplugin

import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.property

@Suppress("unused")
open class LocalSource : Sync(), RepoSource {

    override val repository = project.objects.property(String::class)

    override val eggName = project.objects.property(String::class)

    override val targetDirectory = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("generated/git-clones/${name}"))

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val sourceDirectory = project.objects.directoryProperty()

    init {
        group = "misc"
        description = "Map a local directory"

        apply {
            into(targetDirectory)
            from(sourceDirectory)
        }
    }
}