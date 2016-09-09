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
public class DKVPutBenchmarkSingleNode {

    NonBlockingHashMap<String, Integer> nbhm = new NonBlockingHashMap<String, Integer>(524288);

    @Setup(Level.Iteration)
    public void clearNBHM() { nbhm.clear(); }

    @TearDown(Level.Iteration)
    public void checkNBHM() throws InterruptedException {
        int endIterStoreSize = (nbhm.kvs().length-2)>>1;
        if (524288 < endIterStoreSize) {
            throw new InterruptedException("Looks like the nbhm was resized from "+524288+" to "+
                    endIterStoreSize+". For this benchmark, we want to avoid this.");
        }
    }

    @Benchmark
    @Threads(value=1)
    public void putTest1() { nbhm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=2)
    public void putTest2() { nbhm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=4)
    public void putTest4() { nbhm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=8)
    public void putTest8() { nbhm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=16)
    public void putTest16() { nbhm.put(Key.rand(),0); }
}
