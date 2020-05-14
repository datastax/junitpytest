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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IOUtil
{
    public static void copyOuptuts(Path pytestOutputs, Path workingDirectory, String outputs, String testClass, String testCase)
    {
        Path pytestOutput = pytestOutputs.resolve(testClass).resolve(testCase);
        deltree(pytestOutput);
        for (String output : outputs.split("\n"))
        {
            try
            {
                Path source = workingDirectory.resolve(output);
                if (Files.isDirectory(source))
                {
                    for (Path src : listDirectory(source))
                        copyOutput(pytestOutput, src);
                }
                else
                {
                    copyOutput(pytestOutput, source);
                }
            }
            catch (IOException e)
            {
                // Just log a copy failure and hope that the user will look into the logs
                e.printStackTrace();
            }
        }
    }

    public static void deltree(Path path)
    {
        try
        {
            if (Files.isDirectory(path))
            {
                for (Path ch : listDirectory(path))
                    deltree(ch);
            }
            Files.deleteIfExists(path);
        }
        catch (IOException e)
        {
            // Just log a copy failure and hope that the user will look into the logs
            e.printStackTrace();
        }
    }

    public static void copyOutput(Path target, Path source) throws IOException
    {
        Path dest = target.resolve(source.getFileName().toString());
        if (Files.isDirectory(source))
        {
            List<Path> dirContent = listDirectory(source);
            for (Path c : dirContent)
                copyOutput(dest, c);
        }
        else
        {
            Path dir = dest.getParent();
            if (!Files.isDirectory(dir))
                Files.createDirectories(dir);
            Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static List<Path> listDirectory(Path source) throws IOException
    {
        List<Path> dirContent;
        try (Stream<Path> content = Files.list(source))
        {
            dirContent = content.collect(Collectors.toList());
        }
        return dirContent;
    }
}
