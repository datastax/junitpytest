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

import com.datastax.junitpytest.common.PytestVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import javax.inject.Inject

@Suppress("unused")
open class PytestPlugin : Plugin<Project> {
    @get:Inject
    open val objectFactory: ObjectFactory
        get() {
            throw UnsupportedOperationException()
        }

    override fun apply(project: Project): Unit = project.run {
        plugins.apply(JavaLibraryPlugin::class)

        val pytestExtension = extensions.create(PytestExtension::class, "pytest", PytestExtensionInternal::class, this)

        dependencies {
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:${pytestExtension.junitPlatformVersion.get()}")
            add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-api:${pytestExtension.junitJupiterVersion.get()}")
            add("testRuntimeOnly", "com.datastax.junitpytest:pytest-junit-engine:${PytestVersion.get().version}")
        }

        // Disable the default test task, so it doesn't try to run the generated class files and bark that there are no tests
        tasks.named<Test>("test") {
            enabled = false
        }

        // Create default "pair" of tasks
        pytestExtension.createTasks("")
    }
}
