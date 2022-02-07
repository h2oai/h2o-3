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

    @API(help="The Qini value for this scoring run.", direction=API.Direction.OUTPUT)
    public double qini;

    @API(help="The class labels of the response.", direction=API.Direction.OUTPUT)
    public String[] domain;

    @API(help = "The metrics for various thresholds.", direction = API.Direction.OUTPUT, level = API.Level.expert)
    public TwoDimTableV3 thresholds_and_metric_scores;

    @API(help = "Table of all types of AUUC.", direction = API.Direction.OUTPUT, level = API.Level.secondary)
    public TwoDimTableV3 auuc_table;

    @API(help = "Table of all types of AECU values.", direction = API.Direction.OUTPUT, level = API.Level.secondary)
    public TwoDimTableV3 aecu_table;

    @Override
    public S fillFromImpl(ModelMetricsBinomialUplift modelMetrics) {
        super.fillFromImpl(modelMetrics);

        AUUC auuc = modelMetrics._auuc;
        if (null != auuc) {
            AUUC  = auuc.auuc();
            qini = auuc.qini();
            // Fill TwoDimTable
            String[] thresholds = new String[auuc._nBins];
            AUUCType metrics[] = AUUCType.VALUES;
            metrics = ArrayUtils.remove(metrics, Arrays.asList(metrics).indexOf(AUUCType.AUTO));
            int metricsLength = metrics.length;
            long[] n = new long[auuc._nBins];
            double[][] uplift = new double[metricsLength][];
            double[][] upliftRandom = new double[metricsLength][];
            for( int i = 0; i < auuc._nBins; i++ ) {
                thresholds[i] = Double.toString(auuc._ths[i]);
                n[i] = auuc._frequencyCumsum[i];
            }
            String[] colHeaders = new String[2 * metricsLength + 3];
            String[] types      = new String[2 * metricsLength + 3];
            String[] formats    = new String[2 * metricsLength + 3];
            colHeaders[0] = "thresholds";
            types[0] = "double";
            formats[0] = "%f";
            int i;
            for(i = 0; i < metricsLength; i++) {
                colHeaders[i + 1] = metrics[i].toString();
                colHeaders[(i + 1 + metricsLength)] = metrics[i].toString()+"_random";
                uplift[i] = auuc.upliftByType(metrics[i]);
                upliftRandom[i] = auuc.upliftRandomByType(metrics[i]);
                types     [i + 1] = "double";
                formats   [i + 1] = "%f";
                types     [i + 1 + metricsLength] = "double";
                formats   [i + 1 + metricsLength] = "%f";
            }
            colHeaders[i + 1 + metricsLength]  = "n"; types[i + 1 + metricsLength] = "int"; formats[i + 1 + metricsLength] = "%d";
            colHeaders[i + 2 + metricsLength]  = "idx"; types[i + 2 + metricsLength] = "int"; formats[i + 2 + metricsLength] = "%d";
            TwoDimTable thresholdsByMetrics = new TwoDimTable("Metrics for Thresholds", "Cumulative Uplift metrics for a given percentile", new String[auuc._nBins], colHeaders, types, formats, null );
            for(i = 0; i < auuc._nBins; i++) {
                int j = 0;
                thresholdsByMetrics.set(i, j, Double.valueOf(thresholds[i]));
                for (j = 0; j < metricsLength; j++) {
                    thresholdsByMetrics.set(i, 1 + j, uplift[j][i]);
                    thresholdsByMetrics.set(i, 1 + j + metricsLength, upliftRandom[j][i]);
                }
                thresholdsByMetrics.set(i, 1 + j + metricsLength, n[i]);
                thresholdsByMetrics.set(i, 2 + j + metricsLength, i);
            }
            this.thresholds_and_metric_scores = new TwoDimTableV3().fillFromImpl(thresholdsByMetrics);
            
            // fill AUUC table
            String[] rowHeaders = new String[]{"AUUC value", "AUUC random value"};
            String[] metricNames = EnumUtils.getNames(AUUCType.class);
            colHeaders = ArrayUtils.remove(metricNames, Arrays.asList(metricNames).indexOf("AUTO"));
            types = new String[metricsLength];
            formats = new String[metricsLength];
            for(i = 0; i < metricsLength; i++){
                types[i] = "double";
                formats[i] = "%f";
            }
            TwoDimTable auucs = new TwoDimTable("AUUC table", "All types of AUUC value", rowHeaders, colHeaders, types, formats, "Uplift type" );
            for(i = 0; i < metricsLength; i++) {
                auucs.set(0, i, auuc.auucByType(metrics[i]));
                auucs.set(1, i, auuc.auucRandomByType(metrics[i]));
            }
            this.auuc_table = new TwoDimTableV3().fillFromImpl(auucs);

            rowHeaders = new String[]{"AECU value"};
            TwoDimTable qinis = new TwoDimTable("AECU values table", "All types of AECU value", rowHeaders, colHeaders, types, formats, "Uplift type" );
            for(i = 0; i < metricsLength; i++) {
                qinis.set(0, i, auuc.aecuByType(metrics[i]));
            }
            this.aecu_table = new TwoDimTableV3().fillFromImpl(qinis);
        }
        return (S) this; 
    }
}

