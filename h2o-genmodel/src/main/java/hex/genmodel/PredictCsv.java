package hex.genmodel;

import hex.ModelCategory;

import java.io.*;
import hex.genmodel.prediction.*;

/**
 * Simple driver program for reading a CSV file and making predictions.
 *
 * This driver program is used as a test harness by several tests in the testdir_javapredict directory.
 * <p></p>
 * See the top-of-tree master version of this file <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-genmodel/src/main/java/hex/genmodel/PredictCsv.java" target="_blank">here on github</a>.
 */
public class PredictCsv {
  private static String modelClassName;
  private static String inputCSVFileName;
  private static String outputCSVFileName;
  private static int haveHeaders = -1;

  private static void usage() {
    System.out.println("");
    System.out.println("usage:  java [...java args...] PredictCSV --header --model modelClassName --input inputCSVFileName --output outputCSVFileName");
    System.out.println("");
    System.out.println("        model class name is something like GBMModel_blahblahblahblah.");
    System.out.println("");
    System.out.println("        inputCSV is the test data set.");
    System.out.println("        Specifying --header is required for h2o-3.");
    System.out.println("");
    System.out.println("        outputCSV is the prediction data set (one row per test data set row).");
    System.out.println("");
    System.exit(1);
  }

  private static void parseArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String s = args[i];
      switch (s) {
        case "--model":
          i++;
          if (i >= args.length) usage();
          modelClassName = args[i];
          break;
        case "--input":
          i++;
          if (i >= args.length) usage();
          inputCSVFileName = args[i];
          break;
        case "--output":
          i++;
          if (i >= args.length) usage();
          outputCSVFileName = args[i];
          break;
        case "--header":
          haveHeaders = 1;
          break;
        default:
          System.out.println("ERROR: Bad parameter: " + s);
          usage();
      }
    }

    if (haveHeaders != 1) {
      System.out.println("ERROR: header not specified");
      usage();
    }

    if (modelClassName == null) {
      System.out.println("ERROR: model not specified");
      usage();
    }

    if (inputCSVFileName == null) {
      System.out.println("ERROR: input not specified");
      usage();
    }

    if (outputCSVFileName == null) {
      System.out.println("ERROR: output not specified");
      usage();
    }
  }

  /**
   * This CSV header row parser is as bare bones as it gets.
   * Doesn't handle funny quoting, spacing, or other issues.
   */
  private static String[] parseHeaderRow(String line) {
    return line.trim().split(",");
  }

  /**
   * This CSV parser is as bare bones as it gets.
   * Our test data doesn't have funny quoting, spacing, or other issues.
   * Can't handle cases where the number of data columns is less than the number of header columns.
   */
  private static RowData parseDataRow(String line, String[] inputColumnNames) {
    String[] inputData = line.trim().split(",");

    // Assemble the input values for the row.
    RowData row = new RowData();
    for (int i = 0; i < inputColumnNames.length; i++) {
      String columnName = inputColumnNames[i];
      String cellData = inputData[i];

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

  private static String myDoubleToString(double d) {
    if (Double.isNaN(d)) {
      return "NA";
    }

    return Double.toHexString(d);
  }

  /**
   * CSV reader and predictor test program.
   *
   * @param args Command-line args.
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    parseArgs(args);

    hex.genmodel.GenModel rawModel;
    rawModel = (hex.genmodel.GenModel) Class.forName(modelClassName).newInstance();
    EasyPredictModelWrapper model = new EasyPredictModelWrapper(rawModel);
    ModelCategory category = model.getModelCategory();

    BufferedReader input = new BufferedReader(new FileReader(inputCSVFileName));
    BufferedWriter output = new BufferedWriter(new FileWriter(outputCSVFileName));

    // Emit outputCSV column names.
    switch (category) {
      case AutoEncoder:
        output.write(model.getHeader());
        break;

      case Binomial:
      case Multinomial:
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
    int lineNum = 0;
    String line;
    String[] inputColumnNames = null;
    try {
      while ((line = input.readLine()) != null) {
        lineNum++;

        // Handle the header.
        if (lineNum == 1) {
          inputColumnNames = parseHeaderRow(line);
          continue;
        }

        // Parse the CSV line.  Don't handle quoted commas.  This isn't a parser test.
        RowData row = parseDataRow(line, inputColumnNames);

        // Do the prediction.
        // Emit the result to the output file.
        switch (category) {
          case AutoEncoder: {
            AutoEncoderModelPrediction p = model.predictAutoEncoder(row);
            throw new Exception("TODO");
            // break;
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
      }
    }
    catch (Exception e) {
      System.out.println("Caught exception on line " + lineNum);
      System.out.println("");
      e.printStackTrace();
      System.exit(1);
    }

    // Clean up.
    output.close();
    input.close();

    // Predictions were successfully generated.  Calling program can now compare them with something.
    System.exit(0);
  }
}
