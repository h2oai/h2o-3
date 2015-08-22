package hex.genmodel;

import hex.ModelCategory;

import java.io.*;
import hex.genmodel.prediction.*;

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

  private static String[] parseHeaderRow(String line) {
    return line.trim().split(",");
  }

  private static RowData parseDataRow(String line, String[] inputColumnNames) {
    String trimmedLine = line.trim();
    String[] inputData = trimmedLine.split(",");

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

  private static String h2oDoubleToString(double d) {
    if (Double.isNaN(d)) {
      return "NA";
    }

    return Double.toHexString(d);
  }

  public static void main(String[] args) throws Exception {
    parseArgs(args);

    hex.genmodel.GenModel model;
    model = (hex.genmodel.GenModel) Class.forName(modelClassName).newInstance();

    BufferedReader input = new BufferedReader(new FileReader(inputCSVFileName));
    BufferedWriter output = new BufferedWriter(new FileWriter(outputCSVFileName));

    // Print outputCSV column names.
    if (model.isAutoEncoder()) {
      output.write(model.getHeader());
    }
    else {
      output.write("predict");
      for (int i = 0; model.isClassifier() && i < model.getNumResponseClasses(); i++) {
        output.write(",");
        output.write(model.getDomainValues(model.getResponseIdx())[i]);
      }
    }
    output.write("\n");

    // Loop over inputCSV one row at a time.
    int lineNum = 0;
    String line;

    String[] inputColumnNames = new String[0];

    // An array to store predicted values
    try {
      while ((line = input.readLine()) != null) {
        lineNum++;
        if (lineNum == 1) {
          inputColumnNames = parseHeaderRow(line);
          continue;
        }

        // Parse the CSV line.  Don't handle quoted commas.  This isn't a parser test.
        RowData row = parseDataRow(line, inputColumnNames);

        // Do the prediction.
        // Emit the result to the output file.
        ModelCategory category = model.getModelCategory();
        if (category == ModelCategory.Binomial) {
          BinomialModelPrediction p = model.predictBinomial(row);

          output.write(p.label);
          output.write(",");
          for (int i = 0; i < p.classProbabilities.length; i++) {
            if (i > 0) {
              output.write(",");
            }
            output.write(h2oDoubleToString(p.classProbabilities[i]));
          }
        } else if (category == ModelCategory.Multinomial) {
          MultinomialModelPrediction p = model.predictMultinomial(row);

          output.write(p.label);
          output.write(",");
          for (int i = 0; i < p.classProbabilities.length; i++) {
            if (i > 0) {
              output.write(",");
            }
            output.write(h2oDoubleToString(p.classProbabilities[i]));
          }
        } else if (category == ModelCategory.Regression) {
          RegressionModelPrediction p = model.predictRegression(row);

          output.write(h2oDoubleToString(p.value));
        } else if (category == ModelCategory.Clustering) {
          ClusteringModelPrediction p = model.predictClustering(row);

          output.write(h2oDoubleToString(p.cluster));
        } else {
          System.out.println("Unknown model category: " + category.toString());
          System.exit(1);
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
