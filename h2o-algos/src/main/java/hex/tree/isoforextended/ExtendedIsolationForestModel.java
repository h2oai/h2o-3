package hex.tree.isoforextended;

import hex.ModelCategory;
import hex.ModelMetrics;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.SharedTree;
import hex.tree.SharedTreeModel;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.SBPrintStream;

/**
 * 
 * @author Adam Valenta
 */
public class ExtendedIsolationForestModel extends SharedTreeModel<ExtendedIsolationForestModel, ExtendedIsolationForestModel.ExtendedIsolationForestParameters, 
        ExtendedIsolationForestModel.ExtendedIsolationForestOutput> {
    
    public ExtendedIsolationForestModel(Key<ExtendedIsolationForestModel> selfKey, ExtendedIsolationForestParameters parms,
                                        ExtendedIsolationForestOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        return new ModelMetricsAnomaly.MetricBuilderAnomaly("Extended Isolation Forest Metrics", false);
    }

    @Override
    protected String[] makeScoringNames(){
        return new String[]{"anomaly_score", "mean_length"};
    }

    @Override
    protected String[][] makeScoringDomains(Frame adaptFrm, boolean computeMetrics, String[] names) {
        assert names.length == 2;
        return new String[2][];
    }

    @Override 
    protected double[] score0(double[] data, double[] preds, double offset, int ntrees) {
        super.score0(data, preds, offset, ntrees);
        if (ntrees >= 1) preds[1] = preds[0] / ntrees;
        
        // compute score for given point
        double pathLength = 0;
        for (ExtendedIsolationForest.IsolationTree iTree : _output.iTrees) {
            double iTreeScore = iTree.computePathLength(data);
            pathLength += iTreeScore;
            Log.debug("iTreeScore " + iTreeScore);
        }
        pathLength = pathLength / _output.iTrees.length;
        Log.debug("pathLength " + pathLength);
        double anomalyScore = anomalyScore(pathLength);
        Log.debug("Anomaly score " + anomalyScore);
        preds[0] = anomalyScore;
        preds[1] = pathLength;
        return preds;
    }

    @Override protected void toJavaUnifyPreds(SBPrintStream body) {
        throw new UnsupportedOperationException("Extended Isolation Forest support only MOJOs.");
    }

    @Override
    public ExtendedIsolationForestMojoWriter getMojo() {
        return new ExtendedIsolationForestMojoWriter(this);
    }
    
    /**
     * Anomaly score computation comes from Equation 1 in paper
     *
     * @param pathLength path from root to leaf
     * @return anomaly score in range [0, 1]
     */
    private double anomalyScore(double pathLength) {
        return Math.pow(2, -1 * (pathLength / 
                ExtendedIsolationForest.IsolationTree.averagePathLengthOfUnsuccesfullSearch(_parms._sample_size)));
    }

    public static class ExtendedIsolationForestParameters extends SharedTreeModel.SharedTreeParameters {

        @Override
        public String algoName() {
            return "ExtendedIsolationForest";
        }

        @Override
        public String fullName() {
            return "Extended Isolation Forest";
        }

        @Override
        public String javaName() {
            return ExtendedIsolationForestModel.class.getName();
        }

        // Maximum is N - 1 (N = numCols). Minimum is 0. EIF with extension_level = 0 behaves like Isolation Forest.
        public int extension_level;

        public long _sample_size;

        public ExtendedIsolationForestParameters() {
            super();
            _max_depth = 8; // log2(_sample_size)
            _min_rows = 1;
            _min_split_improvement = 0;
            _nbins = 2;
            _nbins_cats = 2;
            // _nbins_top_level = 2;
            _histogram_type = HistogramType.Random;
            _distribution = DistributionFamily.gaussian;

            // early stopping
            _stopping_tolerance = 0.01; // (default 0.001 is too low for the default criterion anomaly_score)

            _sample_size = 256;
            extension_level = 0;
        }        
    }

    public static class ExtendedIsolationForestOutput extends SharedTreeModel.SharedTreeOutput {
        
        public ExtendedIsolationForest.IsolationTree[] iTrees;
        
        public ExtendedIsolationForestOutput(SharedTree b) {
            super(b);
        }

        @Override
        public ModelCategory getModelCategory() {
            return ModelCategory.AnomalyDetection;
        }
    }
}
