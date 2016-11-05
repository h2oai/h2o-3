package hex.genmodel.tools;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.drf.DrfMojoModel;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.algos.tree.Graph;

import java.io.IOException;

/**
 * Print dot (graphviz) representation of one or more trees in a DRF or GBM model.
 */
public class PrintMojo {
  private GenModel genModel;
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
        if (s.equals("--input") || s.equals("-i")) {
          i++;
          if (i >= args.length) usage();
          s = args[i];
          loadMojo(s);
        }
        else if (s.equals("--dot")) {
          printDot = true;
          printPng = false;
        }
        else if (s.equals("--png")) {
          printDot = false;
          printPng = true;
        }
        else if ((s.equals("-o") || s.equals("--output"))) {
          i++;
          if (i >= args.length) usage();
          outputFileName = args[i];
        }
        else {
          System.out.println("ERROR: Unknown command line argument: " + s);
          usage();
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
    if (genModel instanceof GbmMojoModel) {
      Graph g = ((GbmMojoModel) genModel).computeGraph();
      g.print();
    }
    else if (genModel instanceof DrfMojoModel) {
      Graph g = ((DrfMojoModel) genModel).computeGraph();
      g.print();
    }
    else {
      System.out.println("ERROR: Unknown MOJO type");
      System.exit(1);
    }
  }
}
