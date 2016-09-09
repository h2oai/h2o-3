package water;

import org.openjdk.jmh.annotations.*;
import water.Key;

import java.util.concurrent.ConcurrentHashMap;
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
//@State(Scope.Benchmark)
public class CHMLargePutBenchmark {

    ConcurrentHashMap<String,Integer> chm = new ConcurrentHashMap<String,Integer>(4194304);

    @Setup(Level.Iteration)
    // prior to each jmh iteration, clear out the hash map and load up 1,000,000 keys
    public void initCHM() {
        chm.clear();
        for (int i=0; i<1000000; i++) chm.put(Key.rand(),0);
    }

    //@State(org.openjdk.jmh.annotations.Scope.Thread)
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
