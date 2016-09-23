package water;

import org.openjdk.jmh.annotations.*;

import water.nbhm.NonBlockingHashMap;

import java.util.Arrays;
import java.util.Random;

@State(org.openjdk.jmh.annotations.Scope.Benchmark)

// Alternative setup. Only do 1 method invocation per iteration. Generate unique key prior to each interation.
//@Fork(5)
//@BenchmarkMode(Mode.SingleShotTime)
//@Measurement(iterations=10000)
//@Warmup(iterations=100)
//@OutputTimeUnit(value=TimeUnit.NANOSECONDS)
//@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class DKVLargeStoreGetSingleNode {

    NonBlockingHashMap<String, Integer> nbhm = new NonBlockingHashMap<String, Integer>(4194304);

    @Setup(Level.Trial)
    public void initNBMH() { for (int i=0; i<1000000; i++) nbhm.put(Key.rand(),0); }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class ThreadState {
        String[] keySet; // each thread has its own copy the the hash map's keys
        Random rand = new Random();

        // prior to a jmh trial, each thread will retrieve a copy of the hash map's (1,000,000) keys
        @Setup(Level.Trial)
        public void getKeySet(DKVLargeStoreGetSingleNode bm) {
            Object[] oa = bm.nbhm.keySet().toArray();
            keySet = Arrays.copyOf(oa, oa.length, String[].class); }
    }

    //@State(org.openjdk.jmh.annotations.Scope.Thread)
    //public static class ThreadState {
    //    String[] keySet;
    //    Random rand = new Random();
    //    String key;
    //
    //    @Setup(Level.Trial)
    //    public void getKeySet(DKVLargeStoreGetSingleNode bm) {
    //        Object[] oa = bm.nbhm.keySet().toArray();
    //        keySet = Arrays.copyOf(oa, oa.length, String[].class);
    //    }
    //
    //    @Setup(Level.Iteration)
    //    public void getKey() { key = keySet[rand.nextInt(keySet.length)]; }
    //}

    @Benchmark
    @Threads(value=1)
    public Integer largeStoreGetTest1(ThreadState ts) { return nbhm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=2)
    public Integer largeStoreGetTest2(ThreadState ts) { return nbhm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=4)
    public Integer largeStoreGetTest4(ThreadState ts) { return nbhm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=8)
    public Integer largeStoreGetTest8(ThreadState ts) { return nbhm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=16)
    public Integer largeStoreGetTest16(ThreadState ts) { return nbhm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }
}
