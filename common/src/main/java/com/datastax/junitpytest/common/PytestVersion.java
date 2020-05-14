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

import java.io.BufferedInputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

public class PytestVersion
{
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String pyVersion;

    public static PytestVersion get()
    {
        return DefaultPytestVersion.instance;
    }

    public PytestVersion()
    {
        this(Thread.currentThread().getContextClassLoader().getResource("com/datastax/junitpytest/version.properties"));
    }

    public PytestVersion(URL resource)
    {
        Properties props = loadProperties(resource);
        this.artifactId = props.getProperty("artifactId");
        this.groupId = props.getProperty("groupId");
        this.version = props.getProperty("version");
        this.pyVersion = props.getProperty("pyVersion");
    }

    public String getGroupId()
    {
        return groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getVersion()
    {
        return version;
    }

    public String getPyVersion()
    {
        return pyVersion;
    }

    private Properties loadProperties(URL resource)
    {
        Properties props = new Properties();
        try
        {
            URLConnection conn = resource.openConnection();
            try (InputStream in = conn.getInputStream())
            {
                props.load(new BufferedInputStream(in));
            }
        }
        catch (IOException e)
        {
            throw new IOError(e);
        }
        return props;
    }

    private static final class DefaultPytestVersion
    {
        static PytestVersion instance = new PytestVersion();
    }
}
