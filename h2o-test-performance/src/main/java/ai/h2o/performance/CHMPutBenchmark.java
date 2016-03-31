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
//java -cp h2o-3/build/h2o.jar:target/benchmarks.jar org.openjdk.jmh.Main -rf csv -rff perf.csv
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

    ConcurrentHashMap<String,Integer> chm = new ConcurrentHashMap<String,Integer>(524288);

    public static String getRandomKey() {
        UUID uid = UUID.randomUUID();
        long l1 = uid.getLeastSignificantBits();
        long l2 = uid. getMostSignificantBits();
        return "_"+Long.toHexString(l1)+Long.toHexString(l2);
    }

    @Setup(Level.Iteration)
    public void clearCHM() { chm.clear();}

    @TearDown(Level.Iteration)
    public void checkCHM() throws InterruptedException {
        if (chm.size() > 524288*.75) {
            System.out.println("CHM probably resized. Invalid experiment.");
            throw new InterruptedException();
        }
    }

    @Benchmark
    @Threads(value=1)
    public void chmPutTest1() { chm.put(getRandomKey(),0); }

    @Benchmark
    @Threads(value=2)
    public void chmPutTest2() { chm.put(getRandomKey(),0); }

    @Benchmark
    @Threads(value=4)
    public void chmPutTest4() { chm.put(getRandomKey(),0); }

    @Benchmark
    @Threads(value=8)
    public void chmPutTest8() { chm.put(getRandomKey(),0); }

    @Benchmark
    @Threads(value=16)
    public void chmPutTest16() { chm.put(getRandomKey(),0); }
}
