package hex.maxrglm;

import hex.*;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.H2O;
import water.Key;
import water.api.schemas3.FrameV3;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;

import static hex.genmodel.utils.MathUtils.combinatorial;
import static hex.glm.GLMModel.GLMParameters.Family.AUTO;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.maxrglm.MaxRGLMUtils.*;

public class MaxRGLM extends ModelBuilder<MaxRGLMModel, MaxRGLMModel.MaxRGLMParameters, MaxRGLMModel.MaxRGLMModelOutput> {
    public String[][] _bestModelPredictors; // store for each predictor number, the best model predictors
    public double[] _bestR2Values;  // store the best R2 values of the best models with fix number of predictors
    DataInfo _dinfo;
    public int _numModelBuilt;      // number of models that are to be built
    public int _numPredictors;
    public String[] _predictorNames;
    public int _glmNFolds = 0;
    Model.Parameters.FoldAssignmentScheme _foldAssignment = null;
    String _foldColumn = null;
    

    public MaxRGLM(boolean startup_once) {
        super(new MaxRGLMModel.MaxRGLMParameters(), startup_once);
    }
    
    public MaxRGLM(MaxRGLMModel.MaxRGLMParameters parms) {
        super(parms);
        init(false);
    }
    
    public MaxRGLM(MaxRGLMModel.MaxRGLMParameters parms, Key<MaxRGLMModel> key) {
        super(parms, key);
        init(false);
    }
    
    @Override
    protected int nModelsInParallel(int folds) {
        return nModelsInParallel(1,2);  // disallow nfold cross-validation
    }

    @Override
    protected MaxRGLMDriver trainModelImpl() {
        return new MaxRGLMDriver();
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{ModelCategory.Regression};
    } // because of r2 usage

    @Override
    public boolean isSupervised() {
        return true;
    }

    @Override
    public boolean haveMojo() {
        return false;
    }

    @Override
    public boolean havePojo() {
        return false;
    }

    public ModelBuilder.BuilderVisibility buildVisibility() {
        return ModelBuilder.BuilderVisibility.Experimental;
    }

    public void init(boolean expensive) {
        if (_parms._nfolds > 0 || _parms._fold_column != null) {
            _glmNFolds = _parms._nfolds;
            if (_parms._fold_assignment != null) {
                _foldAssignment = _parms._fold_assignment;
                _parms._fold_assignment = null;
            }
            if (_parms._fold_column != null) {
                _foldColumn = _parms._fold_column;
                _parms._fold_column = null;
            }
            _parms._nfolds = 0;
        }
        super.init(expensive);
        if (expensive) {
            initValidateMaxRGLMParameters();
            initModelParameters();
        }
    }
    
    private void initModelParameters() {
        _numModelBuilt = calculateModelNumber(_numPredictors, _parms._max_predictor_number);
        _bestR2Values = new double[_parms._max_predictor_number];
        _bestModelPredictors = new String[_parms._max_predictor_number][];
    }

    private void initValidateMaxRGLMParameters() {
        if (nclasses() > 1)
            error("response", "MaxRGLM only works with regression.");
        
        if (!(AUTO.equals(_parms._family) || gaussian.equals(_parms._family)))
            error("_family", "MaxRGLM only supports Gaussian family");
        
        if (_parms._nparallelism < 0) 
            error("nparallelism", "must be >= 0.");
        
        if (_parms._nparallelism == 0)
            _parms._nparallelism = H2O.NUMCPUS;
        
        _predictorNames = generatePredictorNames(_parms);
        _numPredictors = _predictorNames.length;
        if (_parms._max_predictor_number < 1 || _parms._max_predictor_number > _numPredictors)
            error("max_predictor_number", "max_predictor_number must exceed 0 and be no greater" +
                    " than the number of predictors of the training frame.");
    }
    
    private class MaxRGLMDriver extends Driver {
        public final void buildModel() {
            MaxRGLMModel model = null;
            try {
                model = new MaxRGLMModel(dest(), _parms, new MaxRGLMModel.MaxRGLMModelOutput(MaxRGLM.this, _dinfo));
                model.write_lock(_job);
                // build glm model with num_predictors and find one with best R2
                for (int predNum=1; predNum <= _parms._max_predictor_number; predNum++) { 
                    int numModels = combinatorial(_numPredictors, predNum);
                    // generate the training frames with num_predictor predictors in the frame
                    Frame[] trainingFrames = generateTrainingFrames(_parms, predNum, _predictorNames, numModels, 
                            _foldColumn);
                    // generate the glm parameters;
                    GLMModel.GLMParameters[] trainingParams = generateGLMParameters(trainingFrames, _parms, _glmNFolds, 
                            _foldColumn, _foldAssignment);
                    // generate the builder;
                    GLM[] glmBuilder = buildGLMBuilders(trainingParams);
                    // call parallel build
                    GLM[] glmResults = ModelBuilderHelper.trainModelsParallel(glmBuilder, _parms._nparallelism);
                    // extract R2 and collect the best R2 and the predictors set
                    extractBestModels(_bestModelPredictors, _bestR2Values, glmResults, predNum-1);
                    // remove training frames from DKV
                    removeTrainingFrames(trainingFrames);
                    _job.update(predNum, "finished building all models with "+predNum+" predictors.");
                }
                _job.update(0, "Completed GLM model building.  Extracting results now.");
                model.update(_job);
                // copy best R2 and best predictors to model._output
                model._output.summarizeRunResult(_bestModelPredictors, _bestR2Values);
            } finally {
                model.update(_job);
                model.unlock(_job);
            }
        }

        @Override
        public void computeImpl() {
            _dinfo = new DataInfo(_train.clone(), _valid, 1, false,
                    DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                    _parms.missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.Skip,
                    _parms.imputeMissing(), _parms.makeImputer(), false, hasWeightCol(), hasOffsetCol(),
                    hasFoldCol(), null);
            init(true);
            if (error_count() > 0)
                throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(MaxRGLM.this);
            _job.update(0, "finished init and ready to build models");
            buildModel();
        }
    }
}
