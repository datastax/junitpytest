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
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;

public class TestClassDescriptor extends AbstractTestDescriptor
{
    public static final String SEGMENT_TYPE = "class";

    private List<Predicate<String>> filters = new ArrayList<>();
    private final PytestClassInfo info;
    private TestExecutionResult lastCaseResult;

    public static UniqueId idForClass(TestDescriptor parent, String fullyQualifiedClassName)
    {
        return parent.getUniqueId().append(SEGMENT_TYPE, fullyQualifiedClassName);
    }

    public static TestClassDescriptor createChild(TestDescriptor parent, PytestClassInfo info)
    {
        return new TestClassDescriptor(idForClass(parent, info.getFullyQualifiedClassName()), info);
    }

    public TestClassDescriptor(UniqueId uniqueId, PytestClassInfo info)
    {
        super(uniqueId, info.getFullyQualifiedClassName(), ClassSource.from(info.getFullyQualifiedClassName()));
        this.info = info;
    }

    @Override
    public String getLegacyReportingName()
    {
        return info.getFullyQualifiedClassName();
    }

    @Override
    public boolean mayRegisterTests()
    {
        return true;
    }

    @Override
    public Type getType()
    {
        return Type.CONTAINER;
    }

    public PytestClassInfo getInfo()
    {
        return info;
    }

    public Optional<List<Predicate<String>>> getFilters()
    {
        return Optional.ofNullable(filters);
    }

    public void clearFilters()
    {
        this.filters = null;
    }

    public boolean acceptsMethod(String method)
    {
        if (filters != null && !filters.isEmpty())
        {
            return filters.stream()
                          .anyMatch(f -> f.test(method));
        }
        return true;
    }

    public boolean runWholeClass()
    {
        return info.getTestCount() == getChildren().size() &&
               info.getMethodNames()
                   .stream()
                   .flatMap(m -> info.testsForMethod(m).stream())
                   .allMatch(testName -> findByUniqueId(getUniqueId().append(TestCaseDescriptor.SEGMENT_TYPE, testName)).isPresent());
    }

    public String toPytestArgument()
    {
        return info.getFile() + "::" + info.getSimpleClassName();
    }

    public TestExecutionResult lastCaseResult()
    {
        if (lastCaseResult != null)
        {
            switch (lastCaseResult.getStatus())
            {
                case ABORTED:
                    return TestExecutionResult.aborted(new Exception("test aborted"));
                case FAILED:
                case SUCCESSFUL:
                    return TestExecutionResult.successful();
            }
        }
        return TestExecutionResult.successful();
    }

    public void setLastCaseResult(TestExecutionResult lastCaseResult)
    {
        this.lastCaseResult = lastCaseResult;
    }

    public String getTestClass()
    {
        return info.getFullyQualifiedClassName();
    }
}
