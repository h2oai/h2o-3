package hex.genmodel.tools;

import hex.genmodel.MojoPipelineBuilder;

import java.io.File;
import java.util.*;

public class BuildPipeline {

  private File _output;
  private Map<String, File> _input;
  private List<MojoPipelineBuilder.MappingSpec> _mappings;

  public static void main(String[] args) {
    // Parse command line arguments
    BuildPipeline main = new BuildPipeline();
    main.parseArgs(args);

    // Run the main program
    try {
      main.run();
    } catch (Exception e) {
      System.err.println("ERROR: " + e.getMessage());
      e.printStackTrace();
      System.exit(2);
    }
  }

  private void run() throws Exception {
    String mainModelAlias = findMainModel();
    MojoPipelineBuilder builder = new MojoPipelineBuilder();
    for (Map.Entry<String, File> e : _input.entrySet()) {
      if (! mainModelAlias.equals(e.getKey())) {
        builder.addModel(e.getKey(), e.getValue());
      }
    }
    builder
            .addMappings(_mappings)
            .addMainModel(mainModelAlias, _input.get(mainModelAlias))
            .buildPipeline(_output);
  }

  private String findMainModel() {
    Set<String> subModels = new HashSet<>();
    for (MojoPipelineBuilder.MappingSpec spec : _mappings) {
      subModels.add(spec._modelAlias);
    }
    Set<String> candidates = new HashSet<>();
    for (String alias : _input.keySet()) {
      if (! subModels.contains(alias)) {
        candidates.add(alias);
      }
    }
    if (candidates.size() != 1) {
      throw new IllegalStateException("Main model cannot be identified, " +
              "main should be the only model that doesn't have output mappings. Candidates: " + candidates.toString());
    }
    return candidates.iterator().next();
  }

  private static void usage() {
    System.out.println("");
    System.out.println("Usage:  java [...java args...] hex.genmodel.tools.BuildPipeline ");
    System.out.println("             --mapping <inputMapping1> <inputMapping2> ... --output <outputFile> --input <inputFile1> <inputFile2> ...");
    System.out.println("");
    System.out.println("     --mapping Mapping of model predictions to main model inputs.");
    System.out.println("               Example: Specify 'CLUSTER=clustering:0' to use a model defined in a MOJO file 'clustering.zip'");
    System.out.println("                        and map the predicted cluster (output 0) to input column 'CLUSTER' of the main model.");
    System.out.println("     --input   List of input MOJO files representing both the main model and the prerequisite models.");
    System.out.println("     --output  Name of the generated MOJO pipeline file.");
    System.out.println("");
    System.out.println("     Input mappings are specified in format '<columnName>=<modelAlias>:<predictionIndex>'.");
    System.out.println("");
    System.out.println("     Model alias is based on the name of the MOJO file.");
    System.out.println("     For example, a MOJO stored in 'glm_model.zip' will have the alias 'glm_model'.");
    System.out.println("");
    System.out.println("Note: There is no need to specify which of the MOJO model represents the main model. The tool");
    System.out.println("automatically identifies the main model as the one that doesn't have any output mappings.");
    System.out.println("");
    System.exit(1);
  }

  private void parseArgs(String[] args) {
    try {
      for (int i = 0; i < args.length; i++) {
        String s = args[i];
        if (s.equals("--mapping")) {
          List<String> mappingSpec = readArgValues(args, i + 1);
          _mappings = new ArrayList<>(mappingSpec.size());
          for (String spec : mappingSpec) {
            try {
              _mappings.add(MojoPipelineBuilder.MappingSpec.parse(spec));
            } catch (Exception e) {
              throw new IllegalArgumentException("Invalid mapping specified ('" + spec + "'." +
                      " Please use format '<columnName>=<modelAlias>:<predictionIndex>'.");
            }
          }
          i += mappingSpec.size();
        } else if (s.equals("--output")) {
          List<String> outputFile = readArgValues(args, i + 1);
          if (outputFile.size() != 1) {
            throw new IllegalArgumentException("Invalid specification of the output file (" + outputFile.toString() + "). " +
                    "Please specify only a single output file.");
          }
          _output = new File(outputFile.get(0));
          i += 1;
        } else if (s.equals("--input")) {
          List<String> inputFiles = readArgValues(args, i + 1);
          if (inputFiles.size() < 2) {
            throw new IllegalArgumentException("Pipeline needs at least 2 input files, only " + inputFiles.size() + " specified.");
          }
          _input = makeAliases(inputFiles);
          i += inputFiles.size();
        } else {
          System.out.println("ERROR: Unknown command line argument: " + s);
          usage();
        }
      }
    } catch (Exception e) {
      System.err.println("ERROR: " + e.getMessage());
      e.printStackTrace();
      usage();
    }
    if (_input == null) {
      System.err.println("ERROR: Missing mandatory argument '--output'");
      usage();
    }
    if (_output == null) {
      System.err.println("ERROR: Missing mandatory argument '--input'");
      usage();
    }
    if (_mappings == null) {
      System.err.println("ERROR: Missing mandatory argument '--mapping'");
      usage();
    }
  }

  private Map<String, File> makeAliases(List<String> paths) {
    Map<String, File> aliases = new HashMap<>(paths.size());
    for (String path : paths) {
      File f = new File(path);
      String name = f.getName();
      int extIndex = name.lastIndexOf(".");
      String alias = extIndex >= 0 ? name.substring(0, extIndex) : name;
      aliases.put(alias, f);
    }
    return aliases;
  }

  private static List<String> readArgValues(String[] args, int startIdx) {
    List<String> params = new LinkedList<>();
    for (int i = startIdx; i < args.length; i++) {
      if (args[i].startsWith("--"))
        break;
      params.add(args[i]);
    }
    return params;
  }

}
