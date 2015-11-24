package hex.genmodel.tools;

import hex.genmodel.GenMunger;
import hex.genmodel.easy.RowData;

import java.io.*;

/**
 * Simple driver program for reading a CSV file and munging it.
 *
 * This driver program is used as a test harness by several tests in the testdir_javamunge directory.
 * <p></p>
 * See the top-of-tree master version of this file <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-genmodel/src/main/java/hex/genmodel/tools/MungeCsv.java" target="_blank">here on github</a>.
 */
public class MungeCsv {
  private static String assemblyClassName;
  private static String inputCSVFileName;
  private static String outputCSVFileName;
  private static int haveHeaders = -1;

  private static void usage() {
    System.out.println("");
    System.out.println("usage:  java [...java args...] hex.genmodel.tools.MungeCsv --header --model modelClassName --input inputCSVFileName --output outputCSVFileName");
    System.out.println("");
    System.out.println("        assembly class name is something like AssemblyPojo_bleehbleehbleeh.");
    System.out.println("");
    System.out.println("        inputCSVFileName is the test data set.");
    System.out.println("        Specifying --header is required for h2o-3.");
    System.out.println("");
    System.out.println("        outputCSVFileName is the munged data set (one row per data set row).");
    System.out.println("");
    System.exit(1);
  }

  private static void parseArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String s = args[i];
      switch( s ) {
        case "--munger":
          i++;
          if (i >= args.length) usage();
          assemblyClassName = args[i];
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
          // skip
          System.out.println("bad param... skipping.");
      }
    }

    if (haveHeaders != 1) {
      System.out.println("ERROR: header not specified");
      usage();
    }

    if (assemblyClassName == null) {
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
   * This CSV parser is as bare bones as it gets.
   * Our test data doesn't have funny quoting, spacing, or other issues.
   * Can't handle cases where the number of data columns is less than the number of header columns.
   */
  private static RowData parseDataRow(String line, GenMunger munger) {
      if( line.isEmpty() || line.equals("") )
        return null;
      String[] inputData = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)|(,)", -1);
      for(int i=0;i<inputData.length;++i)
        inputData[i]=inputData[i]==null?"":inputData[i];
    if( inputData.length != munger.inNames().length )
      return null;
    return munger.fillDefault(inputData);
  }

  /**
   * CSV reader and predictor test program.
   *
   * @param args Command-line args.
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    parseArgs(args);

    GenMunger rawMunger;
    rawMunger = (hex.genmodel.GenMunger) Class.forName(assemblyClassName).newInstance();

    BufferedReader input = new BufferedReader(new FileReader(inputCSVFileName));
    BufferedWriter output = new BufferedWriter(new FileWriter(outputCSVFileName));

    // Emit outputCSV column names.
    String[] rawHeader = rawMunger.outNames();
    StringBuilder header = new StringBuilder();
    for(int i=0;i<rawHeader.length;++i) {
      header.append("\"").append(rawHeader[i]).append("\"");
      if( i < rawHeader.length - 1 ) header.append(",");
    }
    output.write(header.toString());
    output.write("\n");

    // Loop over inputCSV one row at a time.
    int lineNum = 0;
    String line;
    try {
      while ((line = input.readLine()) != null) {
        lineNum++;

        // skip the header.
        if (lineNum == 1)
          continue;

        // Parse the CSV line.  Somewhat handles quoted commas.  But this ain't no parser test!
        RowData row;
        try {
          row = parseDataRow(line, rawMunger);
        } catch( NumberFormatException nfe) {
          nfe.printStackTrace();
          System.out.println("Failed to parse row: " + lineNum );
          throw new RuntimeException();
        }
        RowData mungedRow = rawMunger.fit(row);

        for(int i=0; i<rawMunger.outNames().length;++i) {
          Object val = mungedRow==null?Double.NaN:mungedRow.get(rawMunger.outNames()[i]);
          if( val instanceof Double ) output.write(String.valueOf(val));
          else                        output.write("\"" + val + "\"");
          if( i < rawMunger.outNames().length - 1) output.write(",");
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
    finally {

      // Clean up.
      output.close();
      input.close();
    }

    // Predictions were successfully generated.  Calling program can now compare them with something.
    System.exit(0);
  }
}
