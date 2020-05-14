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

import org.gradle.api.Project
import org.gradle.api.internal.jvm.ClassDirectoryBinaryNamingScheme
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.register

internal open class PytestExtensionInternal(private val project: Project) : PytestExtension(project) {

    private val venvDirectory = project.objects.directoryProperty()

    private val frozenRequirements = project.objects.fileProperty().convention { project.layout.buildDirectory.file("generated/python-requirements/frozen-requirements.txt").get().asFile }

    private val createVirtualenv = project.tasks.register<PytestCreateVirtualenv>("createVirtualenv", requirementsSource, frozenRequirements, venvDirectory)

    init {
        project.objects.sourceDirectorySet("pytestVenv", "virtualenv for pytest").apply {
            destinationDirectory.convention(project.layout.buildDirectory.dir("generated/pytest-venv"))
            compiledBy(createVirtualenv) { venvDirectory }
        }
        createVirtualenv.configure {
            for (srcDir in sourceRequirementsTasks.get()) {
                dependsOn(srcDir)
                inputs.dir(srcDir.get().targetDirectory).withPathSensitivity(RELATIVE)
            }
        }
    }

    override fun createTasks(baseName: String): Pair<TaskProvider<Pytest>, TaskProvider<PytestDiscovery>> = project.run {
        val pytestExtension = extensions.getByType(PytestExtension::class)

        val namingScheme = ClassDirectoryBinaryNamingScheme(baseName)
        val pytestTaskName = namingScheme.getTaskName("pytest", null)

        // common properties for PytestDiscovery + Pytest
        val pytestOptions = objects.listProperty(String::class)
        val pytestEnvironment = objects.mapProperty(String::class, String::class)
        val collectedTestsFile = project.objects.fileProperty().convention(pytestExtension.pytestDirectorySet.destinationDirectory.file("META-INF/${pytestTaskName}"))

        val discoverPytest = tasks.register<PytestDiscovery>(namingScheme.getTaskName("discoverPytest", null), pytestOptions, pytestEnvironment, collectedTestsFile, venvDirectory)
        discoverPytest.configure {
            dependsOn(createVirtualenv)
        }

        pytestExtension.pytestDirectorySet.compiledBy(discoverPytest) { it.outputDirectory }

        val pytest = tasks.register<Pytest>(pytestTaskName, pytestOptions, pytestEnvironment, collectedTestsFile, frozenRequirements, venvDirectory)
        pytest.configure() {
            group = "verification"
            description = "Run Python based ${if (baseName.isEmpty()) "" else "$baseName "}pytest"

            dependsOn(discoverPytest)
            testClassesDirs = files(pytestExtension.pytestDirectorySet.outputDir)
            classpath += files(pytestExtension.pytestDirectorySet.outputDir)
            inputs.files(pytestExtension.pytestDirectorySet).withPathSensitivity(RELATIVE)
            for (srcDir in pytestExtension.sourceRequirementsTasks.get()) {
                inputs.dir(srcDir.get().targetDirectory).withPathSensitivity(RELATIVE)
            }
            inputs.files(discoverPytest.get().collectedTestsFile).withPathSensitivity(RELATIVE)
            inputs.files(frozenRequirements).withPathSensitivity(RELATIVE)
            outputs.dir(pytestOutputs)
            pytestOutputs.fileValue(project.buildDir.resolve("test-results/${name}"))
            setForkEvery(0)
        }

        return Pair(pytest, discoverPytest)
    }
}