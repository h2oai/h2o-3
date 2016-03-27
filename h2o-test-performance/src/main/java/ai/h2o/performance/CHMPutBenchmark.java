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
//java -cp /Users/ece/0xdata/h2o-3/build/h2o.jar:target/benchmarks.jar org.openjdk.jmh.Main -rf csv -rff perf.csv
package org.sample;

import org.openjdk.jmh.annotations.*;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Fork(5)
@BenchmarkMode(Mode.AverageTime)
@Measurement(iterations=10, timeUnit=TimeUnit.MILLISECONDS, time=250)
@Warmup(iterations=2, timeUnit=TimeUnit.MILLISECONDS, time=5)
@OutputTimeUnit(value= TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class CHMPutBenchmark {

    ConcurrentHashMap<String,Integer> chm = new ConcurrentHashMap<String,Integer>(550000);

    public static String getRandomKey() {
        UUID uid = UUID.randomUUID();
        long l1 = uid.getLeastSignificantBits();
        long l2 = uid. getMostSignificantBits();
        return "_"+Long.toHexString(l1)+Long.toHexString(l2);
    }

    @Setup(Level.Iteration)
    public void initCHM() {
        /* Clear out the chm */
        System.out.println("@Setup for CHMPutBenchmark Iteration - Scope.Benchmark");
        System.out.println("Empting the CHM.");
        chm.clear();

        /* Grow the CHM */
        for (int i=0; i<65000; i++) chm.put(getRandomKey(),0);
        System.out.println("Done initializing the CHM. Number of actual keys: "+chm.size()+".");
    }

    @TearDown(Level.Iteration)
    public void checkCHM() throws InterruptedException {
        System.out.println("@TearDown for CHMPutBenchmark Iteration - Scope.Benchmark");
        System.out.println("Checking the CHM. Number of actual keys: "+chm.size()+". ");
        /* Check that we didn't put enough keys that would trigger a CHM resize. */
        if (chm.size() > 550000*.75) {
            System.out.println("CHM probably resized. Invalid experiment.");
            throw new InterruptedException();
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        String k;
        int invocations;

        @Setup(Level.Invocation)
        public void setKey() {
            k = CHMPutBenchmark.getRandomKey();
            invocations += 1;
        }

        @Setup(Level.Iteration)
        public void initInvocations() { invocations = 0; }

        @TearDown(Level.Iteration)
        public void logInvocations() {
            System.out.println("@TearDown for CHMPutBenchmark Iteration - Scope.Thread");
            System.out.println("Number of method invocations for this thread: "+ invocations);
        }
    }

    @Benchmark
    @Threads(value=1)
    public void chmPutTest1(ThreadState ts) { chm.put(ts.k,0); }

    @Benchmark
    @Threads(value=2)
    public void chmPutTest2(ThreadState ts) { chm.put(ts.k,0); }

    @Benchmark
    @Threads(value=4)
    public void chmPutTest4(ThreadState ts) { chm.put(ts.k,0); }

    @Benchmark
    @Threads(value=8)
    public void chmPutTest8(ThreadState ts) { chm.put(ts.k,0); }

    @Benchmark
    @Threads(value=16)
    public void chmPutTest16(ThreadState ts) { chm.put(ts.k,0); }
}
