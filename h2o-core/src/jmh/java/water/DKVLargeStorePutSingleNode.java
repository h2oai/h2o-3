package water;

import org.openjdk.jmh.annotations.*;

import water.nbhm.NonBlockingHashMap;

import java.util.concurrent.TimeUnit;

@Fork(5)
@BenchmarkMode(Mode.AverageTime)
@Measurement(iterations=20, timeUnit=TimeUnit.MILLISECONDS, time=100)
@Warmup(iterations=10, timeUnit=TimeUnit.MILLISECONDS, time=10)
@OutputTimeUnit(value= TimeUnit.NANOSECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)

// Alternative setup. Only do 1 method invocation per iteration. Generate unique key prior to each interation.
//@Fork(3)
//@BenchmarkMode(Mode.AverageTime)
//@Measurement(iterations=1000)
//@Warmup(iterations=10)
//@OutputTimeUnit(value=TimeUnit.NANOSECONDS)
//@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class DKVLargeStorePutSingleNode {

    NonBlockingHashMap<String, Integer> nbhm = new NonBlockingHashMap<String, Integer>(4194304);

    @Setup(Level.Iteration)
    public void initNBMH() {
        nbhm.clear();
        for (int i=0; i<1000000; i++) nbhm.put(Key.rand(),0);
    }


    //@State(org.openjdk.jmh.annotations.Scope.Thread)
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
