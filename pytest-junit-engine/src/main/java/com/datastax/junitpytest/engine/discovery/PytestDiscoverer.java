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
package com.datastax.junitpytest.engine.discovery;

import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.datastax.junitpytest.common.PytestCollectEntry;
import com.datastax.junitpytest.engine.PytestClassInfo;
import com.datastax.junitpytest.engine.RootDescriptor;
import com.datastax.junitpytest.engine.TestCaseDescriptor;
import com.datastax.junitpytest.engine.TestClassDescriptor;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver;

public class PytestDiscoverer
{
    private final Map<String, PytestClassInfo> classInfos = new LinkedHashMap<>();

    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId)
    {
        RootDescriptor engineDescriptor = new RootDescriptor(uniqueId);

        EngineDiscoveryRequestResolver<TestDescriptor> resolver =
                EngineDiscoveryRequestResolver.builder()
                                              .addClassContainerSelectorResolver(cls -> acceptsTestClass(cls.getName()))
                                              .addSelectorResolver(context -> new ClassSelectorResolver(this, context))
                                              .addSelectorResolver(new MethodSelectorResolver())
                                              .build();

        resolver.resolve(discoveryRequest, engineDescriptor);

        engineDescriptor.getChildren().stream()
                        .filter(TestClassDescriptor.class::isInstance)
                        .map(TestClassDescriptor.class::cast)
                        .forEach(this::applyFiltersAndCreateTestCases);

        return engineDescriptor;
    }

    private void applyFiltersAndCreateTestCases(TestClassDescriptor classDescriptor)
    {
        String className = classDescriptor.getUniqueId().getLastSegment().getValue();
        PytestClassInfo classInfo = classInfos.get(className);
        if (classInfo == null)
            throw new NullPointerException("No PytestClassInfo for " + className);
        classInfo.getMethodNames()
                 .stream()
                 // Filter method names
                 .filter(classDescriptor::acceptsMethod)
                 // Add children for all _test_ names
                 .flatMap(m -> classInfo.testsForMethod(m).stream())
                 .forEach(test -> classDescriptor.addChild(TestCaseDescriptor.createChild(classDescriptor, test)));
        classDescriptor.clearFilters();
    }

    boolean acceptsTestClass(String className)
    {
        return classInfos.containsKey(className);
    }

    public void readCollectedTests(Path collectedTestsFile)
    {
        try (BufferedReader br = Files.newBufferedReader(collectedTestsFile))
        {
            String ln;
            while ((ln = br.readLine()) != null)
            {
                PytestCollectEntry entry = PytestCollectEntry.parse(ln);
                if (entry == null)
                    continue;

                PytestClassInfo classInfo = classInfos.computeIfAbsent(entry.getFullyQualifiedClassName(),
                                                                       c -> PytestClassInfo.fromCollectEntry(entry));

                String test = entry.getTest();

                String method = test;
                int i = method.indexOf('[');
                if (i != -1)
                    method = method.substring(0, i);

                classInfo.addTest(method, test);
            }
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
    }

    PytestClassInfo testClassInfo(String testClass)
    {
        return classInfos.get(testClass);
    }
}
