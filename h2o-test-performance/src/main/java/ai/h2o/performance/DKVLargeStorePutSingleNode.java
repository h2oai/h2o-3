/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sample;

import org.openjdk.jmh.annotations.*;

import water.nbhm.NonBlockingHashMap;
import water.Key;

import java.util.concurrent.TimeUnit;

@Fork(5)
@BenchmarkMode(Mode.AverageTime)
@Measurement(iterations=20, timeUnit=TimeUnit.MILLISECONDS, time=100)
@Warmup(iterations=10, timeUnit=TimeUnit.MILLISECONDS, time=10)
@OutputTimeUnit(value= TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)

// Alternative setup. Only do 1 method invocation per iteration. Generate unique key prior to each interation.
//@Fork(3)
//@BenchmarkMode(Mode.AverageTime)
//@Measurement(iterations=1000)
//@Warmup(iterations=10)
//@OutputTimeUnit(value=TimeUnit.NANOSECONDS)
//@State(Scope.Benchmark)
public class DKVLargeStorePutSingleNode {

    NonBlockingHashMap<String, Integer> nbhm = new NonBlockingHashMap<String, Integer>(4194304);

    @Setup(Level.Iteration)
    public void initNBMH() {
        nbhm.clear();
        for (int i=0; i<1000000; i++) nbhm.put(Key.rand(),0);
    }


    //@State(Scope.Thread)
    //public static class ThreadState {
    //    String key;
    //
    //    @Setup(Level.Iteration)
    //    public void getKey(DKVLargeStorePutSingleNode bm) { key = Key.rand(); }
    //}

    @Benchmark
    @Threads(value=1)
    public void largeStorePutTest1() { nbhm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=2)
    public void largeStorePutTest2() { nbhm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=4)
    public void largeStorePutTest4() { nbhm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=8)
    public void largeStorePutTest8() { nbhm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=16)
    public void largeStorePutTest16() { nbhm.put(Key.rand(),0); }
}
