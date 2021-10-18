package hex.anovaglm;

import hex.DataInfo;
import hex.ModelBuilder;
import hex.ModelBuilderHelper;
import hex.ModelCategory;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.DKV;
import water.Key;
import water.Scope;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static hex.anovaglm.ANOVAGLMUtils.*;
import static hex.gam.MatrixFrameUtils.GamUtils.keepFrameKeys;
import static hex.glm.GLMModel.GLMParameters;
import static hex.glm.GLMModel.GLMParameters.Family.*;
import static water.util.ArrayUtils.flat;

public class ANOVAGLM extends ModelBuilder<ANOVAGLMModel, ANOVAGLMModel.ANOVAGLMParameters, ANOVAGLMModel.ANOVAGLMModelOutput> {
  public int _numberOfModels = 4;// (A, A*B), (B, A*B), (A, B), (A, B, A*B)
  public int _numberOfPredCombo = 3;
  public int _numberOfPredictors = 3; // A, B, interaction of A and B
  DataInfo _dinfo;
  String[][] _predictComboNames; // store single predictors, predictor interaction columns
  int[] _degreeOfFreedom;
  String[] _modelNames; // store model description
  String[] _predNamesIndividual;  // store individual column names
  public String[][] _transformedColNames;  // store expanded names for single predictors, predictor interactions.
  public int[] _predictorColumnStart;
  

  public ANOVAGLM(boolean startup_once) {
    super(new ANOVAGLMModel.ANOVAGLMParameters(), startup_once);
  }

  public ANOVAGLM(ANOVAGLMModel.ANOVAGLMParameters parms) {
    super(parms);
    init(false);
  }

  public ANOVAGLM(ANOVAGLMModel.ANOVAGLMParameters parms, Key<ANOVAGLMModel> key) {
    super(parms, key);
    init(false);
  }

  @Override
  protected int nModelsInParallel(int folds) {  // disallow nfold cross-validation
    return nModelsInParallel(1, 2);
  }

  @Override
  protected ANOVAGLMDriver trainModelImpl() {
    return new ANOVAGLMDriver();
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{ModelCategory.Regression, ModelCategory.Binomial, ModelCategory.Multinomial, 
            ModelCategory.Ordinal};
  }

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

  public BuilderVisibility buildVisibility() {
    return BuilderVisibility.Experimental;
  }

  public void init(boolean expensive) {
    super.init(expensive);
    if (expensive) {
      initValidateAnovaGLMParameters();
    }
  }

  /***
   * Init and validate ANOVAGLMParameters.
   */
  private void initValidateAnovaGLMParameters() {
    if (_parms._link == null)
      _parms._link = GLMModel.GLMParameters.Link.family_default;

    _dinfo = new DataInfo(_train.clone(), _valid, 1, true, DataInfo.TransformType.NONE,
            DataInfo.TransformType.NONE,
            _parms.missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.Skip,
            _parms.imputeMissing(), _parms.makeImputer(), false, hasWeightCol(), hasOffsetCol(),
            hasFoldCol(), null);
    _numberOfPredictors = _dinfo._nums + _dinfo._cats;

    if (_numberOfPredictors < 2)
      error("predictors", " there must be at least two predictors.");

    if (_parms._highest_interaction_term == 0)
      _parms._highest_interaction_term = _numberOfPredictors;

    if (_parms._highest_interaction_term < 1 || _parms._highest_interaction_term > _numberOfPredictors)
      error("highest_interaction_term", " must be >= 1 or <= number of predictors.");
    
    if (!(gaussian.equals(_parms._family) || tweedie.equals(_parms._family) || poisson.equals(_parms._family))) {
      _parms._compute_p_values = false;
      _parms._remove_collinear_columns = false;
    }

    if (nclasses() > 2)
      error("family", " multinomial and ordinal are not supported at this point.");
    
    _numberOfPredCombo = calculatePredComboNumber(_numberOfPredictors, _parms._highest_interaction_term);
    _numberOfModels = _numberOfPredCombo + 1;
    _predNamesIndividual = extractPredNames(_dinfo, _numberOfPredictors);
    _predictComboNames = generatePredictorCombos(_predNamesIndividual, _parms._highest_interaction_term);
    _transformedColNames = new String[_numberOfPredCombo][];
    _predictorColumnStart = new int[_numberOfPredCombo];
    _degreeOfFreedom = new int[_numberOfPredCombo];
    generatePredictorNames(_predictComboNames, _transformedColNames, _predictorColumnStart, _degreeOfFreedom, _dinfo);
    _modelNames = generateModelNames(_predictComboNames);

    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(ANOVAGLM.this);
  }

