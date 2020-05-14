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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.platform.engine.ConfigurationParameters;

class Testenv
{
    private final Path venvDir;
    private final Path frozenRequirements;
    private final Path pytestOutputs;
    private final Path workingDirectory;
    private final String[] virtualenvExec;
    private final String[] pythonExec;
    private final boolean verbose;
    private final boolean debug;
    private final boolean keepOutputForPassed;
    private final List<String> pipOptions;
    private final Map<String, String> pipEnv;
    private final List<String> pytestOptions;
    private final Map<String, String> pytestEnv;
    private final Map<String, String> sourceRequirements;

    Testenv(ConfigurationParameters configurationParameters)
    {
        Function<String, String> config = key -> configurationParameters.get(key).orElseThrow(() -> new IllegalArgumentException("Required system property '" + key + "' not present"));
        Function<String, Path> pathConfig = key -> Paths.get(config.apply(key));

        this.verbose = "true".equalsIgnoreCase(configurationParameters.get("pytest.verbose").orElse("false"));
        this.debug = "true".equalsIgnoreCase(configurationParameters.get("pytest.debug").orElse("false"));
        this.keepOutputForPassed = "true".equalsIgnoreCase(configurationParameters.get("pytest.keepOutputForPassed").orElse("false"));

        this.venvDir = pathConfig.apply("pytest.venv");
        this.frozenRequirements = pathConfig.apply("pytest.frozenRequirements");
        this.pytestOutputs = pathConfig.apply("pytest.pytestOutputs");
        this.workingDirectory = pathConfig.apply("pytest.cwd");

        this.virtualenvExec = config.apply("pytest.exec.virtualenv").split(",");
        this.pythonExec = config.apply("pytest.exec.python").split(",");

        this.pytestOptions = extractArgsFromConfig(configurationParameters, "pytest.option");
        this.pytestEnv = extractMapFromConfig(configurationParameters, "pytest.env");
        this.pipOptions = extractArgsFromConfig(configurationParameters, "pytest.pip.option");
        this.pipEnv = extractMapFromConfig(configurationParameters, "pytest.pip.env");

        this.sourceRequirements = extractMapFromConfig(configurationParameters, "pytest.source");
    }

    private static Map<String, String> extractMapFromConfig(ConfigurationParameters config, String prefix)
    {
        Map<String, String> env = new LinkedHashMap<>();
        for (String kv : extractArgsFromConfig(config, prefix))
        {
            int i = kv.indexOf('=');
            env.put(kv.substring(0, i), kv.substring(i + 1));
        }
        return env;
    }

    private static List<String> extractArgsFromConfig(ConfigurationParameters config, String prefix)
    {
        List<String> sb = new ArrayList<>();
        for (int i = 0; ; i++)
        {
            Optional<String> val = config.get(prefix + "." + i);
            if (!val.isPresent())
                break;
            sb.add(val.get());
        }
        return sb;
    }

    Path getVenvDir()
    {
        return venvDir;
    }

    Path getFrozenRequirements()
    {
        return frozenRequirements;
    }

    Path getPytestOutputs()
    {
        return pytestOutputs;
    }

    Path getWorkingDirectory()
    {
        return workingDirectory;
    }

    Path getVenvBinDir()
    {
        return venvDir.resolve("bin");
    }

    String[] getVirtualenvExec()
    {
        return virtualenvExec;
    }

    String[] getPythonExec()
    {
        return pythonExec;
    }

    boolean isVerbose()
    {
        return verbose || debug;
    }

    boolean isDebug()
    {
        return debug;
    }

    boolean isKeepOutputForPassed()
    {
        return keepOutputForPassed;
    }

    List<String> getPipOptions()
    {
        return pipOptions;
    }

    Map<String, String> getPipEnv()
    {
        return pipEnv;
    }

    List<String> getPytestOptions()
    {
        return pytestOptions;
    }

    Map<String, String> getPytestEnv()
    {
        return pytestEnv;
    }

    Map<String, String> getSourceRequirements()
    {
        return sourceRequirements;
    }
}
