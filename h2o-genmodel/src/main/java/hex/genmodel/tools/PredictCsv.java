package hex.genmodel.tools;

import au.com.bytecode.opencsv.CSVReader;
import hex.ModelCategory;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.*;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

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
  private String inputCSVFileName;
  private String outputCSVFileName;
  private boolean useDecimalOutput = false;
  public char separator = ',';   // separator used to delimite input datasets
  public boolean setInvNumNA = false;    // enable .setConvertInvalidNumbersToNa(true)
  // Model instance
  private EasyPredictModelWrapper model;

  public static void main(String[] args) {
    // Parse command line arguments
    PredictCsv main = new PredictCsv();
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

  private void run() throws Exception {
    ModelCategory category = model.getModelCategory();
    CSVReader reader = new CSVReader(new FileReader(inputCSVFileName), separator);
    BufferedWriter output = new BufferedWriter(new FileWriter(outputCSVFileName));
    int lastCommaAutoEn = -1; // for deeplearning model in autoencoder mode

    // Emit outputCSV column names.
    switch (category) {
      case AutoEncoder:
        String[] cnames =  this.model.m.getNames();
        int numCats = this.model.domainMap.size();
        int numNums = this.model.m.nfeatures()-numCats;
        String[][] domainValues = this.model.m.getDomainValues();
        int lastCatIdx = numCats-1;
        //int additionalCatCols = this.model.getUnknownCategoricalLevelsSeenPerColumn().size();
       // ConcurrentHashMap<String, AtomicLong> temp = this.model.getUnknownCategoricalLevelsSeenPerColumn();

        for (int index = 0; index <= lastCatIdx  ; index++) { // add names for categorical columns
          String[] tdomains = domainValues[index]; //this.model.m.getDomainValues(index)
          int tdomainLen = tdomains.length-1;
          for (int index2 = 0; index2 <= tdomainLen; index2++ ) {
            lastCommaAutoEn++;
            String temp = "reconstr_"+tdomains[index2];
            output.write(temp);
            output.write(',');
          }

          lastCommaAutoEn++;
          String temp = "reconstr_" + cnames[index] + ".missing(NA)"; // add missing(NA) column as last column name
          output.write(temp);
          if (numNums > 0 || index < lastCatIdx)
            output.write(',');
        }

        int lastComma = cnames.length-1;
        for (int index = numCats; index < cnames.length; index++) {  // add the numerical column names
          lastCommaAutoEn++;
          String temp = "reconstr_"+cnames[index];
          output.write(temp);

          if (index < lastComma )
            output.write(',');
        }
        break;
      case Binomial:
      case Multinomial:
      case Ordinal:
        output.write("predict");
        String[] responseDomainValues = model.getResponseDomainValues();
        for (String s : responseDomainValues) {
          output.write(",");
          output.write(s);
        }
        break;

      case Clustering:
        output.write("cluster");
        break;

      case Regression:
        output.write("predict");
        break;

      default:
        throw new Exception("Unknown model category " + category);
    }
    output.write("\n");

    // Loop over inputCSV one row at a time.
    //
    // TODO: performance of scoring can be considerably improved if instead of scoring each row at a time we passed
    //       all the rows to the score function, in which case it can evaluate each tree for each row, avoiding
    //       multiple rounds of fetching each tree from the filesystem.
    //
    int lineNum=1;    // count number of lines of input dataset file parsed
    try {
      String[] inputColumnNames = null;
      String[] splitLine;
      //Reader in the column names here.
      if ((splitLine=reader.readNext()) != null)
        inputColumnNames=splitLine;
      else  // file empty, throw an error
        throw new Exception("Input dataset file is empty!");

      while ((splitLine = reader.readNext()) != null) {
        // Parse the CSV line.  Don't handle quoted commas.  This isn't a parser test.
        RowData row = formatDataRow(splitLine, inputColumnNames);
        // Do the prediction.
        // Emit the result to the output file.
        switch (category) {
          case AutoEncoder: { // write the expanded predictions out
            AutoEncoderModelPrediction p = model.predictAutoEncoder(row);
            for (int i=0; i < p.reconstructed.length; i++) {
              output.write(myDoubleToString(p.reconstructed[i]));

              if (i < lastCommaAutoEn)
                output.write(',');
            }
            break;
          }
          case Binomial: {
            BinomialModelPrediction p = model.predictBinomial(row);
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

          case Multinomial: {
            MultinomialModelPrediction p = model.predictMultinomial(row);
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
          case Ordinal: {
            OrdinalModelPrediction p = model.predictOrdinal(row);
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
            ClusteringModelPrediction p = model.predictClustering(row);
            output.write(myDoubleToString(p.cluster));
            break;
          }

          case Regression: {
            RegressionModelPrediction p = model.predictRegression(row);
            output.write(myDoubleToString(p.value));
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
      System.out.println("Caught exception on line " + lineNum);
      System.out.println("");
      e.printStackTrace();
      System.exit(1);
    } finally {
      // Clean up.
      output.close();
      reader.close();
    }
  }


  private void loadModel(String modelName) throws Exception {
    try {
      loadMojo(modelName);
    } catch (IOException e) {
      loadPojo(modelName);  // may throw an exception too
    }
  }

  private void loadPojo(String className) throws Exception {
    GenModel genModel = (GenModel) Class.forName(className).newInstance();
    model = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config().setModel(genModel).setConvertUnknownCategoricalLevelsToNa(true).setConvertInvalidNumbersToNa(setInvNumNA));
  }

  private void loadMojo(String modelName) throws IOException {
    GenModel genModel = MojoModel.load(modelName);
    model = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config().setModel(genModel).setConvertUnknownCategoricalLevelsToNa(true).setConvertInvalidNumbersToNa(setInvNumNA));
  }

  private static void usage() {
    System.out.println("");
    System.out.println("Usage:  java [...java args...] hex.genmodel.tools.PredictCsv --mojo mojoName");
    System.out.println("             --pojo pojoName --input inputFile --output outputFile --separator sepStr --decimal --setConvertInvalidNum");
    System.out.println("");
    System.out.println("     --mojo    Name of the zip file containing model's MOJO.");
    System.out.println("     --pojo    Name of the java class containing the model's POJO. Either this ");
    System.out.println("               parameter or --model must be specified.");
    System.out.println("     --input   text file containing the test data set to score.");
    System.out.println("     --output  Name of the output CSV file with computed predictions.");
    System.out.println("     --separator Separator to be used in input file containing test data set.");
    System.out.println("     --decimal Use decimal numbers in the output (default is to use hexademical).");
    System.out.println("     --setConvertInvalidNum Will call .setConvertInvalidNumbersToNa(true) when loading models.");
    System.out.println("");
    System.exit(1);
  }

  private void parseArgs(String[] args) {
    try {
      String pojoMojoModelNames = ""; // store Pojo/Mojo/Model names
      int loadType = 0; // 0: load pojo, 1: load mojo, 2: load model
      for (int i = 0; i < args.length; i++) {
        String s = args[i];
        if (s.equals("--header")) continue;
        if (s.equals("--decimal"))
          useDecimalOutput = true;
        else if (s.equals("--setConvertInvalidNum"))
          setInvNumNA=true;
        else {
          i++;
          if (i >= args.length) usage();
          String sarg = args[i];
          switch (s) {
            case "--model":  pojoMojoModelNames=sarg; loadType=2; break;//loadModel(sarg); break;
            case "--mojo":   pojoMojoModelNames=sarg; loadType=1; break;//loadMojo(sarg); break;
            case "--pojo":   pojoMojoModelNames=sarg; loadType=0; break;//loadPojo(sarg); break;
            case "--input":  inputCSVFileName = sarg; break;
            case "--output": outputCSVFileName = sarg; break;
            case "--separator": separator=sarg.charAt(sarg.length()-1);; break;
            default:
              System.out.println("ERROR: Unknown command line argument: " + s);
              usage();
          }
        }
      }
      switch(loadType) {
        case 0: loadPojo(pojoMojoModelNames); break;
        case 1: loadMojo(pojoMojoModelNames); break;
        case 2: loadModel(pojoMojoModelNames); break;
      }
    } catch (Exception e) {
      e.printStackTrace();
      usage();
    }
  }
}
