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

import org.gradle.api.tasks.PathSensitivity.RELATIVE

plugins {
    `java-library`
}

java {
    @Suppress("UnstableApiUsage")
    withJavadocJar()
    @Suppress("UnstableApiUsage")
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val pythonSrc = project.file("src/main/python")
val venv = buildDir.resolve("venv")

val createVenv by tasks.registering {
    outputs.upToDateWhen { venv.isDirectory }

    doFirst {
        if (!venv.isDirectory) {
            exec {
                workingDir = pythonSrc
                executable = "virtualenv"
                args = listOf("-p", "python3", venv.toString())
            }
        }
    }
}

val createSetupPy by tasks.registering {
    dependsOn(createVenv)

    val setupPy = pythonSrc.resolve("setup.py")
    val content = """
            import setuptools
            with open("README.md", "r") as fh:
                long_description = fh.read()
            setuptools.setup(
                name="junit-pytest-plugin",
                version="${project.version.toString().replace("-SNAPSHOT", ".dev0")}",
                author="Robert Stupp",
                author_email="snazy@snazy.de",
                description="Gateway plugin for pytest to send test events to the pytest-junit-engine",
                long_description=long_description,
                long_description_content_type="text/markdown",
                url="https://github.com/datastax/junitpytest",
                packages=setuptools.find_packages(),
                entry_points={"pytest11": ["name_of_plugin = junitpytest.gradle"]},
                classifiers=[
                    "Framework :: Pytest",
                    "Programming Language :: Python :: 3",
                    "License :: OSI Approved :: ASF2",
                    "Operating System :: OS Independent",
                ],
                python_requires='>=3.6',
            )
            """.trimIndent()

    inputs.property("version", project.version)
    inputs.property("setup.py", content)
    outputs.file(setupPy)

    doFirst {
        setupPy.writeText(content)
    }
}

val eggName = project.name.replace('-', '_')

val createWheel by tasks.registering(DefaultTask::class) {
    dependsOn(createSetupPy)

    val wheelDir = buildDir.resolve("wheel")

    inputs.files(project.files(pythonSrc)).withPathSensitivity(RELATIVE)
    outputs.dir(wheelDir)

    doFirst {
        exec {
            workingDir = pythonSrc
            executable = "$venv/bin/python3"
            args = listOf("setup.py", "sdist", "bdist_wheel")
        }
        delete(wheelDir)
        mkdir(wheelDir)
        pythonSrc.resolve("dist").renameTo(wheelDir.resolve("dist"))
        pythonSrc.resolve("$eggName.egg-info").renameTo(wheelDir.resolve("$eggName.egg-info"))
    }
}

tasks.named<Jar>("jar") {
    dependsOn(createWheel)

    into("com/datastax/junitpytest/junit-pytest-plugin")
    from(buildDir.resolve("wheel"))
}
