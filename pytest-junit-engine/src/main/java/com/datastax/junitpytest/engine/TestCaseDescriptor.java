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

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;

public class TestCaseDescriptor extends AbstractTestDescriptor
{
    public static final String SEGMENT_TYPE = "case";

    private final String test;
    private final String pytestArgument;

    public static UniqueId idForCase(TestClassDescriptor parent, String test)
    {
        return parent.getUniqueId().append(TestCaseDescriptor.SEGMENT_TYPE, test);
    }

    public static TestCaseDescriptor createChild(TestClassDescriptor parent, String test)
    {
        return new TestCaseDescriptor(idForCase(parent, test), test, parent);
    }

    public TestCaseDescriptor(UniqueId uniqueId, String test, TestClassDescriptor classDescriptor)
    {
        super(uniqueId, test, MethodSource.from(classDescriptor.getTestClass(), test));
        this.test = test;
        this.pytestArgument = classDescriptor.toPytestArgument() + "::" + test;
    }

    @Override
    public String getLegacyReportingName()
    {
        return test;
    }

    @Override
    public boolean mayRegisterTests()
    {
        return true;
    }

    @Override
    public Type getType()
    {
        return Type.TEST;
    }

    public TestClassDescriptor getParentClass()
    {
        return getParent().filter(TestClassDescriptor.class::isInstance)
                          .map(TestClassDescriptor.class::cast)
                          .orElseThrow(IllegalStateException::new);
    }

    public String toPytestArgument()
    {
        return pytestArgument;
    }

    public String getTest()
    {
        return test;
    }

    public String getMethodName()
    {
        int i = test.indexOf('[');
        return i == -1 ? test : test.substring(0, i);
    }
}
