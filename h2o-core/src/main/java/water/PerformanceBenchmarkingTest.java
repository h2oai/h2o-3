package water;

import com.timgroup.statsd.StatsDClient;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.NoOpStatsDClient;
import water.util.Log;

public class PerformanceBenchmarkingTest {


    public static final boolean STATSD_ENABLED = true;
    public static final String STATSD_PREFIX = "h2o.test.prefix";
    public static final String STATSD_HOST = "localhost";
    public static final int STATSD_PORT = 8125;

    private static StatsDClient statsd = null;

    public PerformanceBenchmarkingTest() {
        if (STATSD_ENABLED && !STATSD_HOST.isEmpty() && STATSD_PORT > 0) {
            statsd = new NonBlockingStatsDClient(STATSD_PREFIX, STATSD_HOST, STATSD_PORT);
            Log.info("Created client!");
        }
        else {
            statsd = new NoOpStatsDClient();
        }
    }

    public static final void main(String[] args) {
        Log.info("Creating client!");

        new PerformanceBenchmarkingTest();
        int i = 0;
        try {
            while (i < 10) {
                statsd.incrementCounter("bar");
                Thread.sleep(10000);
                i++;
                Log.info("Incremented bar!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

      //  statsd.recordGaugeValue("baz", 100);
      //  statsd.recordExecutionTime("bag", 25);
      //  statsd.recordSetEvent("qux", "one");
      //  statsd.count("test", 1);
    }

    @Override
    public void finalize() {
        if (statsd != null) {
            statsd.stop();
        }
    }
}