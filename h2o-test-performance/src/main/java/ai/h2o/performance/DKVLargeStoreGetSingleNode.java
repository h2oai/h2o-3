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
import java.util.Random;
import java.util.Set;

@Fork(5)
@BenchmarkMode(Mode.AverageTime)
@Measurement(iterations=10, timeUnit=TimeUnit.SECONDS, time=5)
@Warmup(iterations=3, timeUnit=TimeUnit.SECONDS, time=3)
@OutputTimeUnit(value=TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class DKVLargeStoreGetSingleNode {

    @Setup(Level.Trial)
    public void initH2O() {
        // Start up h2o
        H2O.main(new String[] {"-name", Long.toString(System.currentTimeMillis()) });
        H2O.registerRestApis(System.getProperty("user.dir"));
        H2O.waitForCloudSize(1, 30000);

        // Load up 1,000,000 keys
        Key k;
        for (int i=0; i<1000000; i++) {
            k = Key.make();
            H2O.STORE.put(Key.make(), new Value(k, new IcedInt(0)));
        }
        Log.info("@Setup for DKVLargeStoreGetSingleNode Trial - Scope.Benchmark");
        Log.info("Done initializing the H2O.STORE. Number of actual keys: "+H2O.STORE.size());
    }

    @State(Scope.Thread)
    public static class ThreadState {
        Key tk;
        int invocations;

        @Setup(Level.Invocation)
        public void setKeyForGetOp() {
            // Pick a key from the H2O.STORE at random
            Set<Key> keySet = H2O.STORE.keySet();
            tk = (Key) keySet.toArray()[new Random().nextInt(keySet.size())];
            invocations += 1;
        }

        @Setup(Level.Iteration)
        public void initInvocations() { invocations = 0; }

        @TearDown(Level.Iteration)
        public void logInvocations() {
            Log.info("@TearDown for DKVLargeStoreGetSingleNode Iteration - Scope.Thread");
            Log.info("Number of benchmark method invocations for this thread's iteration: "+ invocations);
        }
    }

    @Benchmark
    @Threads(value=1)
    public Value largeStoreGetTest1(ThreadState ts) { return H2O.STORE.get(ts.tk); }

    @Benchmark
    @Threads(value=2)
    public Value largeStoreGetTest2(ThreadState ts) { return H2O.STORE.get(ts.tk); }

    @Benchmark
    @Threads(value=4)
    public Value largeStoreGetTest4(ThreadState ts) { return H2O.STORE.get(ts.tk); }

    @Benchmark
    @Threads(value=8)
    public Value largeStoreGetTest8(ThreadState ts) { return H2O.STORE.get(ts.tk); }

    @Benchmark
    @Threads(value=16)
    public Value largeStoreGetTest16(ThreadState ts) { return H2O.STORE.get(ts.tk); }
}
