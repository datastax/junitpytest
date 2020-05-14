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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.datastax.junitpytest.common.PytestCollectEntry;

public class PytestClassInfo
{
    private final String file;
    private final String fullyQualifiedClassName;
    private final String simpleClassName;
    private final Set<String> methodNames = new LinkedHashSet<>();
    private final Map<String, List<String>> testsPerMethod = new LinkedHashMap<>();

    public static PytestClassInfo fromCollectEntry(PytestCollectEntry collectEntry)
    {
        return new PytestClassInfo(collectEntry.getFile(), collectEntry.getFullyQualifiedClassName(), collectEntry.getSimpleClassName());
    }

    public PytestClassInfo(String file, String fullyQualifiedClassName, String simpleClassName)
    {
        this.file = file;
        this.fullyQualifiedClassName = fullyQualifiedClassName;
        this.simpleClassName = simpleClassName;
    }

    public String getFile()
    {
        return file;
    }

    public String getFullyQualifiedClassName()
    {
        return fullyQualifiedClassName;
    }

    public String getSimpleClassName()
    {
        return simpleClassName;
    }

    public Set<String> getMethodNames()
    {
        return methodNames;
    }

    public void addTest(String methodName, String testName)
    {
        methodNames.add(methodName);
        testsPerMethod.computeIfAbsent(methodName, m -> new ArrayList<>()).add(testName);
    }

    public List<String> testsForMethod(String method)
    {
        List<String> tests = testsPerMethod.get(method);
        if (tests == null)
            throw new IllegalArgumentException("Test method '" + method + "' unknown for test class '" + simpleClassName + "', known tests: " + testsPerMethod);
        return tests;
    }

    public int getTestCount()
    {
        int cnt = 0;
        for (List<String> value : testsPerMethod.values())
            cnt += value.size();
        return cnt;
    }
}
