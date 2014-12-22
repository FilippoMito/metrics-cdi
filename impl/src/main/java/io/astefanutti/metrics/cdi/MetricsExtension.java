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
package io.astefanutti.metrics.cdi;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.CachedGauge;
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
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MetricsExtension implements Extension {

    private static final AnnotationLiteral<Nonbinding> NON_BINDING = new AnnotationLiteral<Nonbinding>(){};

    private static final AnnotationLiteral<InterceptorBinding> INTERCEPTOR_BINDING = new AnnotationLiteral<InterceptorBinding>(){};

    private static final AnnotationLiteral<MetricsBinding> METRICS_BINDING = new AnnotationLiteral<MetricsBinding>(){};

    private final Map<Bean<?>, AnnotatedMember<?>> metrics = new HashMap<>();

    private void addInterceptorBindings(@Observes BeforeBeanDiscovery bbd, BeanManager manager) {
        declareAsInterceptorBinding(Counted.class, manager, bbd);
        declareAsInterceptorBinding(ExceptionMetered.class, manager, bbd);
        declareAsInterceptorBinding(Metered.class, manager, bbd);
        declareAsInterceptorBinding(Timed.class, manager, bbd);
    }

    private <X> void metricsAnnotations(@Observes @WithAnnotations({CachedGauge.class, Counted.class, ExceptionMetered.class, Gauge.class, Metered.class, Timed.class}) ProcessAnnotatedType<X> pat) {
        pat.setAnnotatedType(new AnnotatedTypeDecorator<>(pat.getAnnotatedType(), METRICS_BINDING));
    }

    private void metricProducerField(@Observes ProcessProducerField<? extends Metric, ?> ppf) {
        metrics.put(ppf.getBean(), ppf.getAnnotatedProducerField());
    }

    private void metricProducerMethod(@Observes ProcessProducerMethod<? extends Metric, ?> ppm) {
        // Skip the Metrics CDI alternatives
        if (!ppm.getBean().getBeanClass().equals(MetricProducer.class))
            metrics.put(ppm.getBean(), ppm.getAnnotatedProducerMethod());
    }

    private void defaultMetricRegistry(@Observes AfterBeanDiscovery abd, BeanManager manager) {
        if (manager.getBeans(MetricRegistry.class).isEmpty())
            abd.addBean(new MetricRegistryBean(manager));
    }

    private void customMetrics(@Observes AfterDeploymentValidation adv, BeanManager manager) {
        MetricProducer producer = getBeanInstance(manager, MetricProducer.class);
        for (Map.Entry<Bean<?>, AnnotatedMember<?>> metric : metrics.entrySet())
            producer.produceMetric(manager, metric.getKey(), metric.getValue());

        // Let's clear the collected metric producers
        metrics.clear();
    }

    private static <T extends Annotation> void declareAsInterceptorBinding(Class<T> annotation, BeanManager manager, BeforeBeanDiscovery bbd) {
        AnnotatedType<T> annotated = manager.createAnnotatedType(annotation);
        Set<AnnotatedMethod<? super T>> methods = new HashSet<>();
        for (AnnotatedMethod<? super T> method : annotated.getMethods())
            methods.add(new AnnotatedMethodDecorator<>(method, NON_BINDING));

        bbd.addInterceptorBinding(new AnnotatedTypeDecorator<>(annotated, INTERCEPTOR_BINDING, methods));
    }

    @SuppressWarnings("unchecked")
    private static <T> T getBeanInstance(BeanManager manager, Class<T> clazz) {
        Bean<?> bean = manager.resolve(manager.getBeans(clazz));
        return (T) manager.getReference(bean, clazz, manager.createCreationalContext(null));
    }
}