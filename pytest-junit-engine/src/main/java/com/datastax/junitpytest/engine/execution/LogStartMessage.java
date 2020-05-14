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

class LogStartMessage extends Message
{
    @Override
    void execute(TestHandler testHandler)
    {
        String nodeid = block("nodeid");
        String fspath = block("fspath");
        String lineNum = block("line_number");
        String domain = block("domain");
        if (testHandler.testenv.isDebug())
            System.err.println(String.format("pytest/runtest_logstart: '%s', '%s', '%s', '%s'", nodeid, fspath, lineNum, domain));
        testHandler.executionStarted(nodeid, fspath, domain);
    }
}
