package hex.genmodel.tools;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.drf.DrfMojoModel;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.algos.tree.SharedTreeGraph;

import java.io.*;

/**
 * Print dot (graphviz) representation of one or more trees in a DRF or GBM model.
 */
public class PrintMojo {
  private GenModel genModel;
  private static boolean printRaw = false;
  private static int treeToPrint = -1;
  private static int maxLevelsToPrintPerEdge = 10;
  private static boolean detail = false;
  private static String outputFileName = null;
  private static String optionalTitle = null;

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

    // Success
    System.exit(0);
  }

  private void loadMojo(String modelName) throws IOException {
    genModel = MojoModel.load(modelName);
  }

  private static void usage() {
    System.out.println("Emit a human-consumable graph of a model for use with dot (graphviz).");
    System.out.println("The currently supported model types are DRF and GBM.");
    System.out.println("");
    System.out.println("Usage:  java [...java args...] hex.genmodel.tools.PrintMojo [--tree n] [--levels n] [--title sss] [-o outputFileName]");
    System.out.println("");
    System.out.println("    --tree          Tree number to print.");
    System.out.println("                    [default all]");
    System.out.println("");
    System.out.println("    --levels        Number of levels per edge to print.");
    System.out.println("                    [default " + maxLevelsToPrintPerEdge + "]");
    System.out.println("");
    System.out.println("    --title         (Optional) Force title of tree graph.");
    System.out.println("");
    System.out.println("    --detail        Specify to print additional detailed information like node numbers.");
    System.out.println("");
    System.out.println("    --input | -i    Input mojo file.");
    System.out.println("");
    System.out.println("    --output | -o   Output dot filename.");
    System.out.println("                    [default stdout]");
    System.out.println("");
    System.out.println("Example:");
    System.out.println("");
    System.out.println("    (brew install graphviz)");
    System.out.println("    java -cp h2o.jar hex.genmodel.tools.PrintMojo --tree 0 -i model_mojo.zip -o model.gv");
    System.out.println("    dot -Tpng model.gv -o model.png");
    System.out.println("    open model.png");
    System.out.println("");
    System.exit(1);
  }

  private void parseArgs(String[] args) {
    try {
      for (int i = 0; i < args.length; i++) {
        String s = args[i];
        switch (s) {
          case "--tree":
            i++;
            if (i >= args.length) usage();
            s = args[i];
            try {
              treeToPrint = Integer.parseInt(s);
            }
            catch (Exception e) {
              System.out.println("ERROR: invalid --tree argument (" + s + ")");
              System.exit(1);
            }
            break;

          case "--levels":
            i++;
            if (i >= args.length) usage();
            s = args[i];
            try {
              maxLevelsToPrintPerEdge = Integer.parseInt(s);
            }
            catch (Exception e) {
              System.out.println("ERROR: invalid --levels argument (" + s + ")");
              System.exit(1);
            }
            break;

          case "--title":
            i++;
            if (i >= args.length) usage();
            optionalTitle = args[i];
            break;

          case "--detail":
            detail = true;
            break;

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
      SharedTreeGraph g = ((GbmMojoModel) genModel)._computeGraph(treeToPrint);
      if (printRaw) {
        g.print();
      }
      g.printDot(os, maxLevelsToPrintPerEdge, detail, optionalTitle);
    }
    else if (genModel instanceof DrfMojoModel) {
      SharedTreeGraph g = ((DrfMojoModel) genModel)._computeGraph(treeToPrint);
      if (printRaw) {
        g.print();
      }
      g.printDot(os, maxLevelsToPrintPerEdge, detail, optionalTitle);
    }
    else {
      System.out.println("ERROR: Unknown MOJO type");
      System.exit(1);
    }
  }
}
