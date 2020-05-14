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

import java.io.PrintStream;

import com.datastax.junitpytest.common.IOUtil;
import com.datastax.junitpytest.engine.exceptions.PytestSkippedException;
import com.datastax.junitpytest.engine.exceptions.PytestUnknownException;
import org.junit.platform.engine.TestExecutionResult;

class LogFinishMessage extends Message
{
    @Override
    void execute(TestHandler testHandler)
    {
        String resultCategory = block("result_category");
        String resultWord = block("result_word");

        boolean passed = "passed".equals(resultCategory); // note: resultCategory can be null

        String nodeid = block("nodeid");
        String fspath = block("fspath");
        String lineNum = block("line_number");
        String domain = block("domain");

        if (!passed || testHandler.testenv.isKeepOutputForPassed())
        {
            // Only keep the output of passed tests, if instructed to do so.

            maybePrint(System.out, "buffered_setup");
            maybePrint(System.out, "Captured stdout setup");
            maybePrint(System.err, "Captured stderr setup");
            maybePrint(System.out, "Captured log setup");

            maybePrint(System.out, "buffered_call");
            maybePrint(System.out, "Captured stdout call");
            maybePrint(System.err, "Captured stderr call");
            maybePrint(System.out, "Captured log call");

            maybePrint(System.out, "buffered_teardown");
            maybePrint(System.out, "Captured stdout teardown");
            maybePrint(System.err, "Captured stderr teardown");
            maybePrint(System.out, "Captured log teardown");

            String outputs = block("outputs");
            if (outputs != null)
            {
                String testClass = testHandler.currentTestClass();
                String testCase = testHandler.currentTestCase();
                IOUtil.copyOuptuts(testHandler.testenv.getPytestOutputs(), testHandler.testenv.getWorkingDirectory(), outputs, testClass, testCase);
            }
        }

        // blockHeader("longrepr_fspath");
        // blockHeader("longrepr_line_number");
        String longreprMsg = block("longrepr_msg", "");

        if (resultCategory == null)
        {
            // pytest, especially with the 'flaky' plugin, may report a runtest_logfinish *without* a result.
            // In this case, assume that the test hasn't finished and a follow-up runtest_logstart for the _same_
            // test will arrive. (A runtest_logstart for the current test is effectively a noop in this engine impl.)
            //
            // Note: if the same test repeated, the engine does detect it and register a dynamic test.

            if (testHandler.testenv.isVerbose())
                System.err.println(String.format("Got pytest/runtest_logfinish with no result, expecting followup messages (not finishing): '%s', '%s', '%s', '%s'",
                                                 nodeid, fspath, lineNum, domain));
        }
        else
        {
            if (testHandler.testenv.isDebug())
                System.err.println(String.format("pytest/runtest_logfinish: '%s', '%s', '%s', '%s', '%s', '%s'",
                                                 nodeid, fspath, lineNum, domain, resultCategory, resultWord));

            switch (resultCategory)
            {
                case "passed":
                    testHandler.executionFinished(TestExecutionResult.successful());
                    break;
                case "skipped":
                    // Must not report using org.junit.platform.engine.EngineExecutionListener.executionSkipped(),
                    // because the contract for executionSkipped() defines that executionStarted() MUST NOT be called,
                    // but it will always be called and in Python tests, a test can be reported as "skipped" from a
                    // running test.
                    // This is not a huge deal w/ Gradle, as Gradle handles "skipped" as "aborted" anyway.
                    testHandler.executionFinished(TestExecutionResult.aborted(new PytestSkippedException(longreprMsg)));
                    break;
                case "error":
                case "failed":
                    block("excinfo_when");
                    block("excinfo_native");
                    String traceback = block("excinfo_traceback");
                    String excinfoLong = block("excinfo_long");
                    String msg = block("excinfo_msg");
                    int lineNumber = Integer.parseInt(block("excinfo_line_number", "0"));
                    String fileName = block("excinfo_path");

                    testHandler.testCaseFailed(msg, fileName, lineNumber, traceback);
                    break;
                default:
                    System.err.println("Got unknown result category '" + resultCategory + "', excinfo_msg='" + block("excinfo_msg") + "', result_word='" + block("result_word") + "'");
                    testHandler.executionFinished(TestExecutionResult.failed(new PytestUnknownException("Unknown result category " + resultCategory + " / " + resultWord)));
                    break;
            }
        }
    }

    private void maybePrint(PrintStream out, String key)
    {
        String msg = block(key);
        if (msg == null || msg.isEmpty())
            return;

        int linelen = 150;
        int keylen = key.length();
        int leftsep = (linelen - keylen) / 2;
        if (leftsep > 1)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < leftsep - 1; i++)
                sb.append('=');
            sb.append(' ');
            sb.append(key);
            sb.append(' ');
            while (sb.length() < linelen)
                sb.append('=');
            out.println(sb);
        }
        else
        {
            out.println("=== " + key);
        }

        out.println(msg);
    }
}
