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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.Set;

@Fork(5)
@BenchmarkMode(Mode.AverageTime)
@Measurement(iterations=10)
@Warmup(iterations=3)
@OutputTimeUnit(value= TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class CHMGetBenchmark {

    public static String getRandomKey() {
        UUID uid = UUID.randomUUID();
        long l1 = uid.getLeastSignificantBits();
        long l2 = uid. getMostSignificantBits();
        return "_"+Long.toHexString(l1)+Long.toHexString(l2);
    }

    ConcurrentHashMap<String,Integer> chm = new ConcurrentHashMap<String,Integer>(10000);

    @Setup(Level.Trial)
    public void initCHM() {
        // Load up 1000 keys
        for (int i=0; i<1000; i++) chm.put(getRandomKey(), 0);
        System.out.println("@Setup for CHMGetBenchmark Trial - Scope.Benchmark");
        System.out.println("Done initializing the CHM. Number of actual keys: "+chm.size());
    }

    @State(Scope.Thread)
    public static class ThreadState {
        Set<String> keySet;
        String tk;
        int invocations;

        @Setup(Level.Trial)
        public void getKeySet(CHMGetBenchmark bm) {
            keySet = bm.chm.keySet();
        }

        @Setup(Level.Iteration)
        public void initInvocations() { invocations = 0; }

        @TearDown(Level.Iteration)
        public void logInvocations() {
            System.out.println("@TearDown for CHMGetBenchmark Iteration - Scope.Thread");
            System.out.println("Number of method invocations for this thread: "+ invocations);
        }

        @Setup(Level.Invocation)
        public void setKeyForGetOp() {
            tk = (String) keySet.toArray()[new Random().nextInt(keySet.size())];
            invocations += 1;
        }
    }

    @Benchmark
    @Threads(value=1)
    public Integer chmGetTest1(ThreadState ts) { return chm.get(ts.tk); }

    @Benchmark
    @Threads(value=2)
    public Integer chmGetTest2(ThreadState ts) { return chm.get(ts.tk); }

    @Benchmark
    @Threads(value=4)
    public Integer chmGetTest4(ThreadState ts) { return chm.get(ts.tk); }

    @Benchmark
    @Threads(value=8)
    public Integer chmGetTest8(ThreadState ts) { return chm.get(ts.tk); }

    @Benchmark
    @Threads(value=16)
    public Integer chmGetTest16(ThreadState ts) { return chm.get(ts.tk); }
}
