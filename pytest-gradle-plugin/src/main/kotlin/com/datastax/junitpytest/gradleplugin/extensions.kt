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
package com.datastax.junitpytest.gradleplugin

import org.gradle.api.Action
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.internal.Actions

fun Test.withJUnitPlatform(testFrameworkConfigure: Action<in JUnitPlatformOptions>?) {
    val action = testFrameworkConfigure ?: Actions.doNothing()
    val currentOptions = this.options
    if (currentOptions is JUnitPlatformOptions)
        action.execute(currentOptions)
    else
        this.useJUnitPlatform(action)
}
