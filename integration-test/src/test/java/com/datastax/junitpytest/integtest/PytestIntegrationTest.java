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
package com.datastax.junitpytest.integtest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.datastax.junitpytest.common.IOUtil;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.FAILED;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;

@TestMethodOrder(OrderAnnotation.class)
public class PytestIntegrationTest
{
    private final String projectVersion = System.getProperty("projectVersion");
    private final String projectPyVersion = System.getProperty("projectPyVersion");
    private final String rootProjectDirectory = System.getProperty("rootProject");
    private final String pythonTestProjectDirectory = System.getProperty("testProject");

    private File rootProjectDir()
    {
        return new File(rootProjectDirectory).getAbsoluteFile();
    }

    private File testProjectDir()
    {
        return new File(pythonTestProjectDirectory).getAbsoluteFile();
    }

    @Test
    @Order(1)
    public void checkPublishToMavenLocal() throws IOException
    {
        BuildResult result = GradleRunner.create()
                                         .withProjectDir(rootProjectDir())
                                         .withArguments("publishToMavenLocal", "--stacktrace", "--info", "--no-scan")
                                         .build();

        assertThat(Arrays.asList(":common:compileJava",
                                 ":common:jar",
                                 ":junit-pytest-plugin:createWheel",
                                 ":junit-pytest-plugin:jar",
                                 ":pytest-gradle-plugin:compileKotlin",
                                 ":pytest-gradle-plugin:publishToMavenLocal",
                                 ":pytest-gradle-plugin:publishPluginMavenPublicationToMavenLocal",
                                 ":pytest-gradle-plugin:publishPytestPluginMarkerMavenPublicationToMavenLocal",
                                 ":pytest-junit-engine:compileJava",
                                 ":pytest-junit-engine:shadowJar",
                                 ":pytest-junit-engine:publishToMavenLocal"))
                .extracting(result::task)
                .allMatch(Objects::nonNull)
                .extracting(BuildTask::getOutcome)
                .allMatch(outcome -> outcome == SUCCESS || outcome == UP_TO_DATE);

        checkJar(new File(rootProjectDir(), "pytest-gradle-plugin/build/libs/pytest-gradle-plugin-" + projectVersion + "-all.jar"),
                 "META-INF/gradle-plugins/com.datastax.junitpytest.properties",
                 // check that the pytest-plugin is included
                 "com/datastax/junitpytest/junit-pytest-plugin/junit_pytest_plugin.egg-info/PKG-INFO",
                 "com/datastax/junitpytest/junit-pytest-plugin/dist/junit-pytest-plugin-" + projectPyVersion + ".tar.gz",
                 "com/datastax/junitpytest/junit-pytest-plugin/dist/junit_pytest_plugin-" + projectPyVersion + "-py3-none-any.whl",
                 // check that the contents of the :common project are included in the jar
                 "com/datastax/junitpytest/common/PytestCollectEntry.class",
                 // check that the asm dependency is relocated
                 "com/datastax/junitpytest/gradleplugin/deps/org/objectweb/asm/ClassWriter.class");

        checkJar(new File(rootProjectDir(), "pytest-junit-engine/build/libs/pytest-junit-engine-" + projectVersion + ".jar"),
                 "META-INF/services/org.junit.platform.engine.TestEngine",
                 // check that the pytest-plugin is included
                 "com/datastax/junitpytest/junit-pytest-plugin/junit_pytest_plugin.egg-info/PKG-INFO",
                 "com/datastax/junitpytest/junit-pytest-plugin/dist/junit-pytest-plugin-" + projectPyVersion + ".tar.gz",
                 "com/datastax/junitpytest/junit-pytest-plugin/dist/junit_pytest_plugin-" + projectPyVersion + "-py3-none-any.whl",
                 // check that the contents of the :common project are included in the jar
                 "com/datastax/junitpytest/common/PytestCollectEntry.class");
    }

