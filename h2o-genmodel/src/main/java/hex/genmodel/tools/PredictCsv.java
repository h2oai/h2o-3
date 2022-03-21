package hex.genmodel.tools;

import au.com.bytecode.opencsv.CSVReader;
import hex.ModelCategory;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.glrm.GlrmMojoModel;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.*;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Simple driver program for reading a CSV file and making predictions.  Added support for separators that are
 * not commas. User needs to add the --separator separator_string to the input call.  Do not escape
 * the special Java characters, I will do it for you.
 *
 * This driver program is used as a test harness by several tests in the testdir_javapredict directory.
 * <p></p>
 * See the top-of-tree master version of this file <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-genmodel/src/main/java/hex/genmodel/tools/PredictCsv.java" target="_blank">here on github</a>.
 */
public class PredictCsv {
  private final String inputCSVFileName;
  private final String outputCSVFileName;
  private final boolean useDecimalOutput;
  private final char separator;
  private final boolean setInvNumNA;
  private final boolean getTreePath;
  private final boolean predictContributions;
  private final boolean returnGLRMReconstruct;
  private final int glrmIterNumber;
  private final boolean outputHeader;

  // Model instance
  private EasyPredictModelWrapper modelWrapper;

  private PredictCsv(
          String inputCSVFileName, String outputCSVFileName, 
          boolean useDecimalOutput, char separator, boolean setInvNumNA, 
          boolean getTreePath, boolean predictContributions, boolean returnGLRMReconstruct, int glrmIterNumber,
          boolean outputHeader) {
    this.inputCSVFileName = inputCSVFileName;
    this.outputCSVFileName = outputCSVFileName;
    this.useDecimalOutput = useDecimalOutput;
    this.separator = separator;
    this.setInvNumNA = setInvNumNA;
    this.getTreePath = getTreePath;
    this.predictContributions = predictContributions;
    this.returnGLRMReconstruct = returnGLRMReconstruct;
    this.glrmIterNumber = glrmIterNumber;
    this.outputHeader = outputHeader;
  }

  public static void main(String[] args) {
    PredictCsvCollection predictors = buildPredictCsv(args);
    PredictCsv main = predictors.main;

    // Run the main program
    try {
      main.run();
    } catch (Exception e) {
      System.out.println("Predict error: " + e.getMessage());
      System.out.println();
      e.printStackTrace();
      System.exit(1);
    }

    if (predictors.concurrent.length > 0) {
      try {
        ExecutorService executor = Executors.newFixedThreadPool(predictors.concurrent.length);
        List<PredictCsvCallable> callables = new ArrayList<>(predictors.concurrent.length);
        for (int i = 0; i < predictors.concurrent.length; i++) {
          callables.add(new PredictCsvCallable(predictors.concurrent[i]));
        }
        int numExceptions = 0;
        for (Future<Exception> future : executor.invokeAll(callables)) {
          Exception e = future.get();
          if (e != null) {
            e.printStackTrace();
            numExceptions++;
          }
        }
        if (numExceptions > 0) {
          throw new Exception("Some predictors failed (#failed=" + numExceptions + ")");
        }
      } catch (Exception e) {
        System.out.println("Concurrent predict error: " + e.getMessage());
        System.out.println();
        e.printStackTrace();
        System.exit(1);
      }
    }

    // Predictions were successfully generated.
    System.exit(0);
  }

