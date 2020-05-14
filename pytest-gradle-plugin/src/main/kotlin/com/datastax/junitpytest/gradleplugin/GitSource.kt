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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import java.io.ByteArrayOutputStream
import java.nio.file.Files

@Suppress("unused")
open class GitSource : DefaultTask(), RepoSource {

    override val repository = project.objects.property(String::class)

    override val eggName = project.objects.property(String::class)

    override val targetDirectory = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("generated/git-clones/${name}"))

    /**
     * The name of the branch (or tag or the git SHA) to clone/checkout.
     * Defaults to `master`
     */
    @Input
    val branch = project.objects.property(String::class).convention("master")

    @Input
    val gitInitCommand = project.objects.listProperty(String::class).convention(listOf("git", "init", "--separate-git-dir"))

    @Input
    val gitRemoteAddCommand = project.objects.listProperty(String::class).convention(listOf("git", "remote", "add", "--no-tags"))

    @Input
    val gitClean = project.objects.listProperty(String::class).convention(listOf("git", "clean", "-f", "-d", "-q", "x"))

    @Input
    val gitFetchCommand = project.objects.listProperty(String::class).convention(listOf("git", "fetch", "--no-tags"))

    @Input
    val gitCheckoutCommand = project.objects.listProperty(String::class).convention(listOf("git", "checkout", "--force"))

    @OutputDirectory
    val gitDirectory = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("generated/git-clones/${name}.git"))

    init {
        group = "misc"
        description = "Git sync task"

        apply {
            outputs.upToDateWhen {
                false
            }
        }
    }

    @TaskAction
    fun gitAction() {
        val cloneDir = targetDirectory.get().asFile
        val gitDir = gitDirectory.get().asFile

        val cloneDirExists = cloneDir.resolve(".git").isFile
        val gitDirExists = gitDir.resolve("config").isFile
        if (!cloneDirExists || !gitDirExists) {
            if (cloneDir.exists())
                project.delete(cloneDir)
            if (gitDir.exists())
                project.delete(gitDir)

            Files.createDirectories(cloneDir.toPath())

            // .git does not exist, we need a new git-clone
            doExec(gitInitCommand.get() + listOf(gitDir.toString(), cloneDir.toString()))
            doExec(gitRemoteAddCommand.get() + listOf("origin", repository.get()))
        } else {
            doExec(gitClean.get())
        }

        val fetchCmd = gitFetchCommand.get() + repository.get()
        val checkoutCmd = gitCheckoutCommand.get()
        val b = branch.get()

        // Try to fetch a branch
        try {
            doExec(fetchCmd + "+refs/heads/$b:refs/remotes/origin/$b")
            doExec(checkoutCmd + listOf("-B", b, "origin/$b"))
        } catch (eBranch: Exception) {
            try {
                // Try to fetch a tag
                doExec(fetchCmd + "+refs/tags/$b:refs/tags/$b")
                doExec(checkoutCmd + b)
            } catch (eTag: Exception) {
                eTag.addSuppressed(eBranch)

                try {
                    // Try to fetch a SHA
                    doExec(fetchCmd + b)
                    doExec(checkoutCmd + b)
                } catch (eSha: Exception) {
                    eSha.addSuppressed(eTag)
                    throw GradleException("Failed to fetch branch/tag/SHA ($eBranch / $eTag / $eSha)", eSha)
                }
            }
        }
    }

    private fun doExec(cmdLine: List<String>) {
        val logStdoutOnSuccess = project.gradle.startParameter.logLevel < LogLevel.LIFECYCLE
        if (!logStdoutOnSuccess) {
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val execResult = project.exec {
                workingDir = targetDirectory.get().asFile
                commandLine = cmdLine
                isIgnoreExitValue = true
                standardOutput = stdout
                errorOutput = stderr
            }
            if (execResult.exitValue != 0) {
                println(stdout)
                System.err.println(stderr)
                execResult.assertNormalExitValue()
            }
        } else {
            project.exec {
                workingDir = targetDirectory.get().asFile
                commandLine = cmdLine
            }
        }
    }
}