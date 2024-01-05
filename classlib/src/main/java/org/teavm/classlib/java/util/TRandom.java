/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.classlib.java.util;

import org.teavm.classlib.impl.RandomUtils;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.classlib.java.util.random.TRandomGenerator;
import org.teavm.interop.Import;
import org.teavm.interop.Unmanaged;
import org.teavm.interop.wasi.Wasi;
import org.teavm.jso.JSBody;

public class TRandom extends TObject implements TRandomGenerator, TSerializable {
    /** A stored gaussian value for nextGaussian() */
    private double storedGaussian;

    /** Whether storedGuassian value is valid */
    private boolean haveStoredGaussian;

    public TRandom() {
    }

    public TRandom(@SuppressWarnings("unused") long seed) {
    }

    public void setSeed(@SuppressWarnings("unused") long seed) {
    }

    @Override
    public int nextInt() {
        return (int) (0x1.0p+32 * nextDouble() + Integer.MIN_VALUE);
    }

    @Override
    public int nextInt(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException();
        }
        return (int) (nextDouble() * n);
    }

    @Override
    public long nextLong() {
        return ((long) nextInt() << 32) | nextInt();
    }

    @Override
    public float nextFloat() {
        return (float) nextDouble();
    }

    @Override
    public double nextDouble() {
        return Math.random();
    }

    /**
     * Generate a random number with Gaussian distribution:
     * centered around 0 with a standard deviation of 1.0.
     */
    @Override
    public double nextGaussian() {
        /*
         * This implementation uses the polar method to generate two gaussian
         * values at a time. One is returned, and the other is stored to be returned
         * next time.
         */
        if (haveStoredGaussian) {
            haveStoredGaussian = false;
            return storedGaussian;
        }

        double[] pair = RandomUtils.pairGaussian(this::nextDouble);
        haveStoredGaussian = true;
        storedGaussian = pair[1];

        return pair[0];
    }

    private static double random() {
        if (PlatformDetector.isWebAssembly()) {
            return Wasi.random();
        } else {
            return nativeRandom();
        }
    }

    @JSBody(script = "return Math.random();")
    @Import(module = "teavmMath", name = "random")
    @Unmanaged
    private static native double nativeRandom();

    public TIntStream ints(long streamSize) {
        if (streamSize < 0) {
            throw new IllegalArgumentException();
        }
        return new TSimpleIntStreamImpl() {
            private long remaining = streamSize;

            @Override
            public boolean next(IntPredicate consumer) {
                while (remaining > 0) {
                    --remaining;
                    if (!consumer.test(nextInt())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public TIntStream ints() {
        return new TSimpleIntStreamImpl() {
            @Override
            public boolean next(IntPredicate consumer) {
                while (consumer.test(nextInt())) {
                    // go on
                }
                return true;
            }
        };
    }

    public TIntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
        if (streamSize < 0 || randomNumberOrigin >= randomNumberBound) {
            throw new IllegalArgumentException();
        }

        int range = randomNumberBound - randomNumberOrigin;
        if (range > 0) {
            return new TSimpleIntStreamImpl() {
                long remaining = streamSize;

                @Override
                public boolean next(IntPredicate consumer) {
                    while (remaining > 0) {
                        --remaining;
                        if (!consumer.test(nextInt(range) + randomNumberOrigin)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        } else {
            return new TSimpleIntStreamImpl() {
                long remaining = streamSize;

                @Override
                public boolean next(IntPredicate consumer) {
                    while (remaining > 0) {
                        --remaining;
                        int n;
                        do {
                            n = nextInt();
                        } while (n < randomNumberOrigin || n >= randomNumberBound);
                        if (!consumer.test(n)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }
    }

    public TIntStream ints(int randomNumberOrigin, int randomNumberBound) {
        if (randomNumberOrigin >= randomNumberBound) {
            throw new IllegalArgumentException();
        }

        int range = randomNumberBound - randomNumberOrigin;
        if (range > 0) {
            return new TSimpleIntStreamImpl() {
                @Override
                public boolean next(IntPredicate consumer) {
                    while (true) {
                        if (!consumer.test(nextInt(range) + randomNumberOrigin)) {
                            return true;
                        }
                    }
                }
            };
        } else {
            return new TSimpleIntStreamImpl() {
                @Override
                public boolean next(IntPredicate consumer) {
                    while (true) {
                        int n;
                        do {
                            n = nextInt();
                        } while (n < randomNumberOrigin || n >= randomNumberBound);
                        if (!consumer.test(n)) {
                            return true;
                        }
                    }
                }
            };
        }
    }

    public TLongStream longs(long streamSize) {
        if (streamSize < 0) {
            throw new IllegalArgumentException();
        }
        return new TSimpleLongStreamImpl() {
            private long remaining = streamSize;

            @Override
            public boolean next(LongPredicate consumer) {
                while (remaining > 0) {
                    --remaining;
                    if (!consumer.test(nextLong())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public TLongStream longs() {
        return new TSimpleLongStreamImpl() {
            @Override
            public boolean next(LongPredicate consumer) {
                while (consumer.test(nextLong())) {
                    // go on
                }
                return true;
            }
        };
    }

    public TLongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
        if (streamSize < 0 || randomNumberOrigin >= randomNumberBound) {
            throw new IllegalArgumentException();
        }

        long range = randomNumberBound - randomNumberOrigin;
        if (range > 0) {
            return new TSimpleLongStreamImpl() {
                long remaining = streamSize;

                @Override
                public boolean next(LongPredicate consumer) {
                    while (remaining > 0) {
                        --remaining;
                        if (!consumer.test(nextLong(range) + randomNumberOrigin)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        } else {
            return new TSimpleLongStreamImpl() {
                long remaining = streamSize;

                @Override
                public boolean next(LongPredicate consumer) {
                    while (remaining > 0) {
                        --remaining;
                        long n;
                        do {
                            n = nextLong();
                        } while (n < randomNumberOrigin || n >= randomNumberBound);
                        if (!consumer.test(n)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }
    }

    public TLongStream longs(long randomNumberOrigin, long randomNumberBound) {
        if (randomNumberOrigin >= randomNumberBound) {
            throw new IllegalArgumentException();
        }

        long range = randomNumberBound - randomNumberOrigin;
        if (range > 0) {
            return new TSimpleLongStreamImpl() {
                @Override
                public boolean next(LongPredicate consumer) {
                    while (true) {
                        if (!consumer.test(nextLong(range) + randomNumberOrigin)) {
                            return true;
                        }
                    }
                }
            };
        } else {
            return new TSimpleLongStreamImpl() {
                @Override
                public boolean next(LongPredicate consumer) {
                    while (true) {
                        long n;
                        do {
                            n = nextLong();
                        } while (n < randomNumberOrigin || n >= randomNumberBound);
                        if (!consumer.test(n)) {
                            return true;
                        }
                    }
                }
            };
        }
    }

    public TDoubleStream doubles(long streamSize) {
        if (streamSize < 0) {
            throw new IllegalArgumentException();
        }
        return new TSimpleDoubleStreamImpl() {
            private long remaining = streamSize;

            @Override
            public boolean next(DoublePredicate consumer) {
                while (remaining > 0) {
                    --remaining;
                    if (!consumer.test(nextDouble())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public TDoubleStream doubles() {
        return new TSimpleDoubleStreamImpl() {
            @Override
            public boolean next(DoublePredicate consumer) {
                while (consumer.test(nextDouble())) {
                    // go on
                }
                return true;
            }
        };
    }

    public TDoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) {
        if (streamSize < 0 || randomNumberOrigin >= randomNumberBound) {
            throw new IllegalArgumentException();
        }

        double range = randomNumberBound - randomNumberOrigin;
        return new TSimpleDoubleStreamImpl() {
            long remaining = streamSize;

            @Override
            public boolean next(DoublePredicate consumer) {
                while (remaining > 0) {
                    --remaining;
                    if (!consumer.test(nextDouble() * range + randomNumberOrigin)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public TDoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
        if (randomNumberOrigin >= randomNumberBound) {
            throw new IllegalArgumentException();
        }

        double range = randomNumberBound - randomNumberOrigin;
        return new TSimpleDoubleStreamImpl() {
            @Override
            public boolean next(DoublePredicate consumer) {
                while (true) {
                    if (!consumer.test(nextDouble() * range + randomNumberOrigin)) {
                        return true;
                    }
                }
            }
        };
    }
}
