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


import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.CachedGauge;
import com.codahale.metrics.annotation.Counted;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Gauge;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import org.stefanutti.metrics.cdi.MetricResolver.Metric;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundConstruct;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Interceptor
@MetricsBinding
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
// See http://docs.oracle.com/javaee/7/tutorial/doc/interceptors.htm
/* packaged-private */ class MetricsInterceptor {

    private final MetricRegistry registry;

    private final MetricResolver resolver;

    @Inject
    private MetricsInterceptor(MetricRegistry registry, MetricResolver resolver) {
        this.registry = registry;
        this.resolver = resolver;
    }

    @AroundConstruct
    private Object metrics(InvocationContext context) throws Exception {
        Object target = context.proceed();

        Class<?> bean = context.getConstructor().getDeclaringClass();
        for (Method method : bean.getDeclaredMethods()) {
            Metric<CachedGauge> cachedGauge = resolver.cachedGaugeMethod(method);
            if (cachedGauge.isPresent())
                registry.register(cachedGauge.metricName(), new CachingGauge(new ForwardingGauge(method, context.getTarget()), cachedGauge.metricAnnotation().timeout(), cachedGauge.metricAnnotation().timeoutUnit()));

            Metric<Counted> counted = resolver.countedMethod(method);
            if (counted.isPresent())
                registry.counter(counted.metricName());

            Metric<ExceptionMetered> exceptionMetered = resolver.exceptionMeteredMethod(method);
            if (exceptionMetered.isPresent())
                registry.meter(exceptionMetered.metricName());

            Metric<Gauge> gauge = resolver.gaugeMethod(method);
            if (gauge.isPresent())
                registry.register(gauge.metricName(), new ForwardingGauge(method, context.getTarget()));

            Metric<Metered> metered = resolver.meteredMethod(method);
            if (metered.isPresent())
                registry.meter(metered.metricName());

            Metric<Timed> timed = resolver.timedMethod(method);
            if (timed.isPresent())
                registry.timer(timed.metricName());
        }

        return target;
    }
    
    private static class CachingGauge extends com.codahale.metrics.CachedGauge<Object> {

        private final com.codahale.metrics.Gauge<?> gauge;
        
        private CachingGauge(com.codahale.metrics.Gauge<?> gauge, long timeout, TimeUnit timeoutUnit) {
            super(timeout, timeoutUnit);
            this.gauge = gauge;
        }

        @Override
        protected Object loadValue() {
            return gauge.getValue();
        }
    }

    private static class ForwardingGauge implements com.codahale.metrics.Gauge<Object> {

        private final Method method;

        private final Object object;

        private ForwardingGauge(Method method, Object object) {
            this.method = method;
            this.object = object;
            method.setAccessible(true);
        }

        @Override
        public Object getValue() {
            return invokeMethod(method, object);
        }
    }

    private static Object invokeMethod(Method method, Object object) {
        try {
            return method.invoke(object);
        } catch (IllegalAccessException cause) {
            throw new IllegalStateException("Error while calling method [" + method + "]", cause);
        } catch (InvocationTargetException cause) {
            throw new IllegalStateException("Error while calling method [" + method + "]", cause);
        }
    }
}
