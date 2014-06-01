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
package org.stefanutti.metrics.cdi.se;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.stefanutti.metrics.cdi.Metric;
import org.stefanutti.metrics.cdi.MetricsExtension;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class TimerInjectionBeanTest {

    @Deployment
    static Archive<?> createTestArchive() {
        return ShrinkWrap.create(JavaArchive.class)
            // Test bean
            .addClass(TimedMethodBean.class)
            // Metrics CDI extension
            .addPackage(MetricsExtension.class.getPackage())
            // Bean archive deployment descriptor
            // FIXME: use EmptyAsset.INSTANCE when OWB supports CDI 1.1
            .addAsManifestResource("beans-test.xml", "beans.xml");
    }

    @Inject
    private MetricRegistry registry;

    @Inject
    private TimedMethodBean bean;

    @Inject
    @Metric(absolute = true, name = "org.stefanutti.metrics.cdi.se.TimedMethodBean.timedMethod")
    private Timer timer;

    @Test
    @InSequence(1)
    public void timedMethodNotCalledYet() {
        // Make sure that the timer hasn't been called yet
        assertThat("Timer count is incorrect", timer.getCount(), is(equalTo(0L)));
    }

    @Test
    @InSequence(2)
    public void callTimedMethodOnce() {
        // Call the timed method and assert it's been timed
        bean.timedMethod();

        // Make sure that the timer has been called
        assertThat("Timer count is incorrect", timer.getCount(), is(equalTo(1L)));
    }
}