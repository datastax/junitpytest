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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property
import org.gradle.process.CommandLineArgumentProvider
import javax.inject.Inject

open class Pytest
@Inject constructor(
        /**
         * Optional: additional command line options for `pytest`.
         *
         * Same property as in the [PytestDiscovery] task.
         */
        @Input
        val pytestOptions: ListProperty<String>,
        /**
         * Optional: additional environment variables used when invoking `pytest`.
         *
         * Same property as in the [PytestDiscovery] task.
         */
        @Input
        val pytestEnvironment: MapProperty<String, String>,
        /**
         * File emitted by the [PytestDiscovery] tasks that contains the list of all collected pytest-tests.
         *
         * Same property as in the [PytestDiscovery] task.
         */
        @Internal
        val collectedTestsFile: RegularFileProperty,

        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        val frozenRequirements: RegularFileProperty,

        @Internal
        val venvDirectory: DirectoryProperty
) : Test() {

    init {
        withJUnitPlatform {
            includeEngines = setOf("pytest")
        }
        apply {
            systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
            environment(pytestEnvironment.get())
        }
        jvmArgumentProviders.add(CmdLineArgs())
        outputs.upToDateWhen { false }
    }

    inner class CmdLineArgs : CommandLineArgumentProvider {
        override fun asArguments(): MutableIterable<String> {
            val pytestExtension = project.extensions.getByType(PytestExtension::class)
            val l = mutableListOf(
                    "-Dpytest.debug=${verbose.get()}",
                    "-Dpytest.debug=${debug.get()}",
                    "-Dpytest.keepOutputForPassed=${keepOutputsForPassedTests.get()}",
                    "-Dpytest.collectedTestsFile=${collectedTestsFile.get().asFile}",
                    "-Dpytest.pytestOutputs=${pytestOutputs.get().asFile}",
                    "-Dpytest.frozenRequirements=${frozenRequirements.get().asFile}",
                    "-Dpytest.venv=${venvDirectory.get().asFile}",
                    "-Dpytest.exec.virtualenv=${pytestExtension.virtualenvExecutable.get().joinToString(",")}",
                    "-Dpytest.exec.python=${pytestExtension.pythonExecutable.get().joinToString(",")}",
                    "-Dpytest.cwd=${project.extensions.getByType(PytestExtension::class).pytestDirectorySet.sourceDirectories.singleFile}")
            pytestOptions.get().forEachIndexed { index, s ->
                l.add("-Dpytest.option.$index=$s")
            }
            pytestEnvironment.get().entries.toList().forEachIndexed { index, kv ->
                l.add("-Dpytest.env.$index=${kv.key}=${kv.value}")
            }
            pytestExtension.pipOptions.get().forEachIndexed { index, s ->
                l.add("-Dpytest.pip.option.$index=$s")
            }
            pytestExtension.pipEnvironment.get().entries.toList().forEachIndexed { index, kv ->
                l.add("-Dpytest.pip.env.$index=${kv.key}=${kv.value}")
            }
            pytestExtension.sourceRequirementsTasks.get().forEachIndexed { index, taskProvider ->
                val src = taskProvider.get()
                l.add("-Dpytest.source.$index=${src.repository.get()}=${src.targetDirectory.get().asFile}")
            }
            return l
        }
    }

    /**
     * All output (stdout, stderr, explicitly added output files) of "passed" tests is discarded by default.
     * Set this property to `true` to keep the output of passed tests as well.
     */
    @Internal
    val keepOutputsForPassedTests = project.objects.property(Boolean::class).convention(false)

    /**
     * Enable internal debug information for the pytest-junit-engine.
     */
    @Internal
    val debug = project.objects.property(Boolean::class).convention(project.gradle.startParameter.logLevel <= LogLevel.DEBUG || project.hasProperty("pytest.debug"))

    /**
     * Enable verbose output for the pytest-junit-engine.
     */
    @Internal
    val verbose = project.objects.property(Boolean::class).convention(project.gradle.startParameter.logLevel <= LogLevel.INFO || project.hasProperty("pytest.verbose"))

    /**
     * Directory where output files registered by pytest-tests via `junitpytest.GradlePlugin.register_outputs(files_and_dirs)`
     * should end up. Defaults to `build/test-results/<task-name>`, i.e. `build/test-results/pytest`
     * for the default `pytest` task.
     */
    @OutputDirectory
    val pytestOutputs = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("test-results/${name}"))

}
