package hex.util;

import hex.DataInfo;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static java.lang.Math.sqrt;
import static water.util.ArrayUtils.*;


/**
 * Created by wendycwong on 2/9/17.
 */
public class DimensionReductionUtils {
    /**
     * This method will calculate the importance of principal components for PCA/GLRM methods.
     *
     * @param std_deviation: array of singular values
     * @param totVar: sum of squared singular values
     * @param vars: array of singular values squared
     * @param prop_var: var[i]/totVar for each i
     * @param cum_var: cumulative sum of var[i]/totVar from index 0 to index i.
     */
    public static void generateIPC(double[] std_deviation, double totVar, double[] vars, double[] prop_var,
                                   double[] cum_var) {
        int arrayLen = std_deviation.length;

        if (totVar > 0) {
            for (int i = 0; i < arrayLen; i++) {
                vars[i] = std_deviation[i] * std_deviation[i];
                prop_var[i] = vars[i] / totVar;
                cum_var[i] = i == 0 ? prop_var[0] : cum_var[i-1] + prop_var[i];
            }
        }
        double lastCum = cum_var[arrayLen-1];
        if (lastCum > 1) {  // GLRM sometimes screw up the matrix estimation pretty bad
            double multF = 1/lastCum;
            ArrayUtils.mult(prop_var, multF);
            ArrayUtils.mult(cum_var, multF);
            ArrayUtils.mult(vars, multF);
            ArrayUtils.mult(std_deviation, sqrt(multF));
        }
    }

    /**
     * Create the scoring history for dimension reduction algorithms like PCA/SVD.  We do make the following assumptions
     * about your scoring_history.  First we assume that you will always have the following field:
     * 1. Timestamp: long denoting the time in ms;
     * 2. All other fields are double.
     *
     * The following field will be generated for you automatically: Duration and Iteration.
     *
     * @param scoreTable: HashMap containing column headers and arraylist containing the history of values collected.
     * @param tableName: title/name of your scoring table
     * @param startTime:  time your model building job was first started.
     * @return: TwoDimTable containing the scoring history.
     */
    public static TwoDimTable createScoringHistoryTableDR(LinkedHashMap<String, ArrayList> scoreTable, String tableName,
                                                          long startTime) {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();
        ArrayList<String> otherTableEntries = new ArrayList<String>();

        for (String fieldName:scoreTable.keySet()) {
            if (fieldName.equals("Timestamp")) {
                colHeaders.add("Timestamp"); colTypes.add("string"); colFormat.add("%s");
                colHeaders.add("Duration"); colTypes.add("string"); colFormat.add("%s");
                colHeaders.add("Iterations"); colTypes.add("long"); colFormat.add("%d");
            } else {
                otherTableEntries.add(fieldName); colHeaders.add(fieldName); colTypes.add("double"); colFormat.add("%.5f");
            }
        }

        int rows = scoreTable.get("Timestamp").size(); // number of entries of training history

        TwoDimTable table = new TwoDimTable(
                tableName, null,
                new String[rows],
                colHeaders.toArray(new String[0]),
                colTypes.toArray(new String[0]),
                colFormat.toArray(new String[0]),
                "");

        assert (rows <= table.getRowDim());

        for (int row = 0; row < rows; row++) {
            int col = 0;
            // take care of Timestamp, Duration, Iteration.
            DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
            table.set(row, col++, fmt.print((long) scoreTable.get("Timestamp").get(row)));
            table.set(row, col++, PrettyPrint.msecs((long) scoreTable.get("Timestamp").get(row) - startTime, true));
            table.set(row, col++, row);
            // take care of the extra field
            for (int remaining_cols = 0; remaining_cols < otherTableEntries.size(); remaining_cols++) {
                table.set(row, col++, (double) scoreTable.get(otherTableEntries.get(remaining_cols)).get(row));
            }
        }
        return table;
    }

    /**
     * This function will tranform the eigenvectors calculated for a matrix T(A) to the ones calculated for
     * matrix A.
     * 
     * @param dinfo
     * @param vEigenIn
     * @return transformed eigenvectors
     */
    public static double[][] getTransformedEigenvectors(DataInfo dinfo, double[][] vEigenIn) {
        Frame tempFrame = new Frame(dinfo._adaptedFrame);
        Frame eigFrame = new water.util.ArrayUtils().frame(vEigenIn);
        tempFrame.add(eigFrame);

        LinearAlgebraUtils.SMulTask stsk = new LinearAlgebraUtils.SMulTask(dinfo, eigFrame.numCols(),
                dinfo._numOffsets[dinfo._numOffsets.length - 1]);   // will allocate new memory for _atq
        double[][] eigenVecs = stsk.doAll(tempFrame)._atq;

        if (eigFrame != null) { // delete frame to prevent leak keys.
            eigFrame.delete();
        }

        // need to normalize eigenvectors after multiplication by transpose(A) so that they have unit norm
        double[][] eigenVecsTranspose = transpose(eigenVecs);   // transpose will allocate memory
        double[] eigenNormsI = new double[eigenVecsTranspose.length];
        for (int vecIndex = 0; vecIndex < eigenVecsTranspose.length; vecIndex++) {
            eigenNormsI[vecIndex] = 1.0 / l2norm(eigenVecsTranspose[vecIndex]);
        }

        return transpose(mult(eigenVecsTranspose, eigenNormsI));
    }
}
