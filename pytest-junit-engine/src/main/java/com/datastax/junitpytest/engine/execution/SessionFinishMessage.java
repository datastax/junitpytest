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

import com.datastax.junitpytest.engine.exceptions.PytestFailedException;
import org.junit.platform.engine.TestExecutionResult;

class SessionFinishMessage extends Message
{
    @Override
    void execute(TestHandler testHandler)
    {
        int exitstatus = Integer.parseInt(block("exitstatus", "3"));

        if (testHandler.testenv.isDebug())
            System.err.println(String.format("pytest/sessionfinish: '%d'", exitstatus));

        switch (exitstatus)
        {
            case 2: // EXIT_INTERRUPTED
                String kbdIntrMsg = block("kbdintr_message");
                String kbdIntrExc = block("kbdintr_excinfo");
                testHandler.sessionFinished(TestExecutionResult.failed(new PytestFailedException(kbdIntrMsg + "\n" + kbdIntrExc)));
                break;
            case 3: // EXIT_INTERNALERROR
                testHandler.sessionFinished(TestExecutionResult.failed(new PytestFailedException(testHandler.internalError != null ? testHandler.internalError : "EXIT_INTERNALERROR")));
                break;
            case 4: // EXIT_USAGEERROR
                testHandler.sessionFinished(TestExecutionResult.failed(new PytestFailedException("EXIT_USAGEERROR")));
                break;
            case 0: // EXIT_OK
            case 1: // EXIT_TESTSFAILED
            case 5: // EXIT_NOTESTSCOLLECTED
                testHandler.sessionFinished(TestExecutionResult.successful());
                break;
            default:
                break;
        }
    }
}
