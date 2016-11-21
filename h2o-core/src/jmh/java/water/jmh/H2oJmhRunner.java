package water.jmh;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormat;
import org.openjdk.jmh.runner.Defaults;
import org.openjdk.jmh.runner.NoBenchmarksException;
import org.openjdk.jmh.runner.ProfilersFailedException;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.lang.System.out;

/**
 * H2O specific JMH runner which collect
 * results and communicates with a specified statsd server.
 *
 * Created by Surekha on 9/19/16.
 */
public class H2oJmhRunner {

  /** Git SHA of the commit which is tested. */
  public static String H2O_UBENCH_GIT_SHA = env("H2O_UBENCH_GIT_SHA", "NA");

  /** Date of this performance tests */
  public static String H2O_UBENCH_DATE = env("H2O_UBENCH_DATE", "NA");

  /** Output directory to report benchmarks results */
  public static String H2O_UBENCH_REPORT_FILE = env("H2O_UBENCH_REPORT_FILE");

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
        if (H2O_UBENCH_REPORT_FILE != null) {
          // Create an output directory
          File ubenchReportFile = new File(H2O_UBENCH_REPORT_FILE);
          getResultFormater(ubenchReportFile).writeOut(results);
        }

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

  private static ResultFormat getResultFormater(final File outputFile) {
    return new ResultFormat() {
      @Override
      public void writeOut(Collection<RunResult> results) {
        try {
          PrintStream pw = new PrintStream(outputFile, "UTF-8");
          ResultFormat rf = new H2OResultFormat(pw, ",", H2O_UBENCH_GIT_SHA, H2O_UBENCH_DATE);
          rf.writeOut(results);
          pw.flush();
          pw.close();
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }
    };
  }
}

// This is modified XSVResultFormat
class H2OResultFormat implements ResultFormat {

  private final PrintStream out;
  private final String delimiter;
  private final String sha;
  private final String date;

  public H2OResultFormat(PrintStream out, String delimiter, String sha, String date) {
    this.out = out;
    this.delimiter = delimiter;
    this.sha = sha;
    this.date = date;
  }

  @Override
  public void writeOut(Collection<RunResult> results) {
    SortedSet<String> params = new TreeSet<String>();
    for (RunResult res : results) {
      params.addAll(res.getParams().getParamsKeys());
    }

    printHeader(params);

    for (RunResult rr : results) {
      BenchmarkParams benchParams = rr.getParams();
      Result res = rr.getPrimaryResult();

      printLine(sha, date, benchParams.getBenchmark(), benchParams, params, res);

      for (String label : rr.getSecondaryResults().keySet()) {
        Result subRes = rr.getSecondaryResults().get(label);
        printLine(sha, date, benchParams.getBenchmark() + ":" + subRes.getLabel(), benchParams, params, subRes);
      }
    }
  }

  private void printHeader(SortedSet<String> params) {
    out.print("\"SHA\"");
    out.print(delimiter);
    out.print("\"Date\"");
    out.print(delimiter);
    out.print("\"Benchmark\"");
    out.print(delimiter);
    out.print("\"Mode\"");
    out.print(delimiter);
    out.print("\"Threads\"");
    out.print(delimiter);
    out.print("\"Samples\"");
    out.print(delimiter);
    out.print("\"Score\"");
    out.print(delimiter);
    out.printf("\"Score Error (%.1f%%)\"", 99.9);
    out.print(delimiter);
    out.print("\"Unit\"");
    out.print(delimiter);
    out.print("\"Params\"");
    out.print("\r\n");
  }

  private void printLine(String sha, String date, String label, BenchmarkParams benchmarkParams, SortedSet<String> params, Result result) {
    out.print("\"");
    out.print(sha);
    out.print("\"");
    out.print(delimiter);
    out.print("\"");
    out.print(date);
    out.print("\"");
    out.print(delimiter);
    out.print("\"");
    out.print(label);
    out.print("\"");
    out.print(delimiter);
    out.print("\"");
    out.print(benchmarkParams.getMode().shortLabel());
    out.print("\"");
    out.print(delimiter);
    out.print(emit(benchmarkParams.getThreads()));
    out.print(delimiter);
    out.print(emit(result.getSampleCount()));
    out.print(delimiter);
    out.print(emit(result.getScore()));
    out.print(delimiter);
    out.print(emit(result.getScoreError()));
    out.print(delimiter);
    out.print("\"");
    out.print(result.getScoreUnit());
    out.print("\"");
    out.print(delimiter);
    out.print("\"");
    if (params == null || params.isEmpty()) {
      out.print("NA");
    } else {
      boolean first = true;
      for (String p : params) {
        String v = benchmarkParams.getParam(p);
        if (v != null) {
          if (!first) out.print("|"); else first = false;
          out.print(String.format("%s:%s", p, emit(v)));
        }
      }
    }
    out.print("\"");
    out.print("\r\n");
  }

  private String emit(String v) {
    if (v.contains(delimiter) || v.contains(" ") || v.contains("\n") || v.contains("\r") || v.contains("\"")) {
      return v.replaceAll("\"", "\"\"");
    } else {
      return v;
    }
  }

  private String emit(int i) {
    return emit(String.format("%d", i));
  }

  private String emit(long l) {
    return emit(String.format("%d", l));
  }

  private String emit(double d) {
    return emit(String.format("%f", d));
  }

}
