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
package com.datastax.junitpytest.engine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import com.datastax.junitpytest.common.PytestVersion;
import com.datastax.junitpytest.engine.discovery.PytestDiscoverer;
import com.datastax.junitpytest.engine.execution.PytestExecutor;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;

public class PytestEngine implements TestEngine
{
    static final String ENGINE_ID = "pytest";

    @Override
    public String getId()
    {
        return ENGINE_ID;
    }

    @Override
    public Optional<String> getGroupId()
    {
        return Optional.of(PytestVersion.get().getGroupId());
    }

    @Override
    public Optional<String> getArtifactId()
    {
        return Optional.of(PytestVersion.get().getArtifactId());
    }

    @Override
    public Optional<String> getVersion()
    {
        return Optional.of(PytestVersion.get().getVersion());
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId)
    {
        boolean debug = isDebug(discoveryRequest.getConfigurationParameters());

        if (debug)
        {
            Class<? extends EngineDiscoveryRequest> c = discoveryRequest.getClass();
            for (String f : Arrays.asList("selectors", "engineFilters", "discoveryFilters", "postDiscoveryFilters"))
            {
                try
                {
                    Field fld = c.getDeclaredField(f);
                    fld.setAccessible(true);
                    Object v = fld.get(discoveryRequest);
                    System.err.println(fld.toString() + " = " + v);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            new Exception("Discover stacktrace (THIS IS NOT AN ERROR!)").printStackTrace(System.err);
        }

        String collectedTestsFile = System.getProperty("pytest.collectedTestsFile");
        if (collectedTestsFile == null)
            throw new IllegalArgumentException("Missing system property 'pytest.collectedTestsFile'");
        PytestDiscoverer discoverer = new PytestDiscoverer();
        discoverer.readCollectedTests(Paths.get(collectedTestsFile));
        TestDescriptor result = discoverer.discover(discoveryRequest, uniqueId);

        if (debug)
            dumpTests("Discover result", result);
        return result;
    }

    @Override
    public void execute(ExecutionRequest request)
    {
        boolean debug = isDebug(request.getConfigurationParameters());
        if (debug)
        {
            new Exception("Execute stacktrace (THIS IS NOT AN ERROR!)").printStackTrace(System.err);
            dumpTests("Execution request", request.getRootTestDescriptor());
        }

        new PytestExecutor(request).execute();
    }

    private boolean isDebug(ConfigurationParameters configurationParameters)
    {
        return "true".equalsIgnoreCase(configurationParameters.get("pytest.debug").orElse("false"));
    }

    private void dumpTests(String label, TestDescriptor root)
    {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw))
        {
            pw.println(label + ":");
            pw.println("  " + root);
            for (TestDescriptor clazz : root.getChildren())
            {
                pw.println("    " + clazz);
                for (TestDescriptor testCase : clazz.getChildren())
                {
                    pw.println("      " + testCase);
                }
            }
            pw.flush();
        }
        System.err.println(sw);
    }
}
