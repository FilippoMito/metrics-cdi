/**
 * Copyright (C) 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.astefanutti.metrics.cdi.se;

import com.codahale.metrics.MetricRegistry;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import io.astefanutti.metrics.cdi.MetricsExtension;
import io.astefanutti.metrics.cdi.se.util.MetricsUtil;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class DefaultNameMetricMethodBeanTest {

    private final static String[] METRIC_NAMES = {"defaultNameCountedMethod", "defaultNameExceptionMeteredMethod.exceptions", "defaultNameMeteredMethod", "defaultNameTimedMethod"};

    private final static String[] ABSOLUTE_METRIC_NAMES = {"absoluteDefaultNameCountedMethod", "absoluteDefaultNameExceptionMeteredMethod.exceptions", "absoluteDefaultNameMeteredMethod", "absoluteDefaultNameTimedMethod"};

    private Set<String> metricNames() {
        Set<String> names = MetricsUtil.absoluteMetricNames(DefaultNameMetricMethodBean.class, METRIC_NAMES);
        names.addAll(Arrays.asList(ABSOLUTE_METRIC_NAMES));
        return names;
    }

    @Deployment
    public static Archive<?> createTestArchive() {
        return ShrinkWrap.create(JavaArchive.class)
            // Test bean
            .addClass(DefaultNameMetricMethodBean.class)
            // Metrics CDI extension
            .addPackage(MetricsExtension.class.getPackage())
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    private MetricRegistry registry;

    @Inject
    private DefaultNameMetricMethodBean bean;

    @Test
    public void metricMethodsWithDefaultNamingConvention() {
        assertThat("Metrics are not registered correctly", registry.getMetrics().keySet(), is(equalTo(metricNames())));
    }
}