  // Only meant to be used in tests
  public static PredictCsv make(String[] args, GenModel model) {
    final PredictCsvCollection predictorCollection = buildPredictCsv(args);
    if (predictorCollection.concurrent.length != 0) {
      throw new UnsupportedOperationException("Predicting with concurrent predictors is not supported in programmatic mode.");
    }
    final PredictCsv predictor = predictorCollection.main;
    if (model != null) {
      try {
        predictor.setModelWrapper(model);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return predictor;
  }

  private static RowData formatDataRow(String[] splitLine, String[] inputColumnNames) {
    // Assemble the input values for the row.
    RowData row = new RowData();
    int maxI = Math.min(inputColumnNames.length, splitLine.length);
    for (int i = 0; i < maxI; i++) {
      String columnName = inputColumnNames[i];
      String cellData = splitLine[i];

      switch (cellData) {
        case "":
        case "NA":
        case "N/A":
        case "-":
          continue;
        default:
          row.put(columnName, cellData);
      }
    }

    return row;
  }

  private String myDoubleToString(double d) {
    if (Double.isNaN(d)) {
      return "NA";
    }
    return useDecimalOutput? Double.toString(d) : Double.toHexString(d);
  }

  private void writeTreePathNames(BufferedWriter output) throws Exception {
    String[] columnNames = ((SharedTreeMojoModel) modelWrapper.m).getDecisionPathNames();
    writeColumnNames(output, columnNames);
  }

  private void writeContributionNames(BufferedWriter output) throws Exception {
    writeColumnNames(output, modelWrapper.getContributionNames());
  }

  private void writeColumnNames(BufferedWriter output, String[] columnNames) throws Exception {
    int lastIndex = columnNames.length-1;
    for (int index = 0; index < lastIndex; index++)  {
      output.write(columnNames[index]);
      output.write(",");
    }
    output.write(columnNames[lastIndex]);
  }

  public void run() throws Exception {
    ModelCategory category = modelWrapper.getModelCategory();
    CSVReader reader = new CSVReader(new FileReader(inputCSVFileName), separator);
    BufferedWriter output = new BufferedWriter(new FileWriter(outputCSVFileName));

    // Emit outputCSV column names.
    if (outputHeader) {
      switch (category) {
        case Binomial:
        case Multinomial:
        case Regression:
          if (getTreePath) {
            writeTreePathNames(output);
          } else if (predictContributions) {
            writeContributionNames(output);
          } else
            writeHeader(modelWrapper.m.getOutputNames(), output);
          break;

        case DimReduction:  // will write factor or the predicted value depending on what the user wants
          if (returnGLRMReconstruct) {
            int datawidth;
            String[] colnames = this.modelWrapper.m.getNames();
            datawidth = ((GlrmMojoModel) modelWrapper.m)._permutation.length;
            int lastData = datawidth - 1;
            for (int index = 0; index < datawidth; index++) {  // add the numerical column names
              output.write("reconstr_" + colnames[index]);

              if (index < lastData)
                output.write(',');
            }
          } else
            writeHeader(modelWrapper.m.getOutputNames(), output);
          break;

        default:
          writeHeader(modelWrapper.m.getOutputNames(), output);
      }
      output.write("\n");
    }

    // Loop over inputCSV one row at a time.
    //
    int lineNum=1;    // count number of lines of input dataset file parsed
    try {
      String[] inputColumnNames;
      String[] splitLine;
      //Reader in the column names here.
      if ((splitLine = reader.readNext()) != null) {
        inputColumnNames = splitLine;
        checkMissingColumns(inputColumnNames);
      }
      else  // file empty, throw an error
        throw new Exception("Input dataset file is empty!");

      while ((splitLine = reader.readNext()) != null) {
        // Parse the CSV line.  Don't handle quoted commas.  This isn't a parser test.
        RowData row = formatDataRow(splitLine, inputColumnNames);
        // Do the prediction.
        // Emit the result to the output file.
        String offsetColumn = modelWrapper.m.getOffsetName();
        double offset = offsetColumn==null ? 0 : Double.parseDouble((String) row.get(offsetColumn));
        switch (category) {
          case AutoEncoder: { // write the expanded predictions out
            AutoEncoderModelPrediction p = modelWrapper.predictAutoEncoder(row);
            for (int i=0; i < p.reconstructed.length; i++) {
              output.write(myDoubleToString(p.reconstructed[i]));
              if (i < p.reconstructed.length)
                output.write(',');
            }
            break;
          }
          case Binomial: {
            BinomialModelPrediction p = modelWrapper.predictBinomial(row, offset);
            if (getTreePath) {
              writeTreePaths(p.leafNodeAssignments, output);
            } else if (predictContributions) {
              writeContributions(p.contributions, output);
            } else {
              output.write(p.label);
              output.write(",");
              for (int i = 0; i < p.classProbabilities.length; i++) {
                if (i > 0) {
                  output.write(",");
                }
                output.write(myDoubleToString(p.classProbabilities[i]));
              }
            }
            break;
          }
          case Multinomial: {
            MultinomialModelPrediction p = modelWrapper.predictMultinomial(row);
            if (getTreePath) {
              writeTreePaths(p.leafNodeAssignments, output);
            } else {
              output.write(p.label);
              output.write(",");
              for (int i = 0; i < p.classProbabilities.length; i++) {
                if (i > 0) {
                  output.write(",");
                }
                output.write(myDoubleToString(p.classProbabilities[i]));
              }
            }
            break;
          }
          case Ordinal: {
            OrdinalModelPrediction p = modelWrapper.predictOrdinal(row, offset);
            output.write(p.label);
            output.write(",");
            for (int i = 0; i < p.classProbabilities.length; i++) {
              if (i > 0) {
                output.write(",");
              }
              output.write(myDoubleToString(p.classProbabilities[i]));
            }
            break;
          }
          case Clustering: {
            ClusteringModelPrediction p = modelWrapper.predictClustering(row);
            output.write(myDoubleToString(p.cluster));
            break;
          }

          case Regression: {
              RegressionModelPrediction p = modelWrapper.predictRegression(row, offset);
              if (getTreePath) {
                writeTreePaths(p.leafNodeAssignments, output);
              } else if (predictContributions) {
                writeContributions(p.contributions, output);
              } else
               output.write(myDoubleToString(p.value));

            break;
          }
          
          case CoxPH: {
              CoxPHModelPrediction p = modelWrapper.predictCoxPH(row);
              output.write(myDoubleToString(p.value));

            break;
          }

          case DimReduction: {
            DimReductionModelPrediction p = modelWrapper.predictDimReduction(row);
            double[] out;

            if (returnGLRMReconstruct) {
              out = p.reconstructed;  // reconstructed A
            } else {
              out = p.dimensions; // x factors
            }

            int lastOne = out.length-1;
            for (int i=0; i < out.length; i++) {
              output.write(myDoubleToString(out[i]));

              if (i < lastOne)
                output.write(',');
            }
            break;
          }

          case AnomalyDetection: {
            AnomalyDetectionPrediction p = modelWrapper.predictAnomalyDetection(row);
            double[] rawPreds = p.toPreds();
            for (int i = 0; i < rawPreds.length - 1; i++) {
              output.write(myDoubleToString(rawPreds[i]));
              output.write(',');
            }
            output.write(myDoubleToString(rawPreds[rawPreds.length - 1]));
            break;
          }

          default:
            throw new Exception("Unknown model category " + category);
        }

        output.write("\n");
        lineNum++;
      }
    }
    catch (Exception e) {
      throw new Exception("Prediction failed on line " + lineNum, e);
    } finally {
      // Clean up.
      output.close();
      reader.close();
    }
  }

  private void writeHeader(String[] colNames, BufferedWriter output) throws Exception {
    output.write(colNames[0]);
    for (int i = 1; i < colNames.length; i++) {
      output.write(",");
      output.write(colNames[i]);
    }
  }

  private void writeTreePaths(String[] treePaths, BufferedWriter output) throws Exception {
    int len = treePaths.length-1;

    for (int index=0; index<len; index++) {
      output.write(treePaths[index]);
      output.write(",");
    }
    output.write(treePaths[len]);
  }

  private void writeContributions(float[] contributions, BufferedWriter output) throws Exception {
    for (int i = 0; i < contributions.length; i++) {
      if (i > 0) {
        output.write(",");
      }
      output.write(myDoubleToString(contributions[i]));
    }
  }

  private void setModelWrapper(GenModel genModel) throws IOException {
    EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
            .setModel(genModel)
            .setConvertUnknownCategoricalLevelsToNa(true)
            .setConvertInvalidNumbersToNa(setInvNumNA);

    if (getTreePath)
      config.setEnableLeafAssignment(true);

    if (predictContributions)
      config.setEnableContributions(true);

    if (returnGLRMReconstruct)
      config.setEnableGLRMReconstrut(true);

    if (glrmIterNumber > 0)   // set GLRM Mojo iteration number
      config.setGLRMIterNumber(glrmIterNumber);

    setModelWrapper(new EasyPredictModelWrapper(config));
  }

  private void setModelWrapper(EasyPredictModelWrapper modelWrapper) {
    this.modelWrapper = modelWrapper;
  } 
  
  private static void usage() {
    System.out.println();
    System.out.println("Usage:  java [...java args...] hex.genmodel.tools.PredictCsv --mojo mojoName");
    System.out.println("             --pojo pojoName --input inputFile --output outputFile --separator sepStr --decimal --setConvertInvalidNum");
    System.out.println();
    System.out.println("     --mojo    Name of the zip file containing model's MOJO.");
    System.out.println("     --pojo    Name of the java class containing the model's POJO. Either this ");
    System.out.println("               parameter or --model must be specified.");
    System.out.println("     --input   text file containing the test data set to score.");
    System.out.println("     --output  Name of the output CSV file with computed predictions.");
    System.out.println("     --separator Separator to be used in input file containing test data set.");
    System.out.println("     --decimal Use decimal numbers in the output (default is to use hexademical).");
    System.out.println("     --setConvertInvalidNum Will call .setConvertInvalidNumbersToNa(true) when loading models.");
    System.out.println("     --leafNodeAssignment will show the leaf node assignment for tree based models instead of" +
            " prediction results");
    System.out.println("     --predictContributions will output prediction contributions (Shapley values) for tree based" +
            " models instead of regular model predictions");
    System.out.println("     --glrmReconstruct will return the reconstructed dataset for GLRM mojo instead of X factor derived from the dataset.");
    System.out.println("     --glrmIterNumber integer indicating number of iterations to go through when constructing X factor derived from the dataset.");
    System.out.println("     --testConcurrent integer (for testing) number of concurrent threads that will be making predictions.");
    System.out.println();
    System.exit(1);
  }

  private void checkMissingColumns(final String[] parsedColumnNamesArr) {
    final String[] modelColumnNames = modelWrapper.m._names;
    final Set<String> parsedColumnNames = new HashSet<>(parsedColumnNamesArr.length);
    Collections.addAll(parsedColumnNames, parsedColumnNamesArr);

    List<String> missingColumns = new ArrayList<>();
    for (String columnName : modelColumnNames) {

      if (!parsedColumnNames.contains(columnName) && !columnName.equals(modelWrapper.m._responseColumn)) {
        missingColumns.add(columnName);
      } else {
        parsedColumnNames.remove(columnName);
      }
    }
    
    if(missingColumns.size() > 0){
      final StringBuilder stringBuilder = new StringBuilder("There were ");
      stringBuilder.append(missingColumns.size());
      stringBuilder.append(" missing columns found in the input data set: {");

      for (int i = 0; i < missingColumns.size(); i++) {
        stringBuilder.append(missingColumns.get(i));
        if(i != missingColumns.size() - 1) stringBuilder.append(",");
      }
      stringBuilder.append('}');
      System.out.println(stringBuilder);
    }
    
    if(parsedColumnNames.size() > 0){
      final StringBuilder stringBuilder = new StringBuilder("Detected ");
      stringBuilder.append(parsedColumnNames.size());
      stringBuilder.append(" unused columns in the input data set: {");

      final Iterator<String> iterator = parsedColumnNames.iterator();
      while (iterator.hasNext()){
        stringBuilder.append(iterator.next());
        if(iterator.hasNext()) stringBuilder.append(",");
      }
      stringBuilder.append('}');
      System.out.println(stringBuilder);
    }
  }

  private static class PredictCsvCollection {
    private final PredictCsv main;
    private final PredictCsv[] concurrent;
    private PredictCsvCollection(PredictCsv main, PredictCsv[] concurrent) {
      this.main = main;
      this.concurrent = concurrent;
    }
  }
  
  private static PredictCsvCollection buildPredictCsv(String[] args) {
    try {
      PredictCsvBuilder builder = new PredictCsvBuilder();
      builder.parseArgs(args);
      final GenModel genModel;
      switch (builder.loadType) {
        case -1:
          genModel = null;
          break;
        case 0:
          genModel = loadPojo(builder.pojoMojoModelNames);
          break;
        case 1:
          genModel = loadMojo(builder.pojoMojoModelNames);
          break;
        case 2:
          genModel = loadModel(builder.pojoMojoModelNames);
          break;
        default:
          throw new IllegalStateException("Unexpected value of loadType = " + builder.loadType);
      }
      PredictCsv mainPredictCsv = builder.newPredictCsv();
      if (genModel != null) {
        mainPredictCsv.setModelWrapper(genModel);
      }
      PredictCsv[] concurrentPredictCsvs = new PredictCsv[builder.testConcurrent];
      for (int id = 0; id < concurrentPredictCsvs.length; id++) {
        PredictCsv concurrentPredictCsv = builder.newConcurrentPredictCsv(id);
        concurrentPredictCsv.setModelWrapper(mainPredictCsv.modelWrapper); // re-use both the wrapper and the MOJO
        concurrentPredictCsvs[id] = concurrentPredictCsv;
      }
      return new PredictCsvCollection(mainPredictCsv, concurrentPredictCsvs);
    } catch (Exception e) {
      e.printStackTrace();
      usage();
      throw new IllegalStateException("Should not be reachable");
    }
  }

  private static GenModel loadPojo(String className) throws Exception {
    return (GenModel) Class.forName(className).newInstance();
  }

  private static GenModel loadMojo(String modelName) throws IOException {
    return MojoModel.load(modelName);
  }

  private static GenModel loadModel(String modelName) throws Exception {
    try {
      return loadMojo(modelName);
    } catch (IOException e) {
      return loadPojo(modelName);  // may throw an exception too
    }
  }

  private static class PredictCsvBuilder {
    // For PredictCsv
    private String inputCSVFileName;
    private String outputCSVFileName;
    private boolean useDecimalOutput;
    private char separator = ',';           // separator used to delimite input datasets
    private boolean setInvNumNA;            // enable .setConvertInvalidNumbersToNa(true)
    private boolean getTreePath;            // enable tree models to obtain the leaf-assignment information
    private boolean predictContributions;   // enable tree models to predict contributions instead of regular predictions
    private boolean returnGLRMReconstruct;  // for GLRM, return x factor by default unless set this to true
    private int glrmIterNumber = -1;        // for GLRM, default to 100.
    private boolean outputHeader = true;    // should we write-out header to output files?

    // For Model Loading
    private int loadType = 0; // 0: load pojo, 1: load mojo, 2: load model, -1: special value when PredictCsv is used embedded and instance of Model is passed directly
    private String pojoMojoModelNames = ""; // store Pojo/Mojo/Model names

    private int testConcurrent = 0;

    private PredictCsv newPredictCsv() {
      return new PredictCsv(inputCSVFileName, outputCSVFileName, useDecimalOutput, separator, setInvNumNA,
              getTreePath, predictContributions, returnGLRMReconstruct, glrmIterNumber, outputHeader);
    }

    private PredictCsv newConcurrentPredictCsv(int id) {
      return new PredictCsv(inputCSVFileName, outputCSVFileName + "." + id, useDecimalOutput, separator, setInvNumNA,
              getTreePath, predictContributions, returnGLRMReconstruct, glrmIterNumber, outputHeader);
    }

    private void parseArgs(String[] args) {
      for (int i = 0; i < args.length; i++) {
        String s = args[i];
        if (s.equals("--header")) 
          continue;
        if (s.equals("--decimal"))
          useDecimalOutput = true;
        else if (s.equals("--glrmReconstruct"))
          returnGLRMReconstruct = true;
        else if (s.equals("--setConvertInvalidNum"))
          setInvNumNA = true;
        else if (s.equals("--leafNodeAssignment"))
          getTreePath = true;
        else if (s.equals("--predictContributions")) {
          predictContributions = true;
        } else if (s.equals("--embedded")) {
          loadType = -1;
        } else {
          i++;
          if (i >= args.length) usage();
          String sarg = args[i];
          switch (s) {
            case "--model":
              pojoMojoModelNames = sarg;
              loadType = 2;
              break;
            case "--mojo":
              pojoMojoModelNames = sarg;
              loadType = 1;
              break;
            case "--pojo":
              pojoMojoModelNames = sarg;
              loadType = 0;
              break;
            case "--input":
              inputCSVFileName = sarg;
              break;
            case "--output":
              outputCSVFileName = sarg;
              break;
            case "--separator":
              separator = sarg.charAt(sarg.length() - 1);
              break;
            case "--glrmIterNumber":
              glrmIterNumber = Integer.parseInt(sarg);
              break;
            case "--testConcurrent":
              testConcurrent = Integer.parseInt(sarg);
              break;
            case "--outputHeader":
              outputHeader = Boolean.parseBoolean(sarg);
              break;
            default:
              System.out.println("ERROR: Unknown command line argument: " + s);
              usage();
          }
        }
      }
    }
  }
  
  private static class PredictCsvCallable implements Callable<Exception> {
    private final PredictCsv predictCsv;

    private PredictCsvCallable(PredictCsv predictCsv) {
      this.predictCsv = predictCsv;
    }

    @Override
    public Exception call() throws Exception {
      try {
        predictCsv.run();
      } catch (Exception e) {
        return e;
      }
      return null;
    }
  } 
  
}
