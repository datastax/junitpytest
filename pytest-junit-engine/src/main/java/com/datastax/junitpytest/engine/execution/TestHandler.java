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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.datastax.junitpytest.common.PytestCollectEntry;
import com.datastax.junitpytest.engine.PytestClassInfo;
import com.datastax.junitpytest.engine.TestCaseDescriptor;
import com.datastax.junitpytest.engine.TestClassDescriptor;
import com.datastax.junitpytest.engine.exceptions.PytestCaseFailedException;
import com.datastax.junitpytest.engine.exceptions.PytestNoResultException;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.reporting.ReportEntry;

final class TestHandler
{
    private final List<TestDescriptor> runningTests = new ArrayList<>();
    private final TestDescriptor rootTestDescriptor;
    private final EngineExecutionListener executionListener;
    final Testenv testenv;
    String internalError;
    final Map<UniqueId, Integer> finished = new HashMap<>();

    TestHandler(TestDescriptor rootTestDescriptor, EngineExecutionListener executionListener, Testenv testenv)
    {
        this.rootTestDescriptor = rootTestDescriptor;
        this.executionListener = executionListener;
        this.testenv = testenv;
    }

    /**
     * Called when {@code pytest} is being started.
     */
    void processStart()
    {
        logVerbose("TestHandler.processStart");

        onExecutionStarted(rootTestDescriptor);
    }

    /**
     * Called when {@code pytest} has exited.
     */
    void processFinished()
    {
        logVerbose("TestHandler.processFinished");

        // Report a failure (only propagated, when there are "running" test-descriptors)
        failure(PytestNoResultException.instance);
    }

    /**
     * Called for errors around {@code pytest} process handling.
     */
    void failure(Exception e)
    {
        logVerbose("TestHandler.failure: %s", e);

        sessionFinished(TestExecutionResult.failed(e));
    }

    /**
     * Called when the test session has been reported by {@code pytest} to be started.
     */
    void sessionStarted()
    {
        logVerbose("TestHandler.sessionStarted");
    }

    /**
     * Called when the test session has been reported by {@code pytest} to be finished.
     */
    void sessionFinished(TestExecutionResult result)
    {
        logVerbose("TestHandler.sessionFinished: %s (running: %d)", result, runningTests.size());

        if (runningTests.size() == 3)
        {
            doExecutionFinished(result, 2);
            result = TestExecutionResult.successful();
        }
        if (runningTests.size() == 2)
        {
            doExecutionFinished(result, 1);
            result = TestExecutionResult.successful();
        }
        if (!runningTests.isEmpty())
        {
            doExecutionFinished(result, 0);
            runningTests.clear();
        }
    }

    void reportEntry(ReportEntry reportEntry)
    {
        if (runningTests.size() > 1)
        {
            doReportEntry(reportEntry);
        }
    }

    /**
     * Called when a test case is being started.
     */
    void executionStarted(String nodeid, String fspath, String domain)
    {
        logVerbose("TestHandler.executionStarted: %s / %s / %s", nodeid, fspath, domain);

        PytestCollectEntry entry = PytestCollectEntry.parseFromPytest(nodeid, fspath, domain);
        if (entry == null)
            throw new IllegalArgumentException("Invalid nodeid/fspath/domain from pytest: " + nodeid + " / " + fspath + " / " + domain);

        TestClassDescriptor testClass = rootTestDescriptor.findByUniqueId(TestClassDescriptor.idForClass(rootTestDescriptor, entry.getFullyQualifiedClassName()))
                                                          .map(TestClassDescriptor.class::cast)
                                                          .orElseGet(() -> registerTestDescriptor(TestClassDescriptor.createChild(rootTestDescriptor,
                                                                                                                                  PytestClassInfo.fromCollectEntry(entry)),
                                                                                                  rootTestDescriptor));

        TestCaseDescriptor testCase = testClass.findByUniqueId(TestCaseDescriptor.idForCase(testClass, entry.getTest()))
                                               .map(TestCaseDescriptor.class::cast)
                                               .orElseGet(() -> registerTestDescriptor(TestCaseDescriptor.createChild(testClass, entry.getTest()),
                                                                                       testClass));

        if (runningTests.size() == 3)
        {
            TestDescriptor currentCase = runningTests.get(2);
            if (!currentCase.getUniqueId().equals(testCase.getUniqueId()))
                doExecutionFinished(TestExecutionResult.aborted(new Exception("No test result received")), 2);
        }
        else if (runningTests.size() == 2)
        {
            TestClassDescriptor currentClass = (TestClassDescriptor) runningTests.get(1);
            if (!currentClass.getUniqueId().equals(testClass.getUniqueId()))
            {
                TestExecutionResult result = currentClass.lastCaseResult();
                doExecutionFinished(result, 1);
            }
        }

        if (runningTests.size() == 1)
            onExecutionStarted(testClass);
        if (runningTests.size() == 2)
            onExecutionStarted(testCase);
    }

