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
package org.stefanutti.metrics.cdi;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.Counted;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Gauge;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.inject.spi.WithAnnotations;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MetricsExtension implements Extension {

    private boolean hasMetricRegistry;

    private final Map<Bean<?>, AnnotatedMember<?>> metrics = new HashMap<Bean<?>, AnnotatedMember<?>>();

    private <X> void processMetricsAnnotatedType(@Observes @WithAnnotations({Counted.class, ExceptionMetered.class, Gauge.class, Metered.class, Timed.class}) ProcessAnnotatedType<X> pat) {
        boolean gauge = false;
        Set<AnnotatedMethod<? super X>> decoratedMethods = new HashSet<AnnotatedMethod<? super X>>(4);
        for (AnnotatedMethod<? super X> method : pat.getAnnotatedType().getMethods()) {
            if (method.isAnnotationPresent(Counted.class))
                decoratedMethods.add(getAnnotatedMethodDecorator(method, CountedBindingLiteral.INSTANCE));
            if (method.isAnnotationPresent(ExceptionMetered.class))
                decoratedMethods.add(getAnnotatedMethodDecorator(method, ExceptionMeteredBindingLiteral.INSTANCE));
            if (method.isAnnotationPresent(Metered.class))
                decoratedMethods.add(getAnnotatedMethodDecorator(method, MeteredBindingLiteral.INSTANCE));
            if (method.isAnnotationPresent(Timed.class))
                decoratedMethods.add(getAnnotatedMethodDecorator(method, TimedBindingLiteral.INSTANCE));
            if (method.isAnnotationPresent(Gauge.class))
                gauge = true;
        }
        AnnotatedType<X> annotatedType = new AnnotatedTypeDecorator<X>(pat.getAnnotatedType(), MetricsBindingLiteral.INSTANCE);
        // FIXME: remove when OWB supports @WithAnnotations, see OWB-980
        if (gauge /*decoratedMethods.isEmpty()*/)
            pat.setAnnotatedType(annotatedType);
        else if (!decoratedMethods.isEmpty())
            pat.setAnnotatedType(new AnnotatedTypeMethodDecorator<X>(annotatedType, decoratedMethods));
    }

    private void processMetricRegistryBean(@Observes ProcessBean<MetricRegistry> pb) {
        hasMetricRegistry = true;
    }

    private void processMetricRegistryProducerField(@Observes ProcessProducerField<MetricRegistry, ?> ppf) {
        hasMetricRegistry = true;
    }

    private void processMetricRegistryProducerMethod(@Observes ProcessProducerMethod<MetricRegistry, ?> ppm) {
        hasMetricRegistry = true;
    }

    private void processMetricProducerField(@Observes ProcessProducerField<? extends Metric, ?> ppf) {
        metrics.put(ppf.getBean(), ppf.getAnnotatedProducerField());
    }

    private void processMetricProducerMethod(@Observes ProcessProducerMethod<? extends Metric, ?> ppm) {
        // Skip the Metrics CDI alternatives
        if (!ppm.getBean().getBeanClass().equals(MetricProducer.class))
            metrics.put(ppm.getBean(), ppm.getAnnotatedProducerMethod());
    }

    private void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager manager) {
        // TODO: should be possible from the CDI spec though Weld is returning an empty set at that stage
        //if (manager.getBeans(MetricRegistry.class, AnyLiteral.INSTANCE).isEmpty())
        if (!hasMetricRegistry)
            abd.addBean(new MetricRegistryBean(manager));
    }

    private void afterDeploymentValidation(@Observes AfterDeploymentValidation adv, BeanManager manager) {
        MetricRegistry registry = getBeanInstance(manager, MetricRegistry.class);
        MetricNameHelper helper = getBeanInstance(manager, MetricNameHelper.class);

        for (Map.Entry<Bean<?>, AnnotatedMember<?>> metric : metrics.entrySet()) {
            Metric reference = (Metric) manager.getReference(metric.getKey(), metric.getValue().getBaseType(), manager.createCreationalContext(null));
            registry.register(helper.metricName(metric.getValue()), reference);
        }

        // Let's clear the collected metric producers
        metrics.clear();
    }

    private static <X> AnnotatedMethod<X> getAnnotatedMethodDecorator(AnnotatedMethod<X> annotatedMethod, Annotation annotated) {
        return new AnnotatedMethodDecorator<X>(annotatedMethod, annotated);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getBeanInstance(BeanManager manager, Class<T> clazz) {
        Bean<?> bean = manager.resolve(manager.getBeans(clazz, AnyLiteral.INSTANCE));
        return (T) manager.getReference(bean, clazz, manager.createCreationalContext(null));
    }
}