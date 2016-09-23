package water;

import org.openjdk.jmh.annotations.*;

import water.nbhm.NonBlockingHashMap;

@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class DKVPutBenchmarkSingleNode {

    NonBlockingHashMap<String, Integer> nbhm = new NonBlockingHashMap<String, Integer>(2097152);

    @Setup(Level.Iteration)
    public void clearNBHM() { nbhm.clear(); }

    @TearDown(Level.Iteration)
    public void checkNBHM() throws InterruptedException {
        int endIterStoreSize = (nbhm.kvs().length-2)>>1;
        if (2097152 < endIterStoreSize) {
            throw new InterruptedException("Looks like the nbhm was resized from "+2097152+" to "+
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
