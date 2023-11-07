package hex.adaboost;

import com.google.gson.*;
import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.apache.log4j.Logger;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Timer;
import water.util.TwoDimTable;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Implementation of AdaBoost algorithm based on
 *
 * Raul Rojas, "Adaboost and the Super Bowl of Classifiers A Tutorial Introduction to Adaptive Boosting"
 * Alexandru Niculescu-Mizil and Richard A. Caruana, "Obtaining Calibrated Probabilities from Boosting"
 * Y. Freund, R. Schapire, “A Decision-Theoretic Generalization of on-Line Learning and an Application to Boosting”, 1995.
 *
 * @author Adam Valenta
 */
public class AdaBoost extends ModelBuilder<AdaBoostModel, AdaBoostModel.AdaBoostParameters, AdaBoostModel.AdaBoostOutput> {
    private static final Logger LOG = Logger.getLogger(AdaBoost.class);
    private static final int MAX_LEARNERS = 100_000;

    private AdaBoostModel _model;
    private String _weightsName = "weights";
    private Gson _gsonParser;

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
        if(_parms._nlearners < 1 || _parms._nlearners > MAX_LEARNERS)
            error("n_estimators", "Parameter n_estimators must be in interval [1, "
                    + MAX_LEARNERS + "] but it is " + _parms._nlearners);
        if (_parms._weak_learner == AdaBoostModel.Algorithm.AUTO) {
            _parms._weak_learner = AdaBoostModel.Algorithm.DRF;
        }
        if (_parms._weights_column != null) {
            // _parms._weights_column cannot be used all time since it breaks scoring
            _weightsName = _parms._weights_column;
        }
        if( !(0. < _parms._learn_rate && _parms._learn_rate <= 1.0) ) {
            error("learn_rate", "learn_rate must be between 0 and 1");
        }
        if (useCustomWeakLearnerParameters()) {
            try {
                _gsonParser = new GsonBuilder()
                        .setFieldNamingStrategy(new PrecedingUnderscoreNamingStrategy())
                        .create();
                _gsonParser.fromJson(_parms._weak_learner_params, JsonObject.class);
            } catch (JsonSyntaxException syntaxException) {
                error("weak_learner_params", "Provided parameters are not in the valid json format. Got error: " + syntaxException.getMessage());
            }
        }
    }

    private boolean useCustomWeakLearnerParameters() {
        return _parms._weak_learner_params != null && !_parms._weak_learner_params.isEmpty();
    }

    private class PrecedingUnderscoreNamingStrategy implements FieldNamingStrategy
    {
        public String translateName(Field field)
        {
            String fieldName =
                    FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES.translateName(field);
            if (fieldName.startsWith("_"))
            {
                fieldName = fieldName.substring(1);
            }
            return fieldName;
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
            _model._output.alphas = new double[(int)_parms._nlearners];
            _model._output.models = new Key[(int)_parms._nlearners];

            Frame _trainWithWeights;
            if (_parms._weights_column == null) {
                _trainWithWeights = new Frame(train());
                Vec weights = _trainWithWeights.anyVec().makeCons(1,1,null,null)[0];
                _weightsName = _trainWithWeights.uniquify(_weightsName); // be sure that we are not accidentally using some column in the train
                _trainWithWeights.add(_weightsName, weights);
                DKV.put(_trainWithWeights);
                Scope.track(weights);
            } else {
                _trainWithWeights = _parms.train();
            }
            
            for (int n = 0; n < _parms._nlearners; n++) {
                Timer timer = new Timer();
                ModelBuilder job = chooseWeakLearner(_trainWithWeights);
                job._parms._seed += n;
                Model model = (Model) job.trainModel().get();
                DKV.put(model);
                Scope.untrack(model._key);
                _model._output.models[n] = model._key;
                Frame predictions = model.score(_trainWithWeights);
                Scope.track(predictions);

                CountWeTask countWe = new CountWeTask().doAll(_trainWithWeights.vec(_weightsName), _trainWithWeights.vec(_parms._response_column), predictions.vec("predict"));
                double eM = countWe.We / countWe.W;
                double alphaM = _parms._learn_rate * Math.log((1 - eM) / eM);
                _model._output.alphas[n] = alphaM;

                UpdateWeightsTask updateWeightsTask = new UpdateWeightsTask(alphaM);
                updateWeightsTask.doAll(_trainWithWeights.vec(_weightsName), _trainWithWeights.vec(_parms._response_column), predictions.vec("predict"));
                _job.update(1);
                _model.update(_job);
                LOG.info((n + 1) + ". estimator was built in " + timer.toString());
                LOG.info("*********************************************************************");
            }
            if (_trainWithWeights != _parms.train()) {
                DKV.remove(_trainWithWeights._key);
            }
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
            case GBM:
                return getGBMWeakLearner(frame);
            case DEEP_LEARNING:
                return getDeepLearningWeakLearner(frame);
            default:
            case DRF:
                return getDRFWeakLearner(frame);
                
        }
    }
    
    private DRF getDRFWeakLearner(Frame frame) {
        DRFModel.DRFParameters parms = useCustomWeakLearnerParameters() ? _gsonParser.fromJson(_parms._weak_learner_params, DRFModel.DRFParameters.class) : new DRFModel.DRFParameters();
        parms._train = frame._key;
        parms._response_column = _parms._response_column;
        parms._weights_column = _weightsName;
        parms._seed = _parms._seed;
        if (!useCustomWeakLearnerParameters()) {
            parms._mtries = 1;
            parms._min_rows = 1;
            parms._ntrees = 1;
            parms._sample_rate = 1;
            parms._max_depth = 1;
        }
        return new DRF(parms);
    }

    private GLM getGLMWeakLearner(Frame frame) {
        GLMModel.GLMParameters parms = useCustomWeakLearnerParameters() ? _gsonParser.fromJson(_parms._weak_learner_params, GLMModel.GLMParameters.class) : new GLMModel.GLMParameters();
        parms._train = frame._key;
        parms._response_column = _parms._response_column;
        parms._weights_column = _weightsName;
        parms._seed = _parms._seed;
        return new GLM(parms);
    }

    private GBM getGBMWeakLearner(Frame frame) {
        GBMModel.GBMParameters parms = useCustomWeakLearnerParameters() ? _gsonParser.fromJson(_parms._weak_learner_params, GBMModel.GBMParameters.class) : new GBMModel.GBMParameters();
        parms._train = frame._key;
        parms._response_column = _parms._response_column;
        parms._weights_column = _weightsName;
        if (!useCustomWeakLearnerParameters()) {
            parms._min_rows = 1;
            parms._ntrees = 1;
            parms._sample_rate = 1;
            parms._max_depth = 1;
            parms._seed = _parms._seed;
        }
        return new GBM(parms);
    }

    private DeepLearning getDeepLearningWeakLearner(Frame frame) {
        DeepLearningModel.DeepLearningParameters parms = useCustomWeakLearnerParameters() ? _gsonParser.fromJson(_parms._weak_learner_params, DeepLearningModel.DeepLearningParameters.class)  :new DeepLearningModel.DeepLearningParameters();
        parms._train = frame._key;
        parms._response_column = _parms._response_column;
        parms._weights_column = _weightsName;
        parms._seed = _parms._seed;
        if (!useCustomWeakLearnerParameters()) {
            parms._epochs = 10;
            parms._hidden = new int[]{2};
        }
        return new DeepLearning(parms);
    }

    public TwoDimTable createModelSummaryTable() {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();

        colHeaders.add("Number of weak learners"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Learn rate"); colTypes.add("int"); colFormat.add("%d");
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
        table.set(row, col++, _parms._nlearners);
        table.set(row, col++, _parms._learn_rate);
        table.set(row, col++, _parms._weak_learner.toString());
        table.set(row, col, _parms._seed);
        return table;
    }

}
