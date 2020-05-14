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
package com.datastax.junitpytest.engine.exceptions;

import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PytestCaseFailedException extends Exception
{
    private static final Pattern tracebackPattern = Pattern.compile("^ {2}File \".*[/\\\\]([^/\\\\]+)\", line ([0-9]+), in (.*)$");

    public PytestCaseFailedException(String message, String fileName, String declaringClass, String methodName, int lineNumber, String traceback)
    {
        super(message, null, false, true);

        if (traceback != null)
        {
            List<StackTraceElement> stacktrace = new LinkedList<>();
            try (BufferedReader br = new BufferedReader(new StringReader(traceback)))
            {
                String ln;
                while ((ln = br.readLine()) != null)
                {
                    Matcher m = tracebackPattern.matcher(ln);
                    if (m.matches())
                        stacktrace.add(0, ste(m.group(1), declaringClass, m.group(3), Integer.parseInt(m.group(2))));
                }
            }
            catch (IOException e)
            {
                throw new IOError(e);
            }
            setStackTrace(stacktrace.toArray(new StackTraceElement[0]));
        }
        else
        {
            setStackTrace(new StackTraceElement[]{
                    ste(fileName, declaringClass, methodName, lineNumber)
            });
        }
    }

    private StackTraceElement ste(String fileName, String declaringClass, String methodName, int lineNumber)
    {
        int i = fileName.lastIndexOf('/');
        fileName = i != -1 ? fileName.substring(i + 1) : fileName;
        i = fileName.lastIndexOf('\\');
        fileName = i != -1 ? fileName.substring(i + 1) : fileName;

        return new StackTraceElement(
                declaringClass,
                methodName,
                fileName,
                lineNumber
        );
    }
}
