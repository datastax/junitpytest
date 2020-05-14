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

import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

interface RepoSource : Task {
    @get:Input
    val repository: Property<String>

    @get:Input
    val eggName: Property<String>

    @get:OutputDirectory
    val targetDirectory: DirectoryProperty

    fun requirementsLineMatches(line: String): Boolean {
        if (line.contains(repository.get()))
            return true
        if (eggName.isPresent && line.contains("#egg=${eggName.get()}&"))
            return true
        return false
    }
}