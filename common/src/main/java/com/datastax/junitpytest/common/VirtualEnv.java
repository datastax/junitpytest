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
package com.datastax.junitpytest.common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class VirtualEnv
{
    private final File sourceDir;
    private final Path venvDir;
    private final Path frozenRequirementsTxt;
    private final File virtualenvExecutable;
    private final File pythonExecutable;
    private final List<String> pipOptions;
    private final Map<String, String> pipEnv;

    public VirtualEnv(File sourceDir,
                      Path venvDir,
                      Path frozenRequirementsTxt,
                      File virtualenvExecutable,
                      File pythonExecutable,
                      List<String> pipOptions,
                      Map<String, String> pipEnv)
    {
        this.sourceDir = sourceDir;
        this.venvDir = venvDir;
        this.frozenRequirementsTxt = frozenRequirementsTxt;
        this.virtualenvExecutable = virtualenvExecutable;
        this.pythonExecutable = pythonExecutable;
        this.pipOptions = pipOptions;
        this.pipEnv = pipEnv;
    }

    public void createVenvIfNecessary() throws IOException
    {
        Path venvBinDir = venvDir.resolve("bin");
        boolean venvExists =
                Files.isDirectory(venvBinDir) &&
                Files.isDirectory(venvDir.resolve("lib")) &&
                Files.isRegularFile(venvDir.resolve("pyvenv.cfg")) &&
                Files.isRegularFile(venvDir.resolve("src").resolve("pip-delete-this-directory.txt")) &&
                Files.isRegularFile(venvBinDir.resolve("activate")) &&
                Files.isExecutable(venvBinDir.resolve("python")) &&
                Files.isExecutable(venvBinDir.resolve("pip"));
        if (!venvExists)
        {
            IOUtil.deltree(venvDir);
            Files.createDirectories(venvDir);

            List<String> command = Arrays.asList(virtualenvExecutable.getAbsolutePath(),
                                                 "--python",
                                                 pythonExecutable.getAbsolutePath(),
                                                 venvDir.toAbsolutePath().toString());

            System.out.println("Starting " + String.join(" ", command));

            Process process = new ProcessBuilder().directory(sourceDir)
                                                  .command(command).start();
            ProcessRunner pr = new ProcessRunner(process, 10, TimeUnit.SECONDS);
            pr.await(2, TimeUnit.MINUTES).assertExitCode();
        }
    }

    public void checkFrozenRequirements(URL pytestPluginResource, Path pytestPluginArchiveFile) throws IOException
    {
        pytestPluginArchiveFile = pytestPluginArchiveFile.toAbsolutePath();

        URLConnection conn = pytestPluginResource.openConnection();
        try (InputStream in = conn.getInputStream())
        {
            Files.copy(in, pytestPluginArchiveFile, StandardCopyOption.REPLACE_EXISTING);
        }

        String modifiedPath = modifiedPathEnv();

        installPytestPlugin(pytestPluginArchiveFile);

        String installed = getInstalled(modifiedPath);
        String expected = new String(Files.readAllBytes(frozenRequirementsTxt), StandardCharsets.UTF_8);
        if (!installed.equals(expected))
            installRequirements(frozenRequirementsTxt.toAbsolutePath().toString());
    }

    private String getInstalled(String modifiedPath) throws IOException
    {
        ProcessBuilder pb = new ProcessBuilder().directory(venvDir.toFile())
                                                .command(venvBin().resolve("pip").toString(),
                                                         "freeze");

        System.out.println("Starting " + String.join(" ", pb.command()));

        pb.environment().put("PATH", modifiedPath);
        Process proc = pb.start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new ProcessRunner(proc, 5, TimeUnit.SECONDS).withCapturedStdout(buffer)
                                                    .await(2, TimeUnit.MINUTES)
                                                    .assertExitCode();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    public void installPytestPlugin(Path pytestPluginArchiveFile) throws IOException
    {
        List<String> installCommand = new ArrayList<>();
        installCommand.add(venvBin().resolve("pip").toString());
        installCommand.add("install");
        if (PytestVersion.get().getVersion().endsWith("-SNAPSHOT"))
            installCommand.add("--upgrade"); // only need to "overwrite" the installation, if the current version is a snapshot
        installCommand.add(pytestPluginArchiveFile.toString());
        ProcessBuilder pb = new ProcessBuilder().directory(venvDir.toFile())
                                                .command(installCommand);

        System.out.println("Starting " + String.join(" ", pb.command()));

        pb.environment().put("PATH", modifiedPathEnv());
        Process proc = pb.start();
        new ProcessRunner(proc, 5, TimeUnit.SECONDS).await(2, TimeUnit.MINUTES)
                                                    .assertExitCode();
    }

    public void installRequirements(String requirementsTxt) throws IOException
    {
        List<String> command = new ArrayList<>();
        command.add(venvBin().resolve("pip").toString());
        command.add("install");
        command.addAll(pipOptions);
        command.add("--requirement");
        command.add(requirementsTxt);

        Path venvBin = venvBin();
        String modifiedPath = venvBin.toString() + File.pathSeparator + System.getenv("PATH");

        ProcessBuilder pb = new ProcessBuilder().directory(venvDir.toFile())
                                                .command(command);

        System.out.println("Starting " + String.join(" ", pb.command()));

        pb.environment().putAll(pipEnv);
        pb.environment().put("PATH", modifiedPath);
        Process proc = pb.start();
        new ProcessRunner(proc, 5, TimeUnit.SECONDS).await(30, TimeUnit.MINUTES)
                                                    .assertExitCode();
    }

    public void installSourceRequirement(String sourceReq) throws IOException
    {
        Path venvBin = venvBin();
        String modifiedPath = venvBin.toString() + File.pathSeparator + System.getenv("PATH");

        ProcessBuilder pb = new ProcessBuilder().directory(venvDir.toFile())
                                                .command(venvBin.resolve("pip").toString(),
                                                         "install",
                                                         "--editable",
                                                         sourceReq);

        System.out.println("Starting " + String.join(" ", pb.command()));

        pb.environment().putAll(pipEnv);
        pb.environment().put("PATH", modifiedPath);
        Process proc = pb.start();
        new ProcessRunner(proc, 5, TimeUnit.SECONDS).await(2, TimeUnit.MINUTES)
                                                    .assertExitCode();
    }

    public String modifiedPathEnv()
    {
        return venvBin().toString() + File.pathSeparator + System.getenv("PATH");
    }

    private Path venvBin()
    {
        return venvDir.resolve("bin").toAbsolutePath();
    }
}
