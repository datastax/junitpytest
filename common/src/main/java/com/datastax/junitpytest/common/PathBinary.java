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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

public final class PathBinary
{
    public static <X extends Throwable> Path fileForExecutableFromPath(Supplier<X> onError, String... executables) throws X
    {
        return fileForExecutableFromPath(executables).orElseThrow(onError);
    }

    public static Optional<Path> fileForExecutableFromPath(String... executables)
    {
        return Arrays.stream(executables)
                     .map(executable -> PathBinary.fileForExecutable(executable, System.getenv("PATH")))
                     .filter(Optional::isPresent)
                     .map(Optional::get)
                     .findFirst();
    }

    public static Optional<Path> fileForExecutable(String binary, String paths)
    {
        return fileForExecutable(binary, paths.split(File.pathSeparator));
    }

    public static Optional<Path> fileForExecutable(String binary, String[] paths)
    {
        return fileForExecutable(binary, Arrays.stream(paths).map(Paths::get).toArray(Path[]::new));
    }

    public static Optional<Path> fileForExecutable(String binary, Path[] paths)
    {
        return Arrays.stream(paths)
                     .map(p -> p.resolve(binary))
                     .filter(Files::isExecutable)
                     .findFirst();
    }
}
