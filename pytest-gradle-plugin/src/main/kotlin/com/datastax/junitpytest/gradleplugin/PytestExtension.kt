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
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property

abstract class PytestExtension(private val project: Project) {
    /**
     * JUnit platform version to use.
     * Defaults to `1.6.2`
     */
    val junitPlatformVersion = project.objects.property(String::class).convention("1.6.2")

    /**
     * JUnit Jupiter version to use.
     * Defaults to `5.6.2`
     */
    val junitJupiterVersion = project.objects.property(String::class).convention("5.6.2")

    /**
     * The list of *file* name of the `virtualenv` executable to look for using the `PATH` environment.
     * Specifying a path here is wrong, as the binaries reside in different locations in different distributions.
     * Defaults to `virtualenv`.
     */
    val virtualenvExecutable = project.objects.listProperty(String::class).convention(listOf("virtualenv"))

    /**
     * The list of *file* name of the `python` executable to look for using the `PATH` environment.
     * Specifying a path here is wrong, as the binaries reside in different locations in different distributions.
     * Defaults to `python3.8`, `python3.7`, `python3.6`, `python3`, `python`.
     */
    val pythonExecutable = project.objects.listProperty(String::class).convention(listOf("python3.8", "python3.7", "python3.6", "python3", "python"))

    /**
     * Source directory set for the pytest sources.
     * Excludes `venv`, `logs`, `.git`, `.idea`, `__pycache__`, `*.swp` `.pytest_cache` by default.
     * Only a single source-directory is allowed.
     *
     * Must be specified, e.g. via
     * ```
     * pytest {
     *     pytestDirectorySet.srcDir("src/test/python")
     * }
     * ```
     */
    val pytestDirectorySet = project.objects.sourceDirectorySet("pytest", "pytest source directory").apply {
        exclude("logs")
        exclude("venv")
        exclude(".git")
        exclude(".idea")
        exclude("**/__pycache__")
        exclude("**/.*.swp")
        exclude(".pytest_cache")
        destinationDirectory.convention(project.layout.buildDirectory.dir("generated/pytest-java/classes/test/java"))
    }

    /**
     * Location of the `requirements.txt` file.
     * Defaults to `requirements.txt` in [pytestDirectorySet].
     */
    val requirementsSource = project.objects.fileProperty().convention { pytestDirectorySet.sourceDirectories.singleFile.resolve("requirements.txt") }

    /**
     * Command line options when running `pip`.
     */
    val pipOptions = project.objects.listProperty(String::class).convention(listOf())

    /**
     * Additional environment variables used when running `pip`.
     */
    val pipEnvironment = project.objects.mapProperty(String::class, String::class).convention(mapOf())

    val sourceRequirementsTasks: ListProperty<TaskProvider<out RepoSource>> = project.objects.listProperty<TaskProvider<out RepoSource>>().convention(listOf())

    abstract fun createTasks(baseName: String): Pair<TaskProvider<Pytest>, TaskProvider<PytestDiscovery>>
}
