/*
 *  Copyright 2023 ihromant.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.java.util.stream.impl;

import java.util.function.Predicate;

public class TDropWhileStream<T> extends TWrappingStreamImpl<T, T> {
    private Predicate<? super T> predicate;

    /* set to `true` as soon as we see a value `v` in the source stream for which `predicate.test(v)` is true */
    private boolean isStarted;

    TDropWhileStream(TSimpleStreamImpl<T> innerStream, Predicate<? super T> predicate) {
        super(innerStream);
        this.predicate = predicate;
    }

    @Override
    protected Predicate<T> wrap(Predicate<? super T> consumer) {
        return t -> {
            if (!isStarted) {
                if (predicate.test(t)) {
                    return true;
                } else {
                    isStarted = true;
                }
            }
            return consumer.test(t);
        };
    }
}
