package hex.genmodel.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import hex.ModelCategory;
import hex.genmodel.GenModel;
import hex.genmodel.RawModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Simple driver program for reading a CSV file and making predictions.
 *
 * This driver program is used as a test harness by several tests in the testdir_javapredict directory.
 * <p></p>
 * See the top-of-tree master version of this file <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-genmodel/src/main/java/hex/genmodel/tools/PredictCsv.java" target="_blank">here on github</a>.
 */
public class PredictCsv {
  @Parameter(names = {"--model", "-m"}, required = true,
      description = "The model to train. This could be either the java class of the model POJO, or the name of the " +
          "zip file containing raw model's data, or the name of the folder with unzipped raw model's data.")
  private String modelName;

  @Parameter(names = {"--input", "-i"}, required = true,
      description = "The dataset that should be scored with the model.")
  private String inputCSVFileName;

  @Parameter(names = {"--output", "-o"}, required = true,
      description = "The name of the output file that will contain the predictions, one row per test dataset row.")
  private String outputCSVFileName;

  @Parameter(names={"--header"}, hidden = true)
  private boolean haveHeaders = false;

  // Model instance
  private EasyPredictModelWrapper model;

  public static void main(String[] args) {
    // Parse command line arguments
    PredictCsv main = new PredictCsv();
    JCommander jc = new JCommander(main);
    try {
      jc.parse(args);
    } catch (ParameterException e) {
      StringBuilder sb = new StringBuilder();
      jc.usage(sb);
      sb.insert(sb.indexOf("PredictCsv"), "java [... java args ...] hex.genmodel.tools.");
      System.out.print(sb);
      System.exit(1);
    }
    // Run the main program
    try {
      main.run();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(2);
    }
    // Predictions were successfully generated.  Calling program can now compare them with something.
    System.exit(0);
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

  static String myDoubleToString(double d) {
    if (Double.isNaN(d)) {
      return "NA";
    }

    return Double.toHexString(d);
  }

  private void run() throws Exception {
    loadModel();
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
    //
    // TODO: performance of scoring can be considerably improved if instead of scoring each row at a time we passed
    //       all the rows to the score function, in which case it can evaluate each tree for each row, avoiding
    //       multiple rounds of fetching each tree from the filesystem.
    //
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

  }

  private void loadModel() throws Exception {
    // This may throw either a `ReflectiveOperationException` or an `IOException`
    GenModel genModel = modelName.endsWith(".java")? (GenModel) Class.forName(modelName).newInstance()
                                                   : RawModel.load(modelName);
    model = new EasyPredictModelWrapper(genModel);
  }

}
