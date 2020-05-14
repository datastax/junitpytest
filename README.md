JUnit5 plugin to run pytest via Gradle
======================================

Allows running pytest from Gradle, either locally or via Gradle Enterprise's Distributed Testing.

# How to use it

## Example

```
import com.datastax.junitpytest.gradleplugin.Pytest

plugins {
    id("com.datastax.junitpytest") version "0.1-SNAPSHOT"
    id("com.gradle.enterprise.test-distribution")
}

repositories {
    mavenCentral()
    mavenLocal()
}

pytest { // Extension type: com.datastax.junitpytest.gradleplugin.PytestExtension
    pytestDirectorySet.srcDir("src/test/python")
}

dependencies {
    testImplementation("org.junit.platform:junit-platform-launcher:${pytest.junitPlatformVersion.get()}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${pytest.junitJupiterVersion.get()}")
    testImplementation("org.assertj:assertj-core:3.15.0")
}

tasks.named<Pytest>("pytest") {  // Note: Pytest extends Test
    pytestOptions = listOf("--some-option-passed-to-pytest",
                           "--another-pytest-option")

    // Gradle Enterprise distributed testing
    distribution {
        enabled.set(false)
        maxLocalExecutors.set(0)
        maxRemoteExecutors.set(20)
    }
}
```

## `PytestExtension` registered as `pytest`

Common properties of the `PytestExtension` extension:

* `junitPlatformVersion` (`Property<String>`): the JUnit platform version, defaults to `1.6.2`, for running pytests
* `junitJupiterVersion` (`Property<String>`): the JUnit Jupiter version, defaults to `5.6.2`, for running pytests
* `pytestDirectorySet` (`SourceDirectorySet`): users must specify exactly one `srcDir`, default excludes:
  `logs`, `venv`, `.git`, `.idea`, `**/__pycache__`, `**/.*.swp`, `.pytest_cache`
* `requirementsSource` (`RegularFileProperty`): location of the `requirements.txt` file to use, defaults to
  `requirements.txt` in `pytestDirectorySet`

The plugin registers a `pytest` (type: `com.datastax.junitpytest.gradleplugin.Pytest`) task. 
Options for pytest can be specified using `Pytest.pytestOptions` (`ListProperty<String>`).

If you want to have an additional pytest-task using different pytest-options, use 
`com.datastax.junitpytest.gradleplugin.PytestExtension.createTasks(baseName: String)` to register another
`Pytest` task. Tasks will be named using camel case with the `baseName` appended to
`pytest`. For example, `createTasks("other")` will create the `pytestOther` task.

## Using editable requirements

So called "editable requirements" (i.e. those installed via `pip install --editable`) can be handled specially using
`GitSource` and `LocalSource` tasks, which can be registered in the `pytest` Gradle extension using the
`sourceRequirementsTasks` property (type: `ListProperty<TaskProvider<out RepoSource>>`). Note that the `LocalSource`
task extends Gradle's built-in `Sync` task.

The local Gradle daemon pulls the latest HEAD from git (if using `GitSource`) and syncs the locally available source
(if using a `LocalSource`).

It's recommended to have a requirements-file that only contains the "released" requirements (non-source requirements)
and rebgister this in the `pytest` Gradle extension using the `requirementsSource` property
(type: `RegularFileProperty`) and use `GitSource`/`LocalSource` for source-requirements. Otherwise, each Gradle
distributed test agent will pull the requirements themselves. In other words: `GitSource`/`LocalSource` make the
source requirements available to all Gradle distributed test-agents.

### `GitSource` task

Necessary properties:
* `repository: Property<String>` Specifiy the git repository URL (as it appears in a `requirements.txt` file)
* `eggName: Property<String>` Specify the name of the pip "egg" as it appears in a `requirements.txt` file
* `branch: Property<String>` Specify the git branch, tag or git SHA

### `LocalSource` task

Necessary properties:
* `repository: Property<String>` The source as it appears in a `requirements.txt` file
* `eggName: Property<String>` Specify the name of the pip "egg" as it appears in a `requirements.txt` file
* `sourceDirectory: DirectoryProperty` Specify the directory containing the source of the requirement

# How it works

1. Create a Python virtual environment (Gradle task `com.datastax.junitpytest.gradleplugin.PytestCreateVirtualenv`)
1. Test discovery (Gradle task `com.datastax.junitpytest.gradleplugin.PytestDiscovery`)
    1. Use `pytest` + `junit-pytest-plugin` to collect all test classes and test cases
    1. Map the Python module + class + test-method names into Java namespace
    1. Generate Java class files (methods are empty)
