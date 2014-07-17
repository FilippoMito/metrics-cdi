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


import javax.enterprise.inject.Vetoed;
import javax.enterprise.util.AnnotationLiteral;

@Vetoed
/* packaged-private */ final class CountedBindingLiteral extends AnnotationLiteral<CountedBinding> implements CountedBinding {

    private static final long serialVersionUID = 1L;

    private static final CountedBinding COUNTED_BINDING = new CountedBindingLiteral(false);

    private static final CountedBinding MONOTONIC_COUNTED_BINDING = new CountedBindingLiteral(true);

    private final boolean monotonic;

    private CountedBindingLiteral(boolean monotonic) {
        this.monotonic = monotonic;
    }

    static CountedBinding instance(boolean monotonic) {
        if (monotonic)
            return MONOTONIC_COUNTED_BINDING;
        else
            return COUNTED_BINDING;
    }

    @Override
    public boolean monotonic() {
        return monotonic;
    }
}