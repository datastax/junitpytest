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
package com.datastax.junitpytest.engine.execution;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.datastax.junitpytest.common.PathBinary;
import com.datastax.junitpytest.common.ProcessRunner;
import com.datastax.junitpytest.common.PytestVersion;
import com.datastax.junitpytest.common.VirtualEnv;
import com.datastax.junitpytest.engine.TestCaseDescriptor;
import com.datastax.junitpytest.engine.TestClassDescriptor;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;

import static java.util.concurrent.TimeUnit.SECONDS;

public class PytestExecutor
{
    private final ExecutionRequest request;

    public PytestExecutor(ExecutionRequest request)
    {
        this.request = request;
    }

    public void execute()
    {
        Testenv testenv = new Testenv(request.getConfigurationParameters());

        Optional<List<String>> command = generatePytestCommand(testenv,
                                                               request.getRootTestDescriptor());

        if (!command.isPresent())
        {
            System.out.println("No tests to execute");
            return;
        }

        VirtualEnv virtualEnv = new VirtualEnv(testenv.getWorkingDirectory().toFile(),
                                               testenv.getVenvDir(),
                                               testenv.getFrozenRequirements(),
                                               PathBinary.fileForExecutableFromPath(() -> new RuntimeException("No executable found for " + Arrays.toString(testenv.getVirtualenvExec())),
                                                                                    testenv.getVirtualenvExec()).toFile(),
                                               PathBinary.fileForExecutableFromPath(() -> new RuntimeException("No executable found for " + Arrays.toString(testenv.getPythonExec())),
                                                                                    testenv.getPythonExec()).toFile(),
                                               testenv.getPipOptions(),
                                               testenv.getPipEnv());

        try
        {

            String pytestPluginFilename = "junit-pytest-plugin".replace('-', '_') +
                                          "-" + PytestVersion.get().getPyVersion() +
                                          "-py3-none-any.whl";
            String pytestPluginResource = "com/datastax/junitpytest/junit-pytest-plugin/dist/" + pytestPluginFilename;

            virtualEnv.createVenvIfNecessary();
            virtualEnv.checkFrozenRequirements(PytestExecutor.class
                                                       .getClassLoader()
                                                       .getResource(pytestPluginResource),
                                               testenv.getVenvDir().resolve(pytestPluginFilename));
            for (String sourceReq : testenv.getSourceRequirements().values())
                virtualEnv.installSourceRequirement(sourceReq);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        TestHandler testHandler = new TestHandler(request.getRootTestDescriptor(),
                                                  request.getEngineExecutionListener(),
                                                  testenv);

        StringBuilder startPytestMsg = new StringBuilder("Starting pytest with ").append(String.join(" ", command.get()));

        ProcessBuilder processBuilder = new ProcessBuilder(command.get()).directory(testenv.getWorkingDirectory().toFile());
        processBuilder.environment().putAll(testenv.getPytestEnv());
        startPytestMsg.append("\n  with configured environment: ").append(testenv.getPytestEnv());
        String newPath = testenv.getVenvBinDir() + File.pathSeparator + System.getenv("PATH");
        if (System.getenv("JAVA_HOME") == null)
        {
            String javaHome = System.getProperty("java.home");
            Path javaHomePath = Paths.get(javaHome);
            if ("jre".equals(javaHomePath.getFileName().toString()))
                javaHome = javaHomePath.getParent().toString();

            startPytestMsg.append("\n  with JAVA_HOME=").append(javaHome);
            newPath = newPath + File.pathSeparator + javaHome + File.separator + "bin";
        }
        System.out.println(startPytestMsg);
        processBuilder.environment().put("PATH", newPath);
        try
        {
            InboundHandler buffer = new InboundHandler();

            testHandler.processStart();

            Process process = processBuilder.start();
            ProcessRunner processRunner = new ProcessRunner(process, 60, SECONDS).register();
            byte[] stderrBuf = new byte[4096];
            try (BufferedInputStream input = new BufferedInputStream(process.getInputStream()); InputStream error = process.getErrorStream())
            {
                while (true)
                {
                    int av = error.available();
                    if (av > 0)
                    {
                        int rd = Math.min(av, stderrBuf.length);
                        rd = error.read(stderrBuf, 0, rd);
                        if (rd > 0)
                            System.err.write(stderrBuf, 0, rd);
                    }

                    Message message = buffer.readMessage(input);
                    if (message != null)
                    {
                        message.execute(testHandler);
                        continue;
                    }

                    if (error.available() > 0 || input.available() > 0)
                        continue;

                    if (process.isAlive())
                    {
                        Thread.sleep(1L);
                        continue;
                    }

                    try
                    {
                        int exitCode = process.exitValue(); // TODO maybe evaluate the exit-code
                        System.out.println("pytest finished with exit code " + exitCode);
                        break;
                    }
                    catch (IllegalThreadStateException e)
                    {
                        // still running
                    }
                }
            }
            finally
            {
                processRunner.stop();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            testHandler.failure(e);
            throw new RuntimeException(e);
        }
        finally
        {
            testHandler.processFinished();
            System.out.println("pytest done");
        }
    }

    static Optional<List<String>> generatePytestCommand(Testenv testenv, TestDescriptor desc)
    {
        List<String> command = new ArrayList<>();
        command.add(testenv.getVenvBinDir().resolve("pytest").toString());
        command.add("--gradle");
        command.addAll(testenv.getPytestOptions());

        int sz = command.size();

        // Collect the tests to actually execute.
        // The operation may add no tests at all, which is fine, but `pytest` must not be run in that
        // case, because running `pytest` without arguments (usually) means to run all tests, which is
        // not intended.
        desc.getChildren()
            .stream()
            .filter(TestClassDescriptor.class::isInstance)
            .map(TestClassDescriptor.class::cast)
            .flatMap(testClassDescriptor -> {
                if (testClassDescriptor.runWholeClass())
                    return Stream.of(testClassDescriptor.toPytestArgument());
                return testClassDescriptor.getChildren()
                                          .stream()
                                          .filter(TestCaseDescriptor.class::isInstance)
                                          .map(TestCaseDescriptor.class::cast)
                                          .map(TestCaseDescriptor::toPytestArgument);
            })
            .forEach(command::add);

        return command.size() == sz ? Optional.empty() : Optional.of(command);
    }
}
