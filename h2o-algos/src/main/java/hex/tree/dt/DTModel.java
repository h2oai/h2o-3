package hex.tree.dt;

import hex.*;
import org.apache.log4j.Logger;
import water.*;


public class DTModel extends Model<DTModel, DTModel.DTParameters, DTModel.DTOutput> {

    private static final Logger LOG = Logger.getLogger(DTModel.class);


    public DTModel(Key<DTModel> selfKey, DTParameters parms,
                   DTOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        switch (_output.getModelCategory()) {
            case Binomial:
                return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
            case Multinomial:
                return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain, _parms._auc_type);
            case Regression:
                return new ModelMetricsRegression.MetricBuilderRegression();
            default:
                throw H2O.unimpl();
        }
    }

    @Override
    protected double[] score0(double[] data, double[] preds) {
        assert _output._treeKey != null : "Output has no tree, check if tree is properly set to the output.";
        // compute score for given point
        CompressedDT tree = DKV.getGet(_output._treeKey);
        DTPrediction prediction = tree.predictRowStartingFromNode(data, 0, "");
        preds[0] = prediction.classPrediction;
        for (int i = 0; i < prediction.probabilities.length; i++) {
            preds[i + 1] =  prediction.probabilities[i];
        }

        return preds;
    }

    public static class DTOutput extends Model.Output {
        public int _max_depth;
        public int _limitNumSamplesForSplit;

        public Key<CompressedDT> _treeKey;

        public DTOutput(DT dt) {
            super(dt);
            _max_depth = dt._parms._max_depth;
            _limitNumSamplesForSplit = dt._parms._min_rows;
        }

    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        Keyed.remove(_output._treeKey, fs, true);
        return super.remove_impl(fs, cascade);
    }

    @Override
    protected AutoBuffer writeAll_impl(AutoBuffer ab) {
        ab.putKey(_output._treeKey);
        return super.writeAll_impl(ab);
    }

    @Override
    protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
        ab.getKey(_output._treeKey, fs);
        return super.readAll_impl(ab, fs);
    }

    public static class DTParameters extends Model.Parameters {
        long seed = -1; //ignored
        /**
         * Depth (max depth) of the tree
         */
        public int _max_depth;

        public int _min_rows;

        public DTParameters() {
            super();
            _max_depth = 20;
            _min_rows = 10;
        }

        @Override
        public String algoName() {
            return "DT";
        }

        @Override
        public String fullName() {
            return "Decision Tree";
        }

        @Override
        public String javaName() {
            return DTModel.class.getName();
        }

        @Override
        public long progressUnits() {
            return 1;
        }
    }
}
