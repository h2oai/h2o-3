package hex.adaboost;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import org.apache.log4j.Logger;
import water.*;

public class AdaBoostModel extends Model<AdaBoostModel, AdaBoostModel.AdaBoostParameters, AdaBoostModel.AdaBoostOutput> {
    private static final Logger LOG = Logger.getLogger(AdaBoostModel.class);

    public enum Algorithm {DRF, GLM, GBM, DEEP_LEARNING,AUTO}

    public AdaBoostModel(Key<AdaBoostModel> selfKey, AdaBoostParameters parms,
                         AdaBoostOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        if (_output.getModelCategory() == ModelCategory.Binomial) {
            return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
        }
        throw H2O.unimpl("AdaBoost currently support only binary classification");
    }

    @Override
    protected String[] makeScoringNames(){
        return new String[]{"predict", "p0", "p1"};
    }
    
    @Override
    protected double[] score0(double[] data, double[] preds) {
        double alphas0 = 0;
        double alphas1 = 0;
        double linearCombination = 0;
        for (int i = 0; i < _output.alphas.length; i++) {
            Model model = DKV.getGet(_output.models[i]);
            if (model.score(data) == 0) {
                linearCombination += _output.alphas[i]*-1;
                alphas0 += _output.alphas[i];
            } else {
                linearCombination += _output.alphas[i];
                alphas1 += _output.alphas[i];
            }
        }
        preds[0] = alphas0 > alphas1 ? 0 : 1;
        preds[2] = 1/(1 + Math.exp(-2*linearCombination));
        preds[1] = 1 - preds[2];
        return preds;
    }

    @Override protected boolean needsPostProcess() { return false; /* pred[0] is already set by score0 */ }

    public static class AdaBoostOutput extends Model.Output {
        public double[] alphas;
        public Key<Model>[] models;

        public AdaBoostOutput(AdaBoost adaBoostModel) {
            super(adaBoostModel);
        }
    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        for (Key<Model> iTreeKey : _output.models) {
            Keyed.remove(iTreeKey, fs, true);
        }
        return super.remove_impl(fs, cascade);
    }

    @Override
    protected AutoBuffer writeAll_impl(AutoBuffer ab) {
        for (Key<Model> iTreeKey : _output.models) {
            ab.putKey(iTreeKey);
        }
        return super.writeAll_impl(ab);
    }

    @Override
    protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
        for (Key<Model> iTreeKey : _output.models) {
            ab.getKey(iTreeKey, fs);
        }
        return super.readAll_impl(ab,fs);
    }

    public static class AdaBoostParameters extends Model.Parameters {

        /**
         * Number of weak learners to train. Defaults to 50.
         */
        public int _nlearners;

        /**
         * Choose a weak learner type. Defaults to DRF.
         */
        public Algorithm _weak_learner;

        /**
         * Specify how quickly the training converge. Number in (0,1]. Defaults to 0.5.
         */
        public double _learn_rate;

        /**
         * Custom _weak_learner parameters.
         */
        public String _weak_learner_params;

        @Override
        public String algoName() {
            return "AdaBoost";
        }

        @Override
        public String fullName() {
            return "AdaBoost";
        }

        @Override
        public String javaName() {
            return AdaBoostModel.class.getName();
        }

        @Override
        public long progressUnits() {
            return _nlearners;
        }

        public AdaBoostParameters() {
            super();
            _nlearners = 50;
            _weak_learner = Algorithm.AUTO;
            _learn_rate = 0.5;
            _weak_learner_params = "";
        }
    }
}
