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

import water.H2O;
import water.Key;
import water.Value;
import water.util.IcedInt;
import water.util.Log;

import java.util.concurrent.TimeUnit;

@Fork(5)
@BenchmarkMode(Mode.AverageTime)
@Measurement(iterations=10, timeUnit=TimeUnit.MILLISECONDS, time=250)
@Warmup(iterations=2, timeUnit=TimeUnit.MILLISECONDS, time=5)
@OutputTimeUnit(value= TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class DKVLargeStorePutSingleNode {

    /* The size of the store at the beginning of each benchmark iteration. */
    int startIterStoreSize;

    @Setup(Level.Trial)
    public void startH2O() {
        H2O.main(new String[] {"-name", Long.toString(System.currentTimeMillis()) });
        H2O.registerRestApis(System.getProperty("user.dir"));
        H2O.waitForCloudSize(1, 30000);
    }

    @Setup(Level.Iteration)
    public void initNBHM() {
        /* Clear out the H2O.STORE */
        Log.info("@Setup for DKVLargeStorePutSingleNode Iteration - Scope.Benchmark");
        Log.info("Empting the H2O.STORE.");
        H2O.STORE.clear();

        /* Put a million keys in the H2O.STORE */
        Key k;
        startIterStoreSize = ((H2O.STORE.kvs().length-2)>>1);
        while (startIterStoreSize < 1000000) {
            k = Key.make();
            H2O.STORE.put(k, new Value(k, new IcedInt(0)));
            startIterStoreSize = ((H2O.STORE.kvs().length-2)>>1);
        }
        Log.info("Done initializing the H2O.STORE. Number of allowed keys: "+startIterStoreSize+
                ". Number of actual keys: "+H2O.STORE.size()+".");
    }

    @TearDown(Level.Iteration)
    public void checkNBHM() throws InterruptedException {
        Log.info("@TearDown for DKVLargeStorePutSingleNode Iteration - Scope.Benchmark");
        Log.info("Checking the H2O.STORE. Number of actual keys: "+H2O.STORE.size()+". ");
        /* Check that the H2O.STORE has not resized. If the H2O.STORE has resized, then throw an
        *  InterruptedException. */
        int endIterStoreSize = (H2O.STORE.kvs().length-2)>>1;
        if (startIterStoreSize < endIterStoreSize) {
            Log.err("H2O.STORE resized.");
            throw new InterruptedException("Looks like the H2O.STORE was resized from "+startIterStoreSize+" to "+
                    endIterStoreSize+". For this benchmark, we want to avoid this.");
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        Key k;
        Value v;
        int invocations;

        @Setup(Level.Invocation)
        public void setKeyValue() {
            k = Key.make();
            v = new Value(k, new IcedInt(0));
            invocations += 1;
        }

        @Setup(Level.Iteration)
        public void initInvocations() { invocations = 0; }

        @TearDown(Level.Iteration)
        public void logInvocations() {
            Log.info("@TearDown for DKVLargeStorePutSingleNode Iteration - Scope.Thread");
            Log.info("Number of method invocations for this thread: "+ invocations);
        }
    }

    @Benchmark
    @Threads(value=1)
    public void largeStorePutTest1(ThreadState ts) { H2O.STORE.put(ts.k,ts.v); }

    @Benchmark
    @Threads(value=2)
    public void largeStorePutTest2(ThreadState ts) { H2O.STORE.put(ts.k,ts.v); }

    @Benchmark
    @Threads(value=4)
    public void largeStorePutTest4(ThreadState ts) { H2O.STORE.put(ts.k,ts.v); }

    @Benchmark
    @Threads(value=8)
    public void largeStorePutTest8(ThreadState ts) { H2O.STORE.put(ts.k,ts.v); }

    @Benchmark
    @Threads(value=16)
    public void largeStorePutTest16(ThreadState ts) { H2O.STORE.put(ts.k,ts.v); }
}
