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

import com.datastax.junitpytest.common.PathBinary
import com.datastax.junitpytest.common.PytestVersion
import com.datastax.junitpytest.common.VirtualEnv
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.getByType
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import javax.inject.Inject

/**
 * Creates the Python virtual environment and installs the requirements including the pytest-plugin.
 * Creates a text file containing the "frozen requirements" (from {@code pip freeze}.
 */
@CacheableTask
open class PytestCreateVirtualenv
@Inject constructor(
        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        val requirementsSource: RegularFileProperty,

        @OutputFile
        val frozenRequirements: RegularFileProperty,

        @Internal
        val venvDirectory: DirectoryProperty
) : DefaultTask() {

    @TaskAction
    fun createVirtualenv() {
        val pytestExtension = project.extensions.getByType(PytestExtension::class)

        val virtualenvExec = PathBinary.fileForExecutableFromPath({ GradleException("Could not find executable for ${pytestExtension.virtualenvExecutable.get()}") },
                *pytestExtension.virtualenvExecutable.get().toTypedArray()).toFile()
        val pythonExec = PathBinary.fileForExecutableFromPath({ GradleException("Could not find executable for ${pytestExtension.pythonExecutable.get()}") },
                *pytestExtension.pythonExecutable.get().toTypedArray()).toFile()

        val virtualEnv = VirtualEnv(project.projectDir,
                venvDirectory.get().asFile.toPath(),
                frozenRequirements.get().asFile.toPath(),
                virtualenvExec,
                pythonExec,
                pytestExtension.pipOptions.get(),
                pytestExtension.pipEnvironment.get())

        virtualEnv.createVenvIfNecessary()
        val venvDir = venvDirectory.get().asFile
        val venvExists = venvDir.isDirectory

        if (!venvExists)
            throw GradleException("Could not create $venvDir")
        Files.createDirectories(venvDir.toPath())

        val pytestPluginFilename = "junit-pytest-plugin".replace('-', '_') +
                "-" + PytestVersion.get().pyVersion +
                "-py3-none-any.whl"

        val pytestPluginArchive = venvDir.resolve(pytestPluginFilename)
        pytestPluginArchive.writeBytes(
                PytestCreateVirtualenv::class.java.classLoader.getResource("com/datastax/junitpytest/junit-pytest-plugin/dist/$pytestPluginFilename")!!.readBytes()
        )

        val modifiedPath = virtualEnv.modifiedPathEnv()

        virtualEnv.installRequirements(requirementsSource.get().asFile.toString())
        pytestExtension.sourceRequirementsTasks.get().forEach { srcReq ->
            virtualEnv.installSourceRequirement(srcReq.get().targetDirectory.get().asFile.toString())
        }

        virtualEnv.installPytestPlugin(pytestPluginArchive.toPath())

        // Get the "frozen" requirements and remove all (git) sources that are referenced in PytestExtension.sourceRequirements
        val requirementsBuffer = ByteArrayOutputStream()
        project.exec {
            workingDir = venvDir
            environment("PATH", modifiedPath)
            executable = "${venvDir}${File.separator}bin${File.separator}pip"
            args = listOf("freeze")
            standardOutput = requirementsBuffer
        }
        frozenRequirements.get().asFile.writeText(requirementsBuffer.toString("UTF-8").lineSequence().filter { ln ->
            pytestExtension.sourceRequirementsTasks.get().stream().noneMatch { taskProv -> taskProv.get().requirementsLineMatches(ln) }
        }.joinToString("\n"))
    }
}