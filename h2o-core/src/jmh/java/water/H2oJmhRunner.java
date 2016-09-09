package water;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Defaults;
import org.openjdk.jmh.runner.NoBenchmarksException;
import org.openjdk.jmh.runner.ProfilersFailedException;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.Collection;

/**
 * H2O specific JMH runner which collect
 * results and communicates with a specified statsd server.
 *
 * Created by Surekha on 9/19/16.
 */
public class H2oJmhRunner {

  public static String UBENCH_REPORT_FILE = env("UBENCH_REPORT_FILE");

  private static String env(String key) {
    return env(key, null);
  }
  private static String env(String key, String defaultValue) {
    return System.getenv(key) != null ? System.getenv(key) : defaultValue;
  }

  /**
   * Copy of {@link org.openjdk.jmh.Main#main(java.lang.String[])}
   *
   * @param args command line parameters
   */
  public static void main(String[] args) {

    // Try to run benchmark and collect results
    try {
      CommandLineOptions cmdOptions = new CommandLineOptions(args);

      Runner runner = new Runner(cmdOptions);

      Collection<RunResult> results = null;

      try {
        results = runner.run();
        // Bench code passed so report results
        /*if (UBENCH_REPORT_FILE != null) {
          .writeOut(results);

          out.println("");
          out.println("Benchmark result is saved to " + resultFile);
        } */

      } catch (NoBenchmarksException e) {
        System.err.println("No matching benchmarks. Miss-spelled regexp?");

        if (cmdOptions.verbosity().orElse(Defaults.VERBOSITY) != VerboseMode.EXTRA) {
          System.err.println("Use " + VerboseMode.EXTRA + " verbose mode to debug the pattern matching.");
        } else {
          runner.list();
        }
        System.exit(1);
      } catch (ProfilersFailedException e) {
        // This is not exactly an error, set non-zero exit code
        System.err.println(e.getMessage());
        System.exit(1);
      } catch (RunnerException e) {
        System.err.print("ERROR: ");
        e.printStackTrace(System.err);
        System.exit(1);
      }
    } catch (CommandLineOptionException e) {
      System.err.println("Error parsing command line:");
      System.err.println(" " + e.getMessage());
      System.exit(1);
    }
  }
  /*
  private static ResultFormat getResultFormater(final File outputFile) {
    return new ResultFormat() {
      @Override
      public void writeOut(Collection<RunResult> results) {
        try {
          PrintStream pw = new PrintStream(outputFile, "UTF-8");
          ResultFormat rf = new H2OResultFormatter(pw, ",");
          rf.writeOut(results);
          pw.flush();
          pw.close();
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }
    };
  }*/
}

/*
class H2OResultFormatter implements ResultFormat {

  private final PrintStream out;
  private final String delimiter;

  public H2OResultFormatter(PrintStream out, String delimiter) {
    this.out = out;
    this.delimiter = delimiter;
  }

  @Override
  public void writeOut(Collection<RunResult> results) {
    SortedSet<String> params = new TreeSet<String>();
    for (RunResult res : results) {
      params.addAll(res.getParams().getParamsKeys());
    }

    for (RunResult rr : results) {
      BenchmarkParams benchParams = rr.getParams();
      Result res = rr.getPrimaryResult();

      printLine(benchParams.getBenchmark(), benchParams, params, res);

      for (String label : rr.getSecondaryResults().keySet()) {
        Result subRes = rr.getSecondaryResults().get(label);
        printLine(benchParams.getBenchmark() + ":" + subRes.getLabel(), benchParams, params, subRes);
      }
    }
  }
}
*/
