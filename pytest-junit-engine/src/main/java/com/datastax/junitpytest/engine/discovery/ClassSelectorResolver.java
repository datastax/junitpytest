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
package com.datastax.junitpytest.engine.discovery;

import java.util.Optional;
import java.util.function.Predicate;

import com.datastax.junitpytest.engine.TestClassDescriptor;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver;
import org.junit.platform.engine.support.discovery.SelectorResolver;

import static org.junit.platform.engine.support.discovery.SelectorResolver.Resolution.unresolved;

class ClassSelectorResolver implements SelectorResolver
{
    private final PytestDiscoverer testDiscoverer;
    private final Predicate<String> classFilter;

    ClassSelectorResolver(PytestDiscoverer testDiscoverer, EngineDiscoveryRequestResolver.InitializationContext<TestDescriptor> context)
    {
        this.testDiscoverer = testDiscoverer;
        this.classFilter = context.getClassNameFilter();
    }

    @Override
    public Resolution resolve(ClassSelector selector, Context context)
    {
        return resolveClass(selector.getClassName(), context);
    }

    @Override
    public Resolution resolve(UniqueIdSelector selector, Context context)
    {
        UniqueId.Segment lastSegment = selector.getUniqueId().getLastSegment();
        if (TestClassDescriptor.SEGMENT_TYPE.equals(lastSegment.getType()))
        {
            String testClassName = lastSegment.getValue();
            return resolveClass(testClassName, context);
        }
        return unresolved();
    }

    private Resolution resolveClass(String testClass, Context context)
    {
        if (!classFilter.test(testClass))
            return unresolved();

        if (!testDiscoverer.acceptsTestClass(testClass))
            return unresolved();

        return context.addToParent(parent -> Optional.of(TestClassDescriptor.createChild(parent, testDiscoverer.testClassInfo(testClass))))
                      .map(Match::exact)
                      .map(Resolution::match)
                      .orElse(unresolved());
    }
}
