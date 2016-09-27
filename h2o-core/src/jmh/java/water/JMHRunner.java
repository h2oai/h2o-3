package water;

import com.timgroup.statsd.NoOpStatsDClient;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.util.Log;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Created by Surekha on 9/19/16.
 */
public class JMHRunner {

    private static int NUM_FORK = 5;
    private static String INCLUDE = ".*";
    // private static String INCLUDE = ".*CHMGet.*chmGetTest4.*";
    private static int NUM_WARMUP_ITERATIONS = 10;
    private static int NUM_MEASUREMENT_ITERATIONS = 20;
    private static boolean STATSD_ENABLED = true;
    private static String STATSD_PREFIX = "h2o.core";
    private static String STATSD_HOST = "localhost";
    private static int STATSD_PORT = 8125;

    public static StatsDClient statsd = null;

    private static void updateConfig(String[] args) {
        for (String arg: args) {
            if (arg.startsWith(Arguments.OPTION_DISABLE_STATSD)) {
                STATSD_ENABLED = false;
            } else if (arg.startsWith(Arguments.OPTION_ENABLE_STATSD)) {
                STATSD_ENABLED = true;
            }

            if (arg.startsWith(Arguments.STATSD_HOST_PREFIX)) {
                STATSD_HOST = arg.substring(Arguments.STATSD_HOST_PREFIX.length());
                System.out.println("Setting statsd host to: " + STATSD_HOST);
            }

            if (arg.startsWith(Arguments.STATSD_PORT_PREFIX)) {
                try {
                    STATSD_PORT = Integer.parseInt(arg.substring(Arguments.STATSD_PORT_PREFIX.length()));
                    System.out.println("Setting statsd port to: " + STATSD_PORT);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid port number. Setting statsd port to default value: " + STATSD_PORT);
                    Log.info("Invalid port number. Setting statsd port to default value: " + STATSD_PORT);
                }
            }
        }
    }

    private static void displayHelp() {
        System.out.println("\nUSAGE: gradle benchmark '-PjmhArgs=[options...]'");
        System.out.println("EXAMPLE: gradle benchmark '-PjmhArgs=-stastd_host=localhost,-statsd_port=8125'");
        System.out.println("List of options available:");
        System.out.printf("%-30sEnable sending metrics to statsd\n", Arguments.OPTION_ENABLE_STATSD);
        System.out.printf("%-30sDisable sending metrics to statsd\n", Arguments.OPTION_DISABLE_STATSD);
        System.out.printf("%-30sDisplay help options\n", Arguments.OPTION_HELP);
        System.out.printf("%-30sStatsd hostname\n", Arguments.STATSD_HOST_PREFIX);
        System.out.printf("%-30sStatsd port number\n", Arguments.STATSD_PORT_PREFIX);
    }

    private static void executeThroughputBenchmarking() {
        try {
            Options opt = new OptionsBuilder()
                    .include(INCLUDE)
                    .warmupIterations(NUM_WARMUP_ITERATIONS)
                 // .warmupTime(TimeValue.milliseconds(100))
                    .measurementIterations(NUM_MEASUREMENT_ITERATIONS)
                 // .measurementTime(TimeValue.milliseconds(1000))
                    .mode(Mode.Throughput)
                    .timeUnit(TimeUnit.SECONDS)
                    .forks(NUM_FORK)
                    .build();

            Runner r = new Runner(opt);
            Collection<RunResult> results = r.run();

            for (RunResult runResult : results) {
                // Result result = runResult.getPrimaryResult();
                // System.out.printf("%nJTA (%s) benchmark score: %f %s over %d iterations%n",
                //         runResult.getParams().getBenchmark(), result.getScore(), result.getScoreUnit(), result.getStatistics().getN());
                // statsd.count(runResult.getParams().getBenchmark() + "." + runResult.getParams().getMode().toString(), Math.round(runResult.getPrimaryResult().getScore()));
                statsd.gauge(runResult.getParams().getBenchmark(), Math.round(runResult.getPrimaryResult().getScore()));
                System.out.println(runResult.getParams().getBenchmark() + " Sent throughput metrics to statsd with score " +runResult.getPrimaryResult().getScore());
            }
        } catch (RunnerException e) {
            Log.info("Exception occurred while running benchmark tests for Throughput.");
        }
    }

    private static void executeAverageTimeBenchmarking() {
        try {
            Options opt = new OptionsBuilder()
                    .include(INCLUDE)
                    .warmupIterations(NUM_WARMUP_ITERATIONS)
                 // .warmupTime(TimeValue.milliseconds(100))
                    .measurementIterations(NUM_MEASUREMENT_ITERATIONS)
                 // .measurementTime(TimeValue.milliseconds(1000))
                    .mode(Mode.AverageTime)
                    .timeUnit(TimeUnit.NANOSECONDS)
                    .forks(NUM_FORK)
                    .build();

            Runner r = new Runner(opt);
            Collection<RunResult> results = r.run();
            for (RunResult runResult : results) {
                // Result result = runResult.getPrimaryResult();
                // System.out.printf("%nJTA (%s) benchmark score: %f %s over %d iterations%n",
                //         runResult.getParams().getBenchmark(), result.getScore(), result.getScoreUnit(), result.getStatistics().getN());
                // statsd.count(runResult.getParams().getBenchmark() + "." + runResult.getParams().getMode().toString(), Math.round(runResult.getPrimaryResult().getScore()));
                statsd.recordExecutionTime(runResult.getParams().getBenchmark(), Math.round(runResult.getPrimaryResult().getScore()));
                System.out.println(runResult.getParams().getBenchmark() + " Sent avg time metrics to statsd with score " + runResult.getPrimaryResult().getScore());
            }
        } catch (RunnerException e) {
            Log.info("Exception occurred while running benchmark tests for Throughput.");
        }
    }

    public static void main(String[] args) {

        // Show help
        if (args.length == 1 && args[0].equalsIgnoreCase(Arguments.OPTION_HELP)) {
            displayHelp();
            System.exit(0);
        }

        // Update statsd config parameters
        if (args.length > 0) {
            updateConfig(args);
        }

        // Create new stats client
        if (STATSD_ENABLED && !STATSD_HOST.isEmpty() && STATSD_PORT > 0) {
            statsd = new NonBlockingStatsDClient(STATSD_PREFIX, STATSD_HOST, STATSD_PORT);
            System.out.println("Created statsd client for: " + STATSD_HOST + ":" + STATSD_PORT);
        }
        else {
            statsd = new NoOpStatsDClient();
        }
      //   while (true) {
            // Run benchmarks for throughput and send metrics to statsd
            executeThroughputBenchmarking();

            // Run benchmarks for average time and send metrics to statsd
            executeAverageTimeBenchmarking();
      //  }
    }

    public static class Arguments
    {
        public static final String OPTION_HELP 		            = "-h";
        public static final String OPTION_ENABLE_STATSD 		= "-enable_statsd";
        public static final String OPTION_DISABLE_STATSD 		= "-disable_statsd";
        public static final String STATSD_HOST_PREFIX   		= "-statsd_host=";
        public static final String STATSD_PORT_PREFIX 	    	= "-statsd_port=";
    }

}
