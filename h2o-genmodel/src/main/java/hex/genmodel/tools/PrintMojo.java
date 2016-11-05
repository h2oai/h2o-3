package hex.genmodel.tools;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.drf.DrfMojoModel;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.algos.tree.Graph;

import java.io.*;

/**
 * Print dot (graphviz) representation of one or more trees in a DRF or GBM model.
 */
public class PrintMojo {
  private GenModel genModel;
  private boolean printRaw = false;
  private boolean printDot = true;
  private boolean printPng = false;
  private String outputFileName = null;

  public static void main(String[] args) {
    // Parse command line arguments
    PrintMojo main = new PrintMojo();
    main.parseArgs(args);

    // Run the main program
    try {
      main.run();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(2);
    }
    // Predictions were successfully generated.
    System.exit(0);
  }

  private void loadMojo(String modelName) throws IOException {
    genModel = MojoModel.load(modelName);
  }

  private static void usage() {
    System.out.println("");
    System.out.println("Usage:  java [...java args...] hex.genmodel.tools.PrintMojo [(--dot | --png)] [-o outputFileName]");
    System.out.println("");
    System.out.println("     --input | -i    Input mojo file.");
    System.out.println("     --dot           Generate dot (graphviz) output.  [default]");
    System.out.println("     --png           Generate png output (requires graphviz).");
    System.out.println("     --output | -o   Output filename.  [default stdout]");
    System.out.println("");
    System.exit(1);
  }

  private void parseArgs(String[] args) {
    try {
      for (int i = 0; i < args.length; i++) {
        String s = args[i];
        switch (s) {
          case "--input":
          case "-i":
            i++;
            if (i >= args.length) usage();
            s = args[i];
            loadMojo(s);
            break;
          case "--raw":
            printRaw = true;
            break;
          case "--dot":
            printRaw = false;
            printDot = true;
            printPng = false;
            break;
          case "--png":
            printRaw = false;
            printDot = false;
            printPng = true;
            break;
          case "-o":
          case "--output":
            i++;
            if (i >= args.length) usage();
            outputFileName = args[i];
            break;
          default:
            System.out.println("ERROR: Unknown command line argument: " + s);
            usage();
            break;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      usage();
    }
  }

  private void validateArgs() {
    if (genModel == null) {
      System.out.println("ERROR: Must specify -i");
      usage();
    }
  }

  private void run() throws Exception {
    validateArgs();

    PrintStream os;
    if (outputFileName != null) {
      os = new PrintStream(new FileOutputStream(new File(outputFileName)));
    }
    else {
      os = System.out;
    }

    if (genModel instanceof GbmMojoModel) {
      Graph g = ((GbmMojoModel) genModel).computeGraph();
      if (printRaw) {
        g.print();
      }
      if (printDot) {
        g.printDot(os);
      }
    }
    else if (genModel instanceof DrfMojoModel) {
      Graph g = ((DrfMojoModel) genModel).computeGraph();
      if (printRaw) {
        g.print();
      }
      if (printDot) {
        g.printDot(os);
      }
    }
    else {
      System.out.println("ERROR: Unknown MOJO type");
      System.exit(1);
    }

    if (printPng) {
      System.out.println("ERROR: --png not yet implemented");
      System.exit(1);
    }
  }
}
