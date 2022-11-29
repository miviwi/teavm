/*
 *  Copyright 2022 Alexey Andreev.
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
package org.teavm.debugging;

import org.teavm.backend.wasm.debug.parser.DebugInfoParser;
import org.teavm.backend.wasm.debug.parser.DebugInfoReader;
import org.teavm.common.CompletablePromise;
import org.teavm.common.Promise;

class DebugInfoReaderImpl implements DebugInfoReader {
    private byte[] data;
    private int ptr;
    private CompletablePromise<Integer> promise;
    private byte[] target;
    private int offset;
    private int count;

    DebugInfoReaderImpl(byte[] data) {
        this.data = data;
    }

    DebugInfoParser read() {
        var debugInfoParser = new DebugInfoParser(this);
        Promise.runNow(() -> {
            debugInfoParser.parse().catchVoid(Throwable::printStackTrace);
            complete();
        });
        return debugInfoParser;
    }

    @Override
    public Promise<Integer> skip(int amount) {
        promise = new CompletablePromise<>();
        count = amount;
        return promise;
    }

    @Override
    public Promise<Integer> read(byte[] buffer, int offset, int count) {
        promise = new CompletablePromise<>();
        this.target = buffer;
        this.offset = offset;
        this.count = count;
        return promise;
    }

    private void complete() {
        while (promise != null) {
            var p = promise;
            count = Math.min(count, data.length - ptr);
            promise = null;
            if (target != null) {
                System.arraycopy(data, ptr, target, offset, count);
                target = null;
            }
            ptr += count;
            if (count == 0) {
                count = -1;
            }
            p.complete(count);
        }
    }
}