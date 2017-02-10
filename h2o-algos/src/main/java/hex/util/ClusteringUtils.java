package hex.util;

import hex.ClusteringModel;
import water.util.TwoDimTable;

public class ClusteringUtils {

    static public TwoDimTable createCenterTable(ClusteringModel.ClusteringOutput output, boolean standardized) {
        String name = standardized ? "Standardized Cluster Means" : "Cluster Means";
        if(output._size == null || output._names == null || output._domains == null || output._centers_raw == null ||
                (standardized && output._centers_std_raw == null)) {
            TwoDimTable table = new TwoDimTable(name, null, new String[] {"1"}, new String[]{"C1"}, new String[]{"double"},
                    new String[]{"%f"}, "Centroid");
            table.set(0,0,Double.NaN);
            return table;
        }

        String[] rowHeaders = new String[output._size.length];
        for(int i = 0; i < rowHeaders.length; i++)
            rowHeaders[i] = String.valueOf(i+1);
        String[] colTypes = new String[output._names.length];
        String[] colFormats = new String[output._names.length];
        for (int i = 0; i < output._domains.length; ++i) {
            colTypes[i] = output._domains[i] == null ? "double" : "String";
            colFormats[i] = output._domains[i] == null ? "%f" : "%s";
        }
        TwoDimTable table = new TwoDimTable(name, null, rowHeaders, output._names, colTypes, colFormats, "Centroid");

        // Internal weights/folds column is included in domain length
        int domain_length = output.hasWeights()? output._domains.length - 1 : output._domains.length;
        for (int j=0; j < domain_length; ++j) {
            boolean string = output._domains[j] != null;
            if (string) {
                for (int i=0; i<output._centers_raw.length; ++i) {
                    table.set(i, j, output._domains[j][(int)output._centers_raw[i][j]]);
                }
            } else {
                for (int i=0; i<output._centers_raw.length; ++i) {
                    table.set(i, j, standardized ? output._centers_std_raw[i][j] : output._centers_raw[i][j]);
                }
            }
        }
        return table;
    }

}
