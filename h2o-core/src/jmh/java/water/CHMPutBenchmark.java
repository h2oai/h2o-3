package water;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.ConcurrentHashMap;

@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class CHMPutBenchmark {

    ConcurrentHashMap<String,Integer> chm = new ConcurrentHashMap<String,Integer>(2097152);

    // prior to each jmh iteration, clear out the hash map
    @Setup(Level.Iteration)
    public void clearCHM() { chm.clear(); }

    // after each jmh iteration, make sure no resize operations took place because we don't want to measure these
    @TearDown(Level.Iteration)
    public void checkCHM() throws InterruptedException {
        if (chm.size() > 2097152*.75) {
            System.out.println("CHM probably resized. Invalid experiment.");
            throw new InterruptedException();
        }
    }

    // measure the amount of time it takes to do a put operation for various numbers of threads. note that we are also
    // measuring the amount of time it takes to conduct Key.rand() operation. i think this is okay because these puts
    // will be compared to h2o NBHM puts, and we do the same thing there. we could have avoided this by generating
    // random keys prior to each jmh invocation (Level.Invocation), but, per jmh docs, this is discouraged for very
    // short operations.
    @Benchmark
    @Threads(value=1)
    public void chmPutTest1() { chm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=2)
    public void chmPutTest2() { chm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=4)
    public void chmPutTest4() { chm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=8)
    public void chmPutTest8() { chm.put(Key.rand(),0); }

    @Benchmark
    @Threads(value=16)
    public void chmPutTest16() { chm.put(Key.rand(),0); }
}
