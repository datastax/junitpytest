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

import com.datastax.junitpytest.common.PytestCollectEntry
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.getByType
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import java.io.*
import java.nio.file.Paths
import javax.inject.Inject

@CacheableTask
open class PytestDiscovery
@Inject constructor(
        /**
         * Optional: additional command line options for `pytest`.
         *
         * Same property as in the [Pytest] task.
         */
        @Input
        val pytestOptions: ListProperty<String>,
        /**
         * Optional: additional environment variables used when invoking `pytest`.
         *
         * Same property as in the [Pytest] task.
         */
        @Input
        val pytestEnvironment: MapProperty<String, String>,
        /**
         * File emitted by the [PytestDiscovery] tasks that contains the list of all collected pytest-tests.
         *
         * Same property as in the [Pytest] task.
         */
        @Internal
        val collectedTestsFile: RegularFileProperty,

        @Internal
        val venvDirectory: DirectoryProperty
) : DefaultTask() {

    /**
     * Same property as [PytestExtension.pytestDirectorySet]
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    val pytestSource = project.extensions.getByType(PytestExtension::class).pytestDirectorySet

    /**
     * Defaults to [org.gradle.api.file.SourceDirectorySet.getDestinationDirectory] of [pytestSource]
     */
    @OutputDirectory
    val outputDirectory = project.objects.directoryProperty().convention(pytestSource.destinationDirectory)

    fun sourceDirectory(): File {
        return pytestSource.sourceDirectories.singleFile
    }

    @TaskAction
    fun discover() {
        val outputFile = collectedTestsFile.get().asFile

        val pytestDir = sourceDirectory()

        outputFile.parentFile.mkdirs()
        val stderr = ByteArrayOutputStream()
        FileOutputStream(outputFile).use {
            val outStream = BufferedOutputStream(it)

            val venvDir = venvDirectory.get().asFile

            val modifiedPath = "${venvDir}${File.separator}bin${File.pathSeparator}${System.getenv("PATH")}"

            val execResult = project.exec {
                workingDir = pytestDir
                environment("PATH", modifiedPath)
                executable = "${venvDir}${File.separator}bin${File.separator}pytest"
                args = listOf("--collect-only",
                        "--gradle") + pytestOptions.get()
                environment(pytestEnvironment.get())
                standardOutput = outStream
                errorOutput = stderr
                isIgnoreExitValue = true
            }

            outStream.flush()

            if (execResult.exitValue != 0) {
                throw GradleException("pytest discovery failed - exit code = ${execResult.exitValue}.\n" +
                        "command: ${venvDir}${File.separator}bin${File.separator}pytest --collect-only --gradle ${pytestOptions.get().joinToString(" ")}\n" +
                        "stderr: $stderr")
            }
        }

        val filesAndMethods: MutableMap<String, MutableList<PytestCollectEntry>> = mutableMapOf()
        BufferedReader(outputFile.reader()).use {
            it.lines().forEach { line ->
                val entry = PytestCollectEntry.parse(line)
                if (entry != null) {
                    filesAndMethods.computeIfAbsent(entry.fullyQualifiedClassName) { mutableListOf() }.add(entry)
                }
            }
        }

        filesAndMethods.forEach { (className, entries) ->
            val outFile = outputDirectory.get().file("${className.replace('.', '/')}.class").asFile
            outFile.parentFile.mkdirs()
            val methods = entries.map { e -> e.method }.toSortedSet()

            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS.or(ClassWriter.COMPUTE_FRAMES))
            cw.visit(V1_8, ACC_PUBLIC, className.replace('.', '/'), null, "java/lang/Object", null)
            cw.visitSource(Paths.get(entries[0].file).fileName.toString(), null)

            val ctor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
            ctor.visitMaxs(2, 1)
            ctor.visitVarInsn(ALOAD, 0) // push `this` to the operand stack
            ctor.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Any::class.java), "<init>", "()V", false) // call the constructor of super class
            ctor.visitInsn(RETURN)
            ctor.visitEnd()
            methods.forEach { method ->
                val mv = cw.visitMethod(ACC_PUBLIC, method, "()V", null, null)
                mv.visitMaxs(1, 1)
                mv.visitInsn(RETURN)
                mv.visitEnd()
            }
            cw.visitEnd()
            outFile.writeBytes(cw.toByteArray())
        }
    }
}