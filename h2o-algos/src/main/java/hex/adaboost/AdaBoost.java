package hex.adaboost;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import org.apache.log4j.Logger;
import water.DKV;
import water.Key;
import water.MRTask;
import water.Scope;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

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
                init(false);
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
//            Vec weights = train().anyVec().makeCons(1,1,null,null)[0];
//            train().add("weights", weights);
//            DKV.put(weights);
//            train()._key = Key.make();
//            DKV.put(train());
//            Scope.track(weights);
            System.out.println("train().toTwoDimTable() = " + train().toTwoDimTable());
            for (int n = 0; n < _parms._n_estimators; n++) {
                ModelBuilder job = chooseWeakLearner();
                job._parms._seed += n;
                Model model = (Model) job.trainModel().get();
                DKV.put(model);
                Scope.untrack(model._key);
                _model._output.models[n] = model._key;
                Frame score = model.score(train());
                Scope.track(score);

                CountWe countWe = new CountWe().doAll(train().vec("weights"), train().vec(_parms._response_column), score.vec("predict"));
                double e_m = countWe.We / countWe.W;
                double alpha_m = _parms._learning_rate * Math.log((1 - e_m) / e_m);
                _model._output.alphas[n] = alpha_m;

                UpdateW updateW = new UpdateW(alpha_m);
                updateW.doAll(train().vec("weights"), train().vec(_parms._response_column), score.vec("predict"));
            }
        }
    }
    
    private class CountWe extends MRTask<CountWe> {
        double W = 0;
        double We = 0;

        @Override
        public void map(Chunk weights, Chunk response, Chunk predict) {
            for (int row = 0; row < weights._len; row++) {
                double weight = weights.atd(row);
                W += weight;
                if (response.at8(row) != predict.at8(row)) {
                    We += weight;
                }
            }
        }

        @Override
        public void reduce(CountWe mrt) {
            W += mrt.W;
            We += mrt.We;
        }        
    }

    private class UpdateW extends MRTask<UpdateW> {
        double exp_am;
        double exp_am_inverse;

        public UpdateW(double alpha_m) {
            exp_am = Math.exp(alpha_m);
            exp_am_inverse = Math.exp(-alpha_m);
        }

        @Override
        public void map(Chunk weights, Chunk response, Chunk predict) {
            for (int row = 0; row < weights._len; row++) {
                double weight = weights.atd(row);
                if (response.at8(row) != predict.at8(row)) {
                    weights.set(row, weight*exp_am);
                } else {
                    weights.set(row, weight*exp_am_inverse);
                }
            }
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
    
    private ModelBuilder chooseWeakLearner() {
        switch (_parms._weak_learner) {
            case GLM:
                return getGLMWeakLearner();
            default:
            case DRF:
                return getDRFWeakLearner();
                
        }
    }
    
    private DRF getDRFWeakLearner() {
        DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
        parms._train = _parms._train;
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

    private GLM getGLMWeakLearner() {
        GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
        parms._train = _parms._train;
        parms._response_column = _parms._response_column;
        return new GLM(parms);
    }

}
