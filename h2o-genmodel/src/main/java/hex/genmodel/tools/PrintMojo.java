package hex.genmodel.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.ConvertTreeOptions;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.TreeBackedMojoModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * Print dot (graphviz) representation of one or more trees in a DRF or GBM model.
 */
public class PrintMojo {
  
  enum Format {
    dot, json, raw
  }
  
  private MojoModel genModel;
  private Format format = Format.dot;
  private int treeToPrint = -1;
  private int maxLevelsToPrintPerEdge = 10;
  private boolean detail = false;
  private String outputFileName = null;
  private String optionalTitle = null;
  private PrintTreeOptions pTreeOptions;
  private boolean internal;

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
    System.out.println("The currently supported model types are DRF, GBM and XGBoost.");
    System.out.println();
    System.out.println("Usage:  java [...java args...] hex.genmodel.tools.PrintMojo [--tree n] [--levels n] [--title sss] [-o outputFileName]");
    System.out.println();
    System.out.println("    --format        Output format.");
    System.out.println("                    dot|json|raw [default dot]");
    System.out.println();
    System.out.println("    --tree          Tree number to print.");
    System.out.println("                    [default all]");
    System.out.println();
    System.out.println("    --levels        Number of levels per edge to print.");
    System.out.println("                    [default 10]");
    System.out.println();
    System.out.println("    --title         (Optional) Force title of tree graph.");
    System.out.println();
    System.out.println("    --detail        Specify to print additional detailed information like node numbers.");
    System.out.println();
    System.out.println("    --input | -i    Input mojo file.");
    System.out.println();
    System.out.println("    --output | -o   Output dot filename.");
    System.out.println("                    [default stdout]");
    System.out.println("    --decimalplaces | -d    Set decimal places of all numerical values.");
    System.out.println();
    System.out.println("    --fontsize | -f    Set font sizes of strings.");
    System.out.println();
    System.out.println("    --internal    Internal H2O representation of the decision tree (splits etc.) is used for generating the GRAPHVIZ format.");
    System.out.println();
    System.out.println();
    System.out.println("Example:");
    System.out.println();
    System.out.println("    (brew install graphviz)");
    System.out.println("    java -cp h2o.jar hex.genmodel.tools.PrintMojo --tree 0 -i model_mojo.zip -o model.gv -f 20 -d 3");
    System.out.println("    dot -Tpng model.gv -o model.png");
    System.out.println("    open model.png");
    System.out.println();
    System.exit(1);
  }

  private void parseArgs(String[] args) {
    int nPlaces = -1;
    int fontSize = 14; // default size is 14
    boolean setDecimalPlaces = false;
    try {
      for (int i = 0; i < args.length; i++) {
        String s = args[i];
        switch (s) {
          case "--format":
            i++;
            if (i >= args.length) usage();
            s = args[i];
            try {
              format = Format.valueOf(s);
            }
            catch (Exception e) {
              System.out.println("ERROR: invalid --format argument (" + s + ")");
              System.exit(1);
            }
            break;

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

          case "--fontsize":
          case "-f":
            i++;
            if (i >= args.length) usage();
            s = args[i];
            fontSize = Integer.parseInt(s);
            break;

          case "--decimalplaces":
          case "-d":
            i++;
            if (i >= args.length) usage();
            setDecimalPlaces=true;
            s = args[i];
            nPlaces = Integer.parseInt(s);
            break;

          case "--raw":
            format = Format.raw;
            break;

          case "--internal":
            internal = true;
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
      pTreeOptions = new PrintTreeOptions(setDecimalPlaces, nPlaces, fontSize, internal);
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
    if (genModel instanceof TreeBackedMojoModel){
      TreeBackedMojoModel treeBackedModel = (TreeBackedMojoModel) genModel;
      ConvertTreeOptions options = new ConvertTreeOptions().withTreeConsistencyCheckEnabled();
      final SharedTreeGraph g = treeBackedModel.convert(treeToPrint, null, options);
      switch (format) {
        case raw:
          g.print();
          break;
        case dot:
          g.printDot(os, maxLevelsToPrintPerEdge, detail, optionalTitle, pTreeOptions);
          break;
        case json:
          printJson(treeBackedModel, g, os);
          break;
      }
    }
    else {
      System.out.println("ERROR: Unknown MOJO type");
      System.exit(1);
    }
  }
  
  private Map<String, Object> getParamsAsJson(TreeBackedMojoModel tree) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("h2o_version", genModel._h2oVersion);
    params.put("mojo_version", genModel._mojo_version);
    params.put("algo", genModel._algoName);
    params.put("model_category", genModel._category.toString());
    params.put("classifier", genModel.isClassifier());
    params.put("supervised", genModel._supervised);
    params.put("nfeatures", genModel._nfeatures);
    params.put("nclasses", genModel._nclasses);
    params.put("balance_classes", genModel._balanceClasses);
    params.put("n_tree_groups", tree.getNTreeGroups());
    params.put("n_trees_in_group", tree.getNTreesPerGroup());
    params.put("base_score", tree.getInitF());
    if (genModel.isClassifier()) {
      String[] responseValues = genModel.getDomainValues(genModel.getResponseIdx());
      params.put("class_labels", responseValues);
    }
    if (genModel instanceof GbmMojoModel) {
      GbmMojoModel m = (GbmMojoModel) genModel;
      params.put("family", m._family.toString());
      params.put("link_function", m._link_function.toString());
    }
    return params;
  }
  
  private List<Object> getDomainValuesAsJSON() {
    List<Object> domainValues = new ArrayList<>();
    String[][] values = genModel.getDomainValues();
    // each col except response
    for (int i = 0; i < values.length-1; i++) {
      if (values[i] == null) continue;
      Map<String, Object> colValuesObject = new LinkedHashMap<>();
      colValuesObject.put("colId", i);
      colValuesObject.put("colName", genModel._names[i]);
      colValuesObject.put("values", values[i]);
      domainValues.add(colValuesObject);
    }
    return domainValues;
  }
  
  private void printJson(TreeBackedMojoModel mojo, SharedTreeGraph trees, PrintStream os) {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("params", getParamsAsJson(mojo));
    json.put("domainValues", getDomainValuesAsJSON());
    json.put("trees", trees.toJson());
    if (optionalTitle != null) {
      json.put("title", optionalTitle);
    }
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    os.print(gson.toJson(json));
  }

  public static class PrintTreeOptions {
    public boolean _setDecimalPlace;
    public int _nPlaces;
    public int _fontSize;
    public boolean _internal;

    public PrintTreeOptions(boolean setdecimalplaces, int nplaces, int fontsize, boolean internal) {
      _setDecimalPlace = setdecimalplaces;
      _nPlaces = _setDecimalPlace ? nplaces : _nPlaces;
      _fontSize = fontsize;
      _internal = internal;
    }

    public float roundNPlace(float value) {
      if (_nPlaces < 0)
        return value;
      double sc = Math.pow(10, _nPlaces);
      return (float) (Math.round(value*sc)/sc);
    }
  }
}
