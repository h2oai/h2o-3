package water;

import org.openjdk.jmh.annotations.*;

import water.nbhm.NonBlockingHashMap;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@Fork(5)
@BenchmarkMode(Mode.AverageTime)
@Measurement(iterations=20)
@Warmup(iterations=10)
@OutputTimeUnit(value= TimeUnit.NANOSECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class DKVGetBenchmarkSingleNode {

    NonBlockingHashMap<String, Integer> nbhm = new NonBlockingHashMap<String, Integer>(131072);

    @Setup(Level.Trial)
    public void initNBMH() { for (int i=0; i<1000; i++) nbhm.put(Key.rand(),0); }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class ThreadState {
        String[] keySet;
        Random rand = new Random();

        @Setup(Level.Trial)
        public void getKeySet(DKVGetBenchmarkSingleNode bm) {
            Object[] oa = bm.nbhm.keySet().toArray();
            keySet = Arrays.copyOf(oa, oa.length, String[].class);
        }
    }

    @Benchmark
    @Threads(value=1)
    public Integer getTest1(ThreadState ts) { return nbhm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=2)
    public Integer getTest2(ThreadState ts) { return nbhm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=4)
    public Integer getTest4(ThreadState ts) { return nbhm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=8)
    public Integer getTest8(ThreadState ts) { return nbhm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }

    @Benchmark
    @Threads(value=16)
    public Integer getTest16(ThreadState ts) { return nbhm.get(ts.keySet[ts.rand.nextInt(ts.keySet.length)]); }
}