  private class ANOVAGLMDriver extends Driver {
    String[] _allTransformedColNames;   // flatten new column names
    Key<Frame> _transformedColsKey;     // store transformed column frame key
    Frame[] _trainingFrames;            // store generated frames
    GLMParameters[] _glmParams;         // store GLMParameters needed to generate all the data
    GLM[] _glmBuilder;                  // store GLM Builders to be build in parallel
    GLM[] _glmResults;
    Frame _completeTransformedFrame;    // store transformed frame

    public final void buildModel() {
      ANOVAGLMModel model = null;
      try {
        _dinfo = new DataInfo(_completeTransformedFrame, _valid, 1, false,
                DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                _parms.missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.Skip,
                _parms.imputeMissing(), _parms.makeImputer(), false, hasWeightCol(), hasOffsetCol(),
                hasFoldCol(), null);
        model = new ANOVAGLMModel(dest(), _parms, new ANOVAGLMModel.ANOVAGLMModelOutput(ANOVAGLM.this, _dinfo));
        model.write_lock(_job);
        if (_parms._save_transformed_framekeys) {
          model._output._transformed_columns_key = _transformedColsKey;
        }
        _trainingFrames = buildTrainingFrames(_transformedColsKey, _numberOfModels, _transformedColNames, _parms);  // build up training frames
        _glmParams = buildGLMParameters(_trainingFrames, _parms);
        _job.update(1, "calling GLM to build GLM models ...");
        _glmBuilder = buildGLMBuilders(_glmParams);
        _glmResults = ModelBuilderHelper.trainModelsParallel(_glmBuilder, _parms._nparallelism); // set to 4 according to Michalk
        model._output._glmModels = extractGLMModels(_glmResults);
        model._output.copyGLMCoeffs(_modelNames);
        fillModelMetrics(model, model._output._glmModels[_numberOfPredCombo], _trainingFrames[_numberOfPredCombo]); // take full model metrics as our model metrics
        model.fillOutput(combineAndFlat(_predictComboNames), _degreeOfFreedom);
        _job.update(0, "Completed GLM model building.  Extracting metrics from GLM models and building" +
                " ANOVAGLM outputs");
        model.update(_job);
      } finally {
        final List<Key> keep = new ArrayList<>();
        int numFrame2Delete = _parms._save_transformed_framekeys ? (_trainingFrames.length - 1) : _trainingFrames.length;
        removeFromDKV(_trainingFrames, numFrame2Delete);
        if (model != null) {
          if (_parms._save_transformed_framekeys)
            keepFrameKeys(keep, _transformedColsKey);
          else
            DKV.remove(_transformedColsKey);
          Scope.untrack(keep.toArray(new Key[keep.size()]));
          model.update(_job);
          model.unlock(_job);
        }
      }
    }

    /***
     * This method will transform the training frame such that the constraints on the GLM parameters will be satisfied.  
     * Refer to ANOVAGLMTutorial https://h2oai.atlassian.net/browse/PUBDEV-8088 section III.II.
     */
    void generateTransformedColumns() {
      _allTransformedColNames = flat(_transformedColNames);
      List<String> expandedColNames = new ArrayList<>(Arrays.asList(_allTransformedColNames));
      if (hasWeightCol())
        expandedColNames.add(_parms._weights_column);
      if (hasOffsetCol())
        expandedColNames.add(_parms._offset_column);
      expandedColNames.add(_parms._response_column);
      GenerateTransformColumns gtc = new GenerateTransformColumns(_transformedColNames, _parms, _dinfo, 
              _predNamesIndividual.length, _predictComboNames);
      gtc.doAll(expandedColNames.size(), Vec.T_NUM, _dinfo._adaptedFrame);
      _completeTransformedFrame = gtc.outputFrame(Key.make(), expandedColNames.toArray(new String[0]), null);
      if (_train.vec(_parms._response_column).isCategorical() && 
              !_completeTransformedFrame.vec(_parms._response_column).isCategorical())
        _completeTransformedFrame.replace(_completeTransformedFrame.numCols()-1,
                _completeTransformedFrame.vec(_parms._response_column).toCategoricalVec()).remove();
      _transformedColsKey = _completeTransformedFrame._key; // contains transformed predicts, weight/offset and response columns
      DKV.put(_completeTransformedFrame);
    }

    @Override
    public void computeImpl() {
      init(true);
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(ANOVAGLM.this);
      generateTransformedColumns();
      _job.update(0, "Finished transforming training frame");
      buildModel();
    }
  }
}
