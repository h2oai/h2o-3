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
import water.Key;

import java.util.concurrent.ConcurrentHashMap;
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
public class CHMLargePutBenchmark {

    ConcurrentHashMap<String,Integer> chm = new ConcurrentHashMap<String,Integer>(4194304);

    @Setup(Level.Iteration)
    // prior to each jmh iteration, clear out the hash map and load up 1,000,000 keys
    public void initCHM() {
        chm.clear();
        for (int i=0; i<1000000; i++) chm.put(Key.rand(),0);
    }

    //@State(Scope.Thread)
    //public static class ThreadState {
    //    String key;
    //
    //    @Setup(Level.Iteration)
    //    public void getKey(CHMLargePutBenchmark bm) { key = Key.rand(); }
    //}

    // after each jmh iteration, make sure no resize operations took place because we don't want to measure these
    @TearDown(Level.Iteration)
    public void checkCHM() throws InterruptedException {
        if (chm.size() > 4194304*.75) {
            System.out.println("CHM probably resized. Invalid experiment.");
            throw new InterruptedException();
        }
    }

    @Benchmark
    @Threads(value=1)
    public void largePutTest1() { chm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=2)
    public void largePutTest2() { chm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=4)
    public void largePutTest4() { chm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=8)
    public void largePutTest8() { chm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=16)
    public void largePutTest16() { chm.put(Key.rand(),0); }
}
