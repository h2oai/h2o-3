package water.api.schemas3;

import hex.AUUC;
import hex.ModelMetricsBinomialUplift;
import water.api.API;
import water.util.ArrayUtils;
import water.util.EnumUtils;
import water.util.TwoDimTable;
import hex.AUUC.AUUCType;

import java.util.Arrays;

public class ModelMetricsBinomialUpliftV3<I extends ModelMetricsBinomialUplift, S extends water.api.schemas3.ModelMetricsBinomialUpliftV3<I, S>>
            extends ModelMetricsBaseV3<I,S> {

    @API(help="The default AUUC for this scoring run.", direction=API.Direction.OUTPUT)
    public double AUUC;

    @API(help="The class labels of the response.", direction=API.Direction.OUTPUT)
    public String[] domain;

    @API(help = "The metrics for various thresholds.", direction = API.Direction.OUTPUT, level = API.Level.expert)
    public TwoDimTableV3 thresholds_and_metric_scores;

    @API(help = "Table of all types of AUUC.", direction = API.Direction.OUTPUT, level = API.Level.secondary)
    public TwoDimTableV3 auuc_table;

    @Override
    public S fillFromImpl(ModelMetricsBinomialUplift modelMetrics) {
        super.fillFromImpl(modelMetrics);

        AUUC auuc = modelMetrics._auuc;
        if (null != auuc) {
            AUUC  = auuc.auuc();

            // Fill TwoDimTable
            String[] thresholds = new String[auuc._nBins];
            AUUCType metrics[] = AUUCType.VALUES;
            metrics = ArrayUtils.remove(metrics, Arrays.asList(metrics).indexOf(AUUCType.AUTO));
            int metricsLength = metrics.length;
            long[] n = new long[auuc._nBins];
            double[][] uplift = new double[metricsLength][];
            for( int i = 0; i < auuc._nBins; i++ ) {
                thresholds[i] = Double.toString(auuc._ths[i]);
                n[i] = auuc._frequencyCumsum[i];
            }
            String[] colHeaders = new String[metricsLength + 3];
            String[] colHeadersMax = new String[metricsLength + 3];
            String[] types      = new String[metricsLength + 3];
            String[] formats    = new String[metricsLength + 3];
            colHeaders[0] = "thresholds";
            types[0] = "double";
            formats[0] = "%f";
            int i;
            for(i = 0; i < metricsLength; i++) {
                if (colHeadersMax.length > i) colHeadersMax[i] = "max " + metrics[i].toString();
                colHeaders[i+1] = metrics[i].toString();
                uplift[i] = auuc.upliftByType(metrics[i]);
                types     [i+1] = "double";
                formats   [i+1] = "%f";
            }
            colHeaders[i + 1]  = "n"; types[i+1] = "int"; formats[i+1] = "%d";
            colHeaders[i + 2]  = "idx"; types[i+2] = "int"; formats[i+2] = "%d";
            TwoDimTable thresholdsByMetrics = new TwoDimTable("Metrics for Thresholds", "Cumulative Uplift metrics for a given percentile", new String[auuc._nBins], colHeaders, types, formats, null );
            for(i = 0; i < auuc._nBins; i++) {
                int j = 0;
                thresholdsByMetrics.set(i, j, Double.valueOf(thresholds[i]));
                for (j = 0; j < metricsLength; j++) {
                    double d = uplift[j][i];
                    thresholdsByMetrics.set(i, 1 + j, d);
                }
                thresholdsByMetrics.set(i, 1 + j, n[i]);
                thresholdsByMetrics.set(i, 2 + j, i);
            }
            this.thresholds_and_metric_scores = new TwoDimTableV3().fillFromImpl(thresholdsByMetrics);
            
            // fill AUUC table
            String[] rowHeaders = new String[]{"AUUC value"};
            String[] metricNames = EnumUtils.getNames(AUUCType.class);
            colHeaders = ArrayUtils.remove(metricNames, Arrays.asList(metricNames).indexOf("AUTO"));
            types = new String[metricsLength];
            formats = new String[metricsLength];
            for(i = 0; i < metricsLength; i++){
                types[i] = "double";
                formats[i] = "%f";
            }
            TwoDimTable auucs = new TwoDimTable("AUUC table", "All types of AUUC", rowHeaders, colHeaders, types, formats, "AUUC type" );
            for(i = 0; i < metricsLength; i++) {
                auucs.set(0, i, auuc.auucByType(metrics[i]));
            }
            this.auuc_table = new TwoDimTableV3().fillFromImpl(auucs);
        }
        return (S) this; 
    }
}