    private void checkJar(File jar, String... filesToCheck) throws IOException
    {
        Set<String> unchecked = new LinkedHashSet<>(Arrays.asList(filesToCheck));

        try (ZipFile zip = new ZipFile(jar))
        {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); )
            {
                ZipEntry entry = e.nextElement();
                unchecked.remove(entry.getName());
            }
        }

        assertThat(unchecked).describedAs(jar.getName()).isEmpty();
    }

    @Test
    @Order(2)
    public void checkPytestProjectClean() throws IOException
    {
        IOUtil.deltree(testProjectDir().toPath().resolve("build").resolve("generated"));

        checkPytestInternal();
    }

    @Test
    @Order(3)
    public void checkPytestProjectAgain() throws IOException
    {
        checkPytestInternal();
    }

    private void checkPytestInternal() throws IOException
    {
        BuildResult result = GradleRunner.create()
                                         .withProjectDir(testProjectDir())
                                         .withArguments("pytest", "--info", "--stacktrace")
                                         .buildAndFail();

        assertThat(Arrays.asList("test-project/build/generated/pytest-java/classes/test/java/extend_tests/TestExtend.class",
                                 "test-project/build/generated/pytest-java/classes/test/java/extend_tests/TestUnit.class",
                                 "test-project/build/generated/pytest-java/classes/test/java/meta_tests/TestMeta.class",
                                 "test-project/build/generated/pytest-java/classes/test/java/unit_tests/TestUnit.class")).allMatch(fn -> Files.exists(Paths.get(fn)));

        assertThat(Files.readAllLines(Paths.get("test-project/build/generated/pytest-java/classes/test/java/META-INF/pytest")))
                .contains("extend_tests.py::TestExtend::test_fail::test_fail",
                          "extend_tests.py::TestExtend::test_something::test_something",
                          "extend_tests.py::TestExtend::test_something_more::test_something_more",
                          "extend_tests.py::TestUnit::test_fail::test_fail",
                          "extend_tests.py::TestUnit::test_something::test_something",
                          "unit_tests.py::TestUnit::test_fail::test_fail",
                          "unit_tests.py::TestUnit::test_something::test_something",
                          "meta_tests.py::TestMeta::test_meta_1::test_meta_1",
                          "meta_tests.py::TestMeta::test_fail2::test_fail2",
                          "meta_tests.py::TestMeta::test_ok::test_ok",
                          "meta_tests.py::TestMeta::test_skip::test_skip",
                          "meta_tests.py::TestMeta::test_parameterized::test_parameterized[a]",
                          "meta_tests.py::TestMeta::test_parameterized::test_parameterized[b]",
                          "meta_tests.py::TestMeta::test_parameterized::test_parameterized[c]",
                          "meta_extend_tests.py::TestMeta::test_meta_1::test_meta_1",
                          "meta_extend_tests.py::TestMeta::test_fail2::test_fail2",
                          "meta_extend_tests.py::TestMeta::test_ok::test_ok",
                          "meta_extend_tests.py::TestMeta::test_skip::test_skip",
                          "meta_extend_tests.py::TestMeta::test_parameterized::test_parameterized[a]",
                          "meta_extend_tests.py::TestMeta::test_parameterized::test_parameterized[b]",
                          "meta_extend_tests.py::TestMeta::test_parameterized::test_parameterized[c]",
                          "meta_extend_tests.py::TestExtend::test_meta_1::test_meta_1",
                          "meta_extend_tests.py::TestExtend::test_fail2::test_fail2",
                          "meta_extend_tests.py::TestExtend::test_ok::test_ok",
                          "meta_extend_tests.py::TestExtend::test_skip::test_skip",
                          "meta_extend_tests.py::TestExtend::test_parameterized::test_parameterized[a]",
                          "meta_extend_tests.py::TestExtend::test_parameterized::test_parameterized[b]",
                          "meta_extend_tests.py::TestExtend::test_parameterized::test_parameterized[c]",
                          "meta_extend_tests.py::TestExtend::test_something_more::test_something_more",
                          "meta_extend_tests.py::TestExtend::test_param_extend::test_param_extend[a]",
                          "meta_extend_tests.py::TestExtend::test_param_extend::test_param_extend[b]",
                          "meta_extend_tests.py::TestExtend::test_param_extend::test_param_extend[c]");

        assertThat(result.getOutput()).contains("Successfully installed junit-pytest-plugin-" + projectPyVersion + "\n",
                                                "unit_tests.TestUnit > test_fail FAILED\n",
                                                "extend_tests.TestExtend > test_fail FAILED\n",
                                                "extend_tests.TestUnit > test_fail FAILED\n",
                                                "meta_tests.TestMeta > test_skip SKIPPED\n",
                                                "meta_tests.TestMeta > test_meta_1 FAILED\n",
                                                "meta_tests.TestMeta > test_fail2 FAILED\n",
                                                "meta_tests.TestMeta > test_parameterized[b] FAILED\n",
                                                "meta_tests.TestMeta > test_parameterized[c] SKIPPED\n",
                                                "meta_extend_tests.TestMeta > test_skip SKIPPED\n",
                                                "meta_extend_tests.TestMeta > test_meta_1 FAILED\n",
                                                "meta_extend_tests.TestMeta > test_fail2 FAILED\n",
                                                "meta_extend_tests.TestMeta > test_parameterized[b] FAILED\n",
                                                "meta_extend_tests.TestMeta > test_parameterized[c] SKIPPED\n",
                                                "meta_extend_tests.TestExtend > test_skip SKIPPED\n",
                                                "meta_extend_tests.TestExtend > test_meta_1 FAILED\n",
                                                "meta_extend_tests.TestExtend > test_fail2 FAILED\n",
                                                "meta_extend_tests.TestExtend > test_parameterized[b] FAILED\n",
                                                "meta_extend_tests.TestExtend > test_parameterized[c] SKIPPED\n",
                                                "meta_extend_tests.TestExtend > test_param_extend[b] FAILED\n",
                                                "meta_extend_tests.TestExtend > test_param_extend[c] SKIPPED\n",
                                                "32 tests completed, 13 failed, 7 skipped")
                                      .containsPattern("meta_tests[.]TestMeta > test_meta_1 FAILED\n" +
                                                       "    com[.]datastax[.]junitpytest[.]engine[.]exceptions[.]PytestCaseFailedException: Exception: balh\n" +
                                                       "        at meta_tests[.]TestMeta[.]test_meta_1[(]meta_tests[.]py:[0-9]+[)]\n")
                                      .containsPattern("meta_tests[.]TestMeta > test_fail2 FAILED\n" +
                                                       "    com[.]datastax[.]junitpytest[.]engine[.]exceptions[.]PytestCaseFailedException: Exception: fail as well\n" +
                                                       "        at meta_tests[.]TestMeta[.]nested[(]meta_tests[.]py:[0-9]+[)]\n" +
                                                       "        at meta_tests[.]TestMeta[.]test_fail2[(]meta_tests[.]py:[0-9]+[)]\n");

        assertThat(Arrays.asList(":createVirtualenv",
                                 ":discoverPytest"))
                .extracting(result::task)
                .allMatch(Objects::nonNull)
                .extracting(BuildTask::getOutcome)
                .allMatch(outcome -> outcome == SUCCESS || outcome == UP_TO_DATE);

        assertThat(Arrays.asList(":pytest"))
                .extracting(result::task)
                .allMatch(Objects::nonNull)
                .extracting(BuildTask::getOutcome)
                .allMatch(outcome -> outcome == FAILED);
    }
}
