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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PytestCollectEntryTest
{
    @Test
    public void linesParsing()
    {
        // meta_tests.py::24::TestMeta::test_skip::test_skip

        assertNull(PytestCollectEntry.parse(""));
        assertNull(PytestCollectEntry.parse("  Foo Bar"));

        assertNull(PytestCollectEntry.parse("some.py::SomeClass"));
        assertNull(PytestCollectEntry.parse("some.py::cls::test_method::test_method"));

        assertThat(PytestCollectEntry.parse("some_file.py::FooTest::test_a::test_a")).extracting(PytestCollectEntry::getFile,
                                                                                                 PytestCollectEntry::getSimpleClassName,
                                                                                                 PytestCollectEntry::getMethod,
                                                                                                 PytestCollectEntry::getTest,
                                                                                                 PytestCollectEntry::getFullyQualifiedClassName,
                                                                                                 PytestCollectEntry::toString)
                                                                                     .containsExactly("some_file.py",
                                                                                                      "FooTest",
                                                                                                      "test_a",
                                                                                                      "test_a",
                                                                                                      "some_file.FooTest",
                                                                                                      "some_file.py::FooTest::test_a::test_a");

        assertThat(PytestCollectEntry.parse("dir_one/some_file.py::FooTest::test_a::test_a")).extracting(PytestCollectEntry::getFile,
                                                                                                         PytestCollectEntry::getSimpleClassName,
                                                                                                         PytestCollectEntry::getMethod,
                                                                                                         PytestCollectEntry::getTest,
                                                                                                         PytestCollectEntry::getFullyQualifiedClassName,
                                                                                                         PytestCollectEntry::toString)
                                                                                             .containsExactly("dir_one/some_file.py",
                                                                                                              "FooTest",
                                                                                                              "test_a",
                                                                                                              "test_a",
                                                                                                              "dir_one.some_file.FooTest",
                                                                                                              "dir_one/some_file.py::FooTest::test_a::test_a");

        assertThat(PytestCollectEntry.parse("dir_one/two/some_file.py::FooTest::test_a::test_a[some,thing]")).extracting(PytestCollectEntry::getFile,
                                                                                                                         PytestCollectEntry::getSimpleClassName,
                                                                                                                         PytestCollectEntry::getMethod,
                                                                                                                         PytestCollectEntry::getTest,
                                                                                                                         PytestCollectEntry::getFullyQualifiedClassName,
                                                                                                                         PytestCollectEntry::toString)
                                                                                                             .containsExactly("dir_one/two/some_file.py",
                                                                                                                              "FooTest",
                                                                                                                              "test_a",
                                                                                                                              "test_a[some,thing]",
                                                                                                                              "dir_one.two.some_file.FooTest",
                                                                                                                              "dir_one/two/some_file.py::FooTest::test_a::test_a[some,thing]");
    }
}