    /**
     * Called for a finished test case
     */
    void executionFinished(TestExecutionResult result)
    {
        logVerbose("TestHandler.executionFinished: %s", result);

        if (runningTests.size() == 3)
        {
            doExecutionFinished(result, 2);
            TestClassDescriptor classDescriptor = (TestClassDescriptor) runningTests.get(1);
            classDescriptor.setLastCaseResult(result);
        }
    }

    /**
     * Called for a failed test case
     */
    void testCaseFailed(String msg, String fileName, int lineNumber, String traceback)
    {
        logVerbose("TestHandler.testCaseFailed: %s:%d --> %s", fileName, lineNumber, msg);

        TestClassDescriptor classDescriptor = (TestClassDescriptor) runningTests.get(1);
        TestCaseDescriptor caseDescriptor = (TestCaseDescriptor) runningTests.get(2);
        PytestCaseFailedException caseException = new PytestCaseFailedException(msg,
                                                                                fileName,
                                                                                classDescriptor.getTestClass(),
                                                                                caseDescriptor.getMethodName(),
                                                                                lineNumber,
                                                                                traceback);
        executionFinished(TestExecutionResult.failed(caseException));
    }

    String currentTestClass()
    {
        if (runningTests.size() < 2)
            throw new IllegalStateException("No current test class");
        TestClassDescriptor classDescriptor = (TestClassDescriptor) runningTests.get(1);
        return classDescriptor.getTestClass();
    }

    String currentTestCase()
    {
        if (runningTests.size() < 3)
            throw new IllegalStateException("No current test case");
        TestCaseDescriptor caseDescriptor = (TestCaseDescriptor) runningTests.get(2);
        return caseDescriptor.getTest();
    }

    private <P extends TestDescriptor, C extends TestDescriptor> C registerTestDescriptor(C child, P parent)
    {
        logVerbose("TestHandler.registerTestDescriptor: %s", child.getUniqueId());

        parent.addChild(child);
        executionListener.dynamicTestRegistered(child);
        return child;
    }

    private void onExecutionStarted(TestDescriptor testDescriptor)
    {
        logVerbose("TestHandler.onExecutionStarted: %s", testDescriptor.getUniqueId());

        // Check if the test was already run before. If that's the case, register a new, dynamic test with
        // a different test name (append '-pytest-rerun-#' to the test name).
        int rerun = finished.getOrDefault(testDescriptor.getUniqueId(), 0);
        if (rerun > 0)
        {
            System.err.println("Detected re-run #" + rerun + " of " + testDescriptor);

            if (testDescriptor instanceof TestCaseDescriptor)
            {
                TestCaseDescriptor caseDescriptor = (TestCaseDescriptor) testDescriptor;
                TestClassDescriptor classDescriptor = (TestClassDescriptor) testDescriptor.getParent().orElseThrow(IllegalStateException::new);
                String test = caseDescriptor.getTest() + "-pytest_rerun-" + rerun;
                testDescriptor = registerTestDescriptor(TestCaseDescriptor.createChild(classDescriptor, test), classDescriptor);
            }
            else
            {
                throw new IllegalArgumentException("Detected re-run of class " + testDescriptor.getUniqueId());
            }
        }

        if (testenv.isDebug())
            System.err.println("executionStarted " + testDescriptor);
        executionListener.executionStarted(testDescriptor);

        runningTests.add(testDescriptor);
    }

    private void doExecutionFinished(TestExecutionResult result, int removeIndex)
    {
        TestDescriptor testDescriptor = runningTests.remove(removeIndex);

        logVerbose("TestHandler.doExecutionFinished: %s --> %s / %d", testDescriptor.getUniqueId(), result, removeIndex);

        finished.compute(testDescriptor.getUniqueId(), (id, existing) -> existing == null ? 1 : 1 + existing);

        if (testenv.isDebug())
            System.err.println("executionFinished " + testDescriptor + " " + result);
        executionListener.executionFinished(testDescriptor, result);
    }

    private void doReportEntry(ReportEntry reportEntry)
    {
        TestDescriptor current = runningTests.get(runningTests.size() - 1);
        if (!finished.containsKey(current.getUniqueId()))
        {
            if (testenv.isDebug())
                System.err.println("reportingEntryPublished " + current);
            executionListener.reportingEntryPublished(current, reportEntry);
        }
        else
        {
            System.err.println("NOT propagating reportingEntryPublished for " + current);
        }
    }

    private void logVerbose(String format, Object... args)
    {
        if (testenv.isVerbose())
            System.out.println(String.format(format, args));
    }
}
