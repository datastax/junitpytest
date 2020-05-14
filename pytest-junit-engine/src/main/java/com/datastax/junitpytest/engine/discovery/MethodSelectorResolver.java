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
import java.util.function.Function;
import java.util.function.Predicate;

import com.datastax.junitpytest.engine.TestClassDescriptor;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.engine.support.discovery.SelectorResolver;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectUniqueId;
import static org.junit.platform.engine.support.discovery.SelectorResolver.Resolution.unresolved;

class MethodSelectorResolver implements SelectorResolver
{
    @Override
    public Resolution resolve(MethodSelector selector, Context context)
    {
        Class<?> testClass = selector.getJavaClass();
        return resolveParentAndAddFilter(context,
                                         selectClass(testClass),
                                         parent -> toMethodFilter(selector));
    }

    @Override
    public Resolution resolve(UniqueIdSelector selector, Context context)
    {
        for (UniqueId current = selector.getUniqueId(); !current.getSegments().isEmpty(); current = current.removeLastSegment())
        {
            if (TestClassDescriptor.SEGMENT_TYPE.equals(current.getLastSegment().getType()))
            {
                return resolveParentAndAddFilter(context, selectUniqueId(current),
                                                 parent -> toUniqueIdFilter(selector.getUniqueId()));
            }
        }
        return unresolved();
    }

    private Resolution resolveParentAndAddFilter(Context context, DiscoverySelector selector,
                                                 Function<TestClassDescriptor, Predicate<String>> filterCreator)
    {
        return context.resolve(selector)
                      .flatMap(parent -> addFilter(parent, filterCreator))
                      .map(this::toResolution)
                      .orElse(unresolved());
    }

    private Resolution toResolution(TestClassDescriptor parent)
    {
        return Resolution.match(Match.partial(parent));
    }

    private Optional<TestClassDescriptor> addFilter(TestDescriptor parent,
                                                    Function<TestClassDescriptor, Predicate<String>> filterCreator)
    {
        if (parent instanceof TestClassDescriptor)
        {
            TestClassDescriptor testClassDescriptor = (TestClassDescriptor) parent;
            testClassDescriptor.getFilters()
                               .ifPresent(filters -> filters.add(filterCreator.apply(testClassDescriptor)));
            return Optional.of(testClassDescriptor);
        }
        return Optional.empty();
    }

    private Predicate<String> toMethodFilter(MethodSelector methodSelector)
    {
        String testMethod = methodSelector.getJavaMethod().getName();
        return m -> m.equals(testMethod);
    }

    private Predicate<String> toUniqueIdFilter(UniqueId methodUniqueId)
    {
        String testMethod = methodUniqueId.getLastSegment().getValue();
        return m -> m.equals(testMethod);
    }
}
