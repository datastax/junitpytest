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

import java.util.Objects;

public class PytestCollectEntry
{
    private final String file;
    private final String simpleClassName;
    private final String method;
    private final String test;
    private final String packageName;
    private final String fullyQualifiedClassName;

    public PytestCollectEntry(String file, String simpleClassName, String method, String test)
    {
        this.file = Objects.requireNonNull(file);
        this.simpleClassName = Objects.requireNonNull(simpleClassName);
        this.method = Objects.requireNonNull(method);
        this.test = Objects.requireNonNull(test);

        String packageName = file.replace('/', '.');
        if (packageName.endsWith(".py"))
            packageName = packageName.substring(0, packageName.length() - ".py".length());

        this.packageName = packageName;
        this.fullyQualifiedClassName = packageName + '.' + simpleClassName;
    }

    public static PytestCollectEntry parse(String ln)
    {
        int i = ln.indexOf("::");
        if (i == -1)
            return null;

        String pyFile = ln.substring(0, i);
        ln = ln.substring(i + 2);
        i = ln.indexOf("::");
        if (i == -1)
            return null;

        String simpleClassName = ln.substring(0, i);

        if ("cls".equals(simpleClassName))
            return null;

        ln = ln.substring(i + 2);
        i = ln.indexOf("::");
        if (i == -1)
            return null;

        String method = ln.substring(0, i);
        ln = ln.substring(i + 2);

        String test = ln;

        return new PytestCollectEntry(pyFile, simpleClassName, method, test);
    }

    public static PytestCollectEntry parseFromPytest(String nodeid, String fspath, String domain)
    {
        int i = nodeid.indexOf("::");
        if (i == -1)
            return null;

        String pyFile = nodeid.substring(0, i);
        nodeid = nodeid.substring(i + 2);
        i = nodeid.indexOf("::");
        if (i == -1)
            return null;

        String simpleClassName = nodeid.substring(0, i);
        nodeid = nodeid.substring(i + 2);
        String method = nodeid;

        i = domain.indexOf('.');
        if (i == -1)
            return null;

        String test = domain.substring(i + 1);
        return new PytestCollectEntry(pyFile, simpleClassName, method, test);
    }

    public String getFile()
    {
        return file;
    }

    public String getSimpleClassName()
    {
        return simpleClassName;
    }

    public String getTest()
    {
        return test;
    }

    public String getMethod()
    {
        return method;
    }

    public String getPackageName()
    {
        return packageName;
    }

    public String getFullyQualifiedClassName()
    {
        return fullyQualifiedClassName;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PytestCollectEntry that = (PytestCollectEntry) o;
        return file.equals(that.file) &&
               simpleClassName.equals(that.simpleClassName) &&
               test.equals(that.test);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(file, simpleClassName, test);
    }

    @Override
    public String toString()
    {
        return file + "::" + simpleClassName + "::" + method + "::" + test;
    }
}
