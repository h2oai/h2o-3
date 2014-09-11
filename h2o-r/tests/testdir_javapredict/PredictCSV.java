import java.io.*;
import java.util.HashMap;

class PredictCSV {
    private static String modelClassName;
    private static String inputCSVFileName;
    private static String outputCSVFileName;
    private static int skipFirstLine = -1;

    private static void usage() {
        System.out.println("");
        System.out.println("usage:  java [...java args...] PredictCSV (--header | --noheader) --model modelClassName --input inputCSVFileName --output outputCSVFileName");
        System.out.println("");
        System.out.println("        model class name is something like GBMModel_blahblahblahblah.");
        System.out.println("");
        System.out.println("        inputCSV is the test data set.");
        System.out.println("        Specify --header or --noheader as appropriate.");
        System.out.println("");
        System.out.println("        outputCSV is the prediction data set (one row per test data set).");
        System.out.println("");
        System.exit(1);
    }

    private static void usageHeader() {
        System.out.println("ERROR: One of --header or --noheader must be specified exactly once");
        usage();
    }

    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if (s.equals("--model")) {
                i++; if (i >= args.length) usage();
                modelClassName = args[i];
            }
            else if (s.equals("--input")) {
                i++; if (i >= args.length) usage();
                inputCSVFileName = args[i];
            }
            else if (s.equals("--output")) {
                i++; if (i >= args.length) usage();
                outputCSVFileName = args[i];
            }
            else if (s.equals("--header")) {
                if (skipFirstLine >= 0) usageHeader();
                skipFirstLine = 1;
            }
            else if (s.equals("--noheader")) {
                if (skipFirstLine >= 0) usageHeader();
                skipFirstLine = 0;
            }
            else {
                System.out.println("ERROR: Bad parameter: " + s);
                usage();
            }
        }

        if (skipFirstLine < 0) {
            usageHeader();
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

    public static void main(String[] args) throws Exception{
        parseArgs(args);

        water.genmodel.GeneratedModel model;
        model = (water.genmodel.GeneratedModel) Class.forName(modelClassName).newInstance();

        BufferedReader input = new BufferedReader(new FileReader(inputCSVFileName));
        BufferedWriter output = new BufferedWriter(new FileWriter(outputCSVFileName));

        System.out.println("COLS " + model.getNumCols());
        // Create map of input variable domain information.
        // This contains the categorical string to numeric mapping.
        HashMap<Integer,HashMap<String,Integer>> domainMap = new HashMap<Integer,HashMap<String,Integer>>();
        for (int i = 0; i < model.getNumCols(); i++) {
            String[] domainValues = model.getDomainValues(i);
            if (domainValues != null) {
                HashMap<String,Integer> m = new HashMap<String,Integer>();
                for (int j = 0; j < domainValues.length; j++) {
                    System.out.println("Putting ("+ i +","+ j +","+ domainValues[j] +")");
                    m.put(domainValues[j], new Integer(j));
                }

                domainMap.put(i, m);
            }
        }

        // Print outputCSV column names.
        output.write("predict");
        for (int i = 0; i < model.getNumResponseClasses(); i++) {
            output.write(",");
            output.write(model.getDomainValues(model.getResponseIdx())[i]);
        }
        output.write("\n");

        // Loop over inputCSV one row at a time.
        int lineno = 0;
        String line = null;
        // An array to store predicted values
        float[] preds = new float[model.getPredsSize()];
        while ((line = input.readLine()) != null) {
            lineno++;
            if (skipFirstLine > 0) {
                skipFirstLine = 0;
                String[] names = line.trim().split(",");
                String[] modelNames = model.getNames();
                for (int i=0; i < Math.min(names.length, modelNames.length); i++ )
                  if ( !names[i].equals(modelNames[i]) ) {
                    System.out.println("ERROR: Column names does not match: input column " + i + ". "+names[i]+" != model column "+modelNames[i] );
                    System.exit(1);
                  }
                // go to the next line
                continue;
            }

            // Parse the CSV line.  Don't handle quoted commas.  This isn't a parser test.
            String trimmedLine = line.trim();
            String[] inputColumnsArray = trimmedLine.split(",");
            int numInputColumns = model.getNames().length-1; // we do not need response !
            if (inputColumnsArray.length != numInputColumns) {
                System.out.println("WARNING: Line " + lineno + " has " + inputColumnsArray.length + " columns (expected " + numInputColumns + ")");
            }

            // Assemble the input values for the row.
            double[] row = new double[numInputColumns];
            int j = 0;
            for (j = 0; j < inputColumnsArray.length; j++) {
                String cellString = inputColumnsArray[j];

                // System.out.println("Line " + lineno +" column ("+ model.getNames()[i] + " == " + i + ") cellString("+cellString+")");

                String[] domainValues = model.getDomainValues(j);
                if (cellString.equals("") ||    // empty field is default NA
                    (domainValues == null) && ( // if the column is enum then NA is part of domain by default ! 
                      cellString.equals("NA") ||
                      cellString.equals("N/A") ||
                      cellString.equals("-") )
                    ) {
                    row[j] = Double.NaN;
                } else {
                    if (domainValues != null) {
                        HashMap m = (HashMap<String,Integer>) domainMap.get(j);
                        assert (m != null);
                        Integer cellOrdinalValue = (Integer) m.get(cellString);
                        if (cellOrdinalValue == null) {
                            System.out.println("WARNING: Line " + lineno + " column ("+ model.getNames()[j] + " == " + j +") has unknown categorical value (" + cellString + ")");
                            row[j] = Double.NaN;
                        }
                        else {
                            row[j] = (double) cellOrdinalValue.intValue();
                        }
                    } else {
                        try {
                          double value = Double.parseDouble(cellString);
                          row[j] = value;
                        } catch (NumberFormatException e) {
                          row[j] = Double.NaN;
                        }
                    }
                }
            }
            for (; j< numInputColumns; j++) row[j] = Double.NaN;

            // Do the prediction.
            //model.predict(row, preds);
            preds = model.predict(row, preds);

            // Emit the result to the output file.
            for (int i = 0; i < preds.length; i++) {
                if (i == 0 && model.isClassifier()) {
                    // See if there is a domain to map this output value to.
                    String[] domainValues = model.getDomainValues(model.getResponseIdx());
                    if (domainValues != null) {
                        // Classification.
                        double value = preds[i];
                        int valueAsInt = (int)value;
                        if (value != (int)valueAsInt) {
                            System.out.println("ERROR: Line " + lineno + " has non-integer output for classification (" + value + ")");
                            System.exit(1);
                        }

                        String predictedOutputClassLevel = domainValues[valueAsInt];
                        output.write(predictedOutputClassLevel);
                    }
                } else {
                    if (i > 0) output.write(",");
                    output.write(Double.toHexString(preds[i]));
                    if (!model.isClassifier()) break;
                }
            }
            output.write("\n");
        }

        // Clean up.
        output.close();
        input.close();

        // Predictions were successfully generated.  Calling program can now compare them with something.
        System.exit(0);
    }
}
