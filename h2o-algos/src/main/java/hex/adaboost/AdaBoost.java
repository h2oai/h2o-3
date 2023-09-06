package hex.adaboost;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.dt.DTModel;
import org.apache.log4j.Logger;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO valenad1
 *
 * @author Adam Valenta
 */
public class AdaBoost extends ModelBuilder<AdaBoostModel, AdaBoostModel.AdaBoostParameters, AdaBoostModel.AdaBoostOutput> {
    private static final Logger LOG = Logger.getLogger(AdaBoost.class);

    private AdaBoostModel _model;

    // Called from an http request
    public AdaBoost(AdaBoostModel.AdaBoostParameters parms) {
        super(parms);
        init(false);
    }

    public AdaBoost(boolean startup_once) {
        super(new AdaBoostModel.AdaBoostParameters(), startup_once);
    }

    @Override
    public boolean havePojo() {
        return false;
    }

    @Override
    public boolean haveMojo() {
        return false;
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (expensive) {
            if (_parms._weak_learner == AdaBoostModel.Algorithm.AUTO) {
                _parms._weak_learner = AdaBoostModel.Algorithm.DRF;
            }
        }
    }

    private class AdaBoostDriver extends Driver {

        @Override
        public void computeImpl() {
            _model = null;
            try {
                init(true);
                if (error_count() > 0) {
                    throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(AdaBoost.this);
                }
                _model = new AdaBoostModel(dest(), _parms,
                        new AdaBoostModel.AdaBoostOutput(AdaBoost.this));
                _model.delete_and_lock(_job);
                buildAdaboost();
                LOG.info(_model.toString());
            } finally {
                if (_model != null)
                    _model.unlock(_job);
            }
        }

        private void buildAdaboost() {
            _model._output.alphas = new double[(int)_parms._n_estimators];
            _model._output.models = new Key[(int)_parms._n_estimators];
            
            Frame _trainWithWeights = new Frame(train());
            Vec weights = _trainWithWeights.anyVec().makeCons(1,1,null,null)[0];
            _trainWithWeights.add("weights", weights);
            DKV.put(_trainWithWeights);
            Scope.track(weights);
            
            for (int n = 0; n < _parms._n_estimators; n++) {
                ModelBuilder job = chooseWeakLearner(_trainWithWeights);
                job._parms._seed += n;
                Model model = (Model) job.trainModel().get();
                DKV.put(model);
                Scope.untrack(model._key);
                _model._output.models[n] = model._key;
                Frame score = model.score(_trainWithWeights);
                Scope.track(score);

                CountWeTask countWe = new CountWeTask().doAll(_trainWithWeights.vec("weights"), _trainWithWeights.vec(_parms._response_column), score.vec("predict"));
                double e_m = countWe.We / countWe.W;
                double alpha_m = _parms._learning_rate * Math.log((1 - e_m) / e_m);
                _model._output.alphas[n] = alpha_m;

                UpdateWeightsTask updateWeightsTask = new UpdateWeightsTask(alpha_m);
                updateWeightsTask.doAll(_trainWithWeights.vec("weights"), _trainWithWeights.vec(_parms._response_column), score.vec("predict"));
                _job.update(1);
                _model.update(_job);
            }
            DKV.remove(_trainWithWeights._key);
            _model._output._model_summary = createModelSummaryTable();
        }
    }

    @Override
    protected Driver trainModelImpl() {
        return new AdaBoostDriver();
    }

    @Override
    public BuilderVisibility builderVisibility() {
        return BuilderVisibility.Experimental;
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{
                ModelCategory.Binomial,
        };
    }

    @Override
    public boolean isSupervised() {
        return true;
    }
    
    private ModelBuilder chooseWeakLearner(Frame frame) {
        switch (_parms._weak_learner) {
            case GLM:
                return getGLMWeakLearner(frame);
            default:
            case DRF:
                return getDRFWeakLearner(frame);
                
        }
    }
    
    private DRF getDRFWeakLearner(Frame frame) {
        DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
        parms._train = frame._key;
        parms._response_column = _parms._response_column;
        parms._mtries = 1;
        parms._min_rows = 1;
        parms._weights_column = "weights";
        parms._ntrees = 1;
        parms._sample_rate = 1;
        parms._max_depth = 1;
        parms._seed = _parms._seed;
        return new DRF(parms);
    }

    private GLM getGLMWeakLearner(Frame frame) {
        GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
        parms._train = frame._key;
        parms._response_column = _parms._response_column;
        return new GLM(parms);
    }

    public TwoDimTable createModelSummaryTable() {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();

        colHeaders.add("Number of weak learners"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Learning rate"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Weak learner"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Seed"); colTypes.add("long"); colFormat.add("%d");

        final int rows = 1;
        TwoDimTable table = new TwoDimTable(
                "Model Summary", null,
                new String[rows],
                colHeaders.toArray(new String[0]),
                colTypes.toArray(new String[0]),
                colFormat.toArray(new String[0]),
                "");
        int row = 0;
        int col = 0;
        table.set(row, col++, _parms._n_estimators);
        table.set(row, col++, _parms._learning_rate);
        table.set(row, col++, _parms._weak_learner.toString());
        table.set(row, col, _parms._seed);
        return table;
    }

}
