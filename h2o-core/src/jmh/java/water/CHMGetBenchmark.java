package water;

import org.openjdk.jmh.annotations.*;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class CHMGetBenchmark {

    ConcurrentHashMap<String,Integer> chm = new ConcurrentHashMap<String,Integer>(131072);

    // prior to a jmh trial, put 1000 key, value pairs into the hash map
    @Setup(Level.Trial)
    public void initCHM() {
        for (int i=0; i<1000; i++) chm.put(Key.rand(), 0);
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class ThreadState {
        String[] keySet; // each thread has its own copy the the hash map's keys
        Random rand = new Random();

        // prior to a jmh trial, each thread will retrieve a copy of the hash map's (1000) keys
        @Setup(Level.Trial)
        public void getKeySet(CHMGetBenchmark bm) {
            Object[] oa = bm.chm.keySet().toArray();
            keySet = Arrays.copyOf(oa, oa.length, String[].class); }
    }

    // measure the amount of time it takes to get a value from the hash map (for a random key) for various numbers of
    // threads. note that we are also measuring the time for the ts.keySet[ts.rand.nextInt(ts.keySet.length)] operation.
    // i think this is okay because these gets will be compared to h2o NBHM gets, and we do the same thing there.
    @Benchmark
    @Threads(value=1)
    public Integer chmGetTest1(ThreadState ts) { return chm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=2)
    public Integer chmGetTest2(ThreadState ts) { return chm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=4)
    public Integer chmGetTest4(ThreadState ts) { return chm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=8)
    public Integer chmGetTest8(ThreadState ts) { return chm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=16)
    public Integer chmGetTest16(ThreadState ts) { return chm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }
}
