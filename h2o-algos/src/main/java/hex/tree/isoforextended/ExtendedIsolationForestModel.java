package hex.tree.isoforextended;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.tree.CompressedTree;
import hex.tree.isofor.ModelMetricsAnomaly;
import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import org.apache.log4j.Logger;
import water.*;
import water.fvec.Frame;

/**
 * 
 * @author Adam Valenta
 */
public class ExtendedIsolationForestModel extends Model<ExtendedIsolationForestModel, ExtendedIsolationForestModel.ExtendedIsolationForestParameters, 
        ExtendedIsolationForestModel.ExtendedIsolationForestOutput> {
    private static final Logger LOG = Logger.getLogger(ExtendedIsolationForestModel.class);

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
    protected double[] score0(double[] data, double[] preds) {
        assert _output._iTreeKeys != null : "Output has no trees, check if trees are properly set to the output.";
        // compute score for given point
        double pathLength = 0;
        for (Key<CompressedIsolationTree> iTreeKey : _output._iTreeKeys) {
            CompressedIsolationTree iTree = DKV.getGet(iTreeKey);
            double iTreeScore = iTree.computePathLength(data);
            pathLength += iTreeScore;
            LOG.trace("iTreeScore " + iTreeScore);
        }
        pathLength = pathLength / _output._ntrees;
        LOG.trace("Path length " + pathLength);
        double anomalyScore = anomalyScore(pathLength);
        LOG.trace("Anomaly score " + anomalyScore);
        preds[0] = anomalyScore;
        preds[1] = pathLength;
        return preds;
    }

    /**
     * Anomaly score computation comes from Equation 1 in paper
     *
     * @param pathLength path from root to leaf
     * @return anomaly score in range [0, 1]
     */
    private double anomalyScore(double pathLength) {
        return Math.pow(2, -1 * (pathLength / 
                CompressedIsolationTree.averagePathLengthOfUnsuccessfulSearch(_output._sample_size)));
    }

    public static class ExtendedIsolationForestParameters extends Model.Parameters {

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

        @Override
        public long progressUnits() {
            return _ntrees;
        }

        /**
         * Number of trees in the forest
         */
        public int _ntrees;

        /**
         * Maximum is N - 1 (N = numCols). Minimum is 0. EIF with extension_level = 0 behaves like Isolation Forest.
         */
        public int _extension_level;

        /**
         * Number of randomly selected rows from original data before each tree build.
         */
        public int _sample_size;

        public ExtendedIsolationForestParameters() {
            super();
            _ntrees = 100;
            _sample_size = 256;
            _extension_level = 0;
        }        
    }

    public static class ExtendedIsolationForestOutput extends Model.Output {

        public int _ntrees;
        public long _sample_size;
        
        public Key<CompressedIsolationTree>[] _iTreeKeys;
        
        public ExtendedIsolationForestOutput(ExtendedIsolationForest eif) {
            super(eif);
            _ntrees = eif._parms._ntrees;
            _sample_size = eif._parms._sample_size;
        }

        @Override
        public ModelCategory getModelCategory() {
            return ModelCategory.AnomalyDetection;
        }
    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        for (Key<CompressedIsolationTree> iTreeKey : _output._iTreeKeys) {
            Keyed.remove(iTreeKey, fs, true);
        }
        return super.remove_impl(fs, cascade);
    }

    @Override
    protected AutoBuffer writeAll_impl(AutoBuffer ab) {
        for (Key<CompressedIsolationTree> iTreeKey : _output._iTreeKeys) {
            ab.putKey(iTreeKey);
        }
        return super.writeAll_impl(ab);
    }

    @Override
    protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
        for (Key<CompressedIsolationTree> iTreeKey : _output._iTreeKeys) {
            ab.getKey(iTreeKey, fs);
        }
        return super.readAll_impl(ab,fs);
    }
}
