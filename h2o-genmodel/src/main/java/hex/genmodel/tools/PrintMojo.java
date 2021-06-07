package hex.genmodel.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.ConvertTreeOptions;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeGraphConverter;
import hex.genmodel.algos.tree.TreeBackedMojoModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.List;

import static water.util.JavaVersionUtils.JAVA_VERSION;

/**
 * Print dot (graphviz) representation of one or more trees in a DRF or GBM model.
 */
public class PrintMojo implements MojoPrinter {
  
  protected MojoModel genModel;
  protected Format format = Format.dot;
  protected int treeToPrint = -1;
  protected int maxLevelsToPrintPerEdge = 10;
  protected boolean detail = false;
  protected String outputFileName = null;
  protected String optionalTitle = null;
  protected PrintTreeOptions pTreeOptions;
  protected boolean internal;
  protected final String tmpOutputFileName = "tmpOutputFileName.gv";

  public static void main(String[] args) {
    MojoPrinter mojoPrinter = null;
    
    if (JAVA_VERSION.isKnown() && JAVA_VERSION.getMajor() > 7) {
      ServiceLoader<MojoPrinter> mojoPrinters = ServiceLoader.load(MojoPrinter.class);
      for (MojoPrinter printer : mojoPrinters) {
        if (printer.supportsFormat(getFormat(args))) {
          mojoPrinter = printer;
        }
      }
      if (mojoPrinter == null) {
        System.out.println("No supported MojoPrinter for the format required found. Please make sure you are using h2o-genmodel.jar for executing this tool.");
        System.exit(1);
      }
    } else {
      mojoPrinter = new PrintMojo();
    }

    // Parse command line arguments
    mojoPrinter.parseArgs(args);

    // Run the main program
    try {
      mojoPrinter.run();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(2);
    }

    // Success
    System.exit(0);
  }

  @Override
  public boolean supportsFormat(Format format) {
    if (Format.png.equals(format)){
      return false;
    } else {
      return true;
    }
  }
  
  static Format getFormat(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--format")) {
        try {
          return Format.valueOf(args[++i]);
        }
        catch (Exception e) {
          // invalid format will be handled in parseArgs()
          return null;
        }
      }
    }
    return null;
  }

  private void loadMojo(String modelName) throws IOException {
    genModel = MojoModel.load(modelName);
  }

  protected static void usage() {
    System.out.println("Emit a human-consumable graph of a model for use with dot (graphviz).");
    System.out.println("The currently supported model types are DRF, GBM and XGBoost.");
    System.out.println();
    System.out.println("Usage:  java [...java args...] hex.genmodel.tools.PrintMojo [--tree n] [--levels n] [--title sss] [-o outputFileName]");
    System.out.println();
    System.out.println("    --format        Output format. For .png output at least Java 8 is required.");
    System.out.println("                    dot|json|raw|png [default dot]");
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
    System.out.println("    --output | -o   Output filename. Taken as a directory name in case of .png format and multiple trees to visualize.");
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

  public void parseArgs(String[] args) {
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

  protected void validateArgs() {
    if (genModel == null) {
      System.out.println("ERROR: Must specify -i");
      usage();
    }
  }

  public void run() throws Exception {
    validateArgs();
    PrintStream os;
    if (outputFileName != null) {
      os = new PrintStream(new FileOutputStream(outputFileName));
    }
    else {
      os = System.out;
    }
    if (genModel instanceof SharedTreeGraphConverter) {
      SharedTreeGraphConverter treeBackedModel = (SharedTreeGraphConverter) genModel;
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
          if (!(treeBackedModel instanceof TreeBackedMojoModel)) {
            System.out.println("ERROR: Printing XGBoost MOJO as JSON not supported");
            System.exit(1);
          }
          printJson((TreeBackedMojoModel) treeBackedModel, g, os);
          break;
      }
    }
    else {
      System.out.println("ERROR: Unsupported MOJO type");
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
