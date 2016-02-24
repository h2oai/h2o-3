package water.nbhm;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class NonBlockingHashMapLongZeroKeyConcurrentGetPutIfAbsentTest extends TestCase {
    private static final int N_THREADS = 4;
    private static final int ITERATIONS = 100000;
    private ExecutorService service;

    protected void setUp() {
        service = Executors.newFixedThreadPool(N_THREADS);
    }

    protected void tearDown() throws Exception {
        service.shutdownNow();
        service.awaitTermination(1, TimeUnit.HOURS);
    }


    public void test_empty_map_key_0() throws Exception {
        empty_map_test(0);
    }

    public void test_empty_map_key_123() throws Exception {
        empty_map_test(123);
    }

    private void empty_map_test(long key) throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            Set<String> results = runIteration(new NonBlockingHashMapLong<String>(), key);
            assertEquals(results.toString(), 1, results.size());
        }
    }

    private Set<String> runIteration(NonBlockingHashMapLong<String> map, long key) throws Exception {
        List<Callable<String>> tasks = new ArrayList<Callable<String>>(N_THREADS);
        for (int i = 1; i <= N_THREADS; i++) {
            tasks.add(new PutIfAbsent(map, key, "worker #" + i));
        }

        List<String> results = executeTasks(tasks);

        return new HashSet<String>(results);
    }

    private List<String> executeTasks(List<Callable<String>> tasks) throws Exception {
        List<Future<String>> futures = new ArrayList<Future<String>>(N_THREADS);
        final CountDownLatch startLatch = new CountDownLatch(N_THREADS + 1);
        for (Callable<String> t : tasks) {
            final Callable<String> task = t;
            Future<String> future = service.submit(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    startLatch.countDown();
                    startLatch.await();
                    return task.call();
                }
            });
            futures.add(future);
        }

        startLatch.countDown();

        List<String> results = new ArrayList<String>(N_THREADS);
        for (Future<String> f : futures) {
            results.add(f.get());
        }
        return results;
    }

    private static class PutIfAbsent implements Callable<String> {
        private final NonBlockingHashMapLong<String> map;
        private final String newValue;
        private final long key;

        public PutIfAbsent(NonBlockingHashMapLong<String> map, long key, String newValue) {
            this.map = map;
            this.newValue = newValue;
            this.key = key;
        }

        @Override
        public String call() throws Exception {
            String value = map.get(key);
            if (value == null) {
                value = newValue;
                String tmp = map.putIfAbsent(key, value);
                if (tmp != null) {
                    return tmp;
                }
            }
            return value;
        }
    }
}