1. Test execution (Gradle task `com.datastax.junitpytest.gradleplugin.Pytest`)
    1. Use a custom `org.gradle.api.tasks.testing.Test` based task to run the tests using the `pytest-junit-engine`

## Internal tasks

The plugin creates two more types of internal tasks, which should *not* be referenced/used/configured by a user:
* A `PytestDiscovery` task for each `Pytest` task to run test discovery via pytest only when the Python sources changed.
* A `PytestCreateVirtualenv` task for the project to setup the virtual environment (if necessary), install the
  junit-pytest-plugin + dependencies from `requirements.txt` and to create the "frozen" list of dependencies
  using `pip freeze`.

# Gradle Enterprise Distributed Testing

Gradle Enterprise (as of version 2020.2) Distributed Testing requires JUnit 5 to run tests and tests must be
self-contained.

Since Python setups (think: virtual environments) are machine dependent, no actual Python _installation_ is passed
around.
Instead, the Gradle tasks create a virtual environment on the machine running the Gradle daemon, install the necessary
requirements (`pip install -r requirements.txt`), collect the installed dependencies (`pip freeze`) and install the
"frozen requirements" on the test agents.

This approach ensures that the test agents use the same dependencies as the machine that started the build.
However, it may not ensure that no _other_ dependencies, that are not mentioned via `requirements.txt`, are installed
on the test agents.

# Modules (sub-projects)

* `:pytest-gradle-plugin` the Gradle plugin to wire an extension and some tasks to run Python test via Gradle
  (published as a maven-publication)
* `:pytest-junit-engine` the JUnit engine that can run Python tests (published as a maven-publication)
* `:junit-pytest-plugin` the `pytest` plugin to emit test events to the `:pytest-junit-engine` (not published,
  included in the Gradle plugin and junit-engine)
* `:common` some Java classes used by `:pytest-gradle-plugin` and `:pytest-junit-engine` (not published, included in
  the Gradle plugin and junit-engine)
       
# Build requirements

* Recent Linux or OSX
* Java 8 or newer (Java 11 is fine)
* Python >= 3.6 (with python3 and pip3 and virtualenv in `PATH` environment)

# Background information

## pytest-junit-engine

The `pytest-junit-engine` requires some configuration options. From Gradle, those are passed using system properties.

* `pytest.venv` The directory for the Python virtual environment
* `pytest.frozenRequirements` File containing the output of `pip freeze`
* `pytest.pytestOutputs` Directory receiving additional output files from Python tests added via
  `junitpytest.gradle.register_outputs(files_or_dirs)`
* `pytest.cwd` Current working directory when executing `pytest`
* `pytest.exec.virtualenv` Comma separated list of executable *file* names (looked up via `PATH`)
* `pytest.exec.python` Comma separated list of executable *file* names (looked up via `PATH`)
* `pytest.keepOutputForPassed` When set to `true`, keep the output even for tests that passed.
* `pytest.debug` When set to `true`, enables additional debug output
* `pytest.option.<N>` Additional command line options for `pytest`.
  `<N>` starts with `0`, so the first argument is `pytest.option.0`, the second `pytest.option.1`, etc.
* `pytest.env.<N>` Additional environment variables for `pytest`. Values are in the form `VAR=VALUE`
  `<N>` starts with `0`, so the first argument is `pytest.env.0`, the second `pytest.env.1`, etc.
* `pytest.pip.option.<N>` Additional command line options for `pip`.
  `<N>` starts with `0`, so the first argument is `pytest.pip.option.0`, the second `pytest.pip.option.1`, etc.
* `pytest.pip.env.<N>` Additional environment variables for `pip`. Values are in the form `VAR=VALUE`
  `<N>` starts with `0`, so the first argument is `pytest.pip.env.0`, the second `pytest.pip.env.1`, etc.
* `pytest.source.<N>` Additional requirements to be installed using `pip install --source`. 
  Values are in the form `repository=relative-path`. `repository` is the value of the property of the 
  `GitSource`/`LocalSource` tasks.
  `<N>` starts with `0`, so the first argument is `pytest.source.0`, the second `pytest.source.1`, etc. 


# License and Copyright

```
Copyright DataStax, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
