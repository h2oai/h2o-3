package hex.Infogram;

import hex.*;
import hex.Infogram.InfogramModel.InfogramModelOutput;
import hex.Infogram.InfogramModel.InfogramParameters;
import hex.ModelMetrics.MetricBuilder;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.*;
import java.util.stream.IntStream;
import hex.genmodel.utils.DistributionFamily;
import static hex.Infogram.InfogramModel.InfogramModelOutput.sortCMIRel;
import static hex.Infogram.InfogramModel.InfogramParameters.Algorithm.AUTO;
import static hex.Infogram.InfogramModel.InfogramParameters.Algorithm.gbm;
import static hex.Infogram.InfogramUtils.*;
import static hex.gam.MatrixFrameUtils.GamUtils.keepFrameKeys;
import static water.util.ArrayUtils.sort;
import static water.util.ArrayUtils.sum;

public class Infogram extends ModelBuilder<hex.Infogram.InfogramModel, InfogramParameters,
        InfogramModelOutput> {
  static final double NORMALIZE_ADMISSIBLE_INDEX = 1.0/Math.sqrt(2.0);
  boolean _buildCore;       // true to find core predictors, false to find admissible predictors
  String[] _topKPredictors; // contain the names of top predictors to consider for infogram
  Frame _baseOrSensitiveFrame = null;
  String[] _modelDescription; // describe each model in terms of predictors used
  int _numModels; // number of models to build
  double[] _cmi;  // store conditional mutual information
  double[] _cmiValid;
  double[] _cmiCV;
  double[] _cmiRaw;  // raw conditional mutual information
  double[] _cmiRawValid;  // raw conditional mutual information from validation frame
  double[] _cmiRawCV;
  String[] _columnsCV;
  TwoDimTable _varImp;
  int _numPredictors; // number of predictors in training dataset
  Key<Frame> _cmiRelKey;
  Key<Frame> _cmiRelKeyValid;
  Key<Frame> _cmiRelKeyCV;
  boolean _cvDone = false;  // on when we are inside cv
  private transient InfogramModel _model;
  long _validNonZeroNumRows;
  int _nFoldOrig = 0;
  Model.Parameters.FoldAssignmentScheme _foldAssignmentOrig = null;
  String _foldColumnOrig = null;
  
  public Infogram(boolean startup_once) { super(new InfogramParameters(), startup_once);}

  public Infogram(InfogramParameters parms) {
    super(parms);
    init(false);
  }

  public Infogram(InfogramParameters parms, Key<hex.Infogram.InfogramModel> key) {
    super(parms, key);
    init(false);
  }
  
  @Override
  protected Driver trainModelImpl() {
    return new InfogramDriver();
  }

  @Override
  protected int nModelsInParallel(int folds) {
    return nModelsInParallel(folds,2);
  }

  /***
   * This is called before cross-validation is carried out
   */
  @Override
  protected void cv_init() {
    super.cv_init();
    info("cross-validation", "cross-validation infogram information is stored in frame with key" +
            " labeled as admissible_score_key_cv and the admissible features in admissible_features_cv.");
    if (error_count() > 0) {
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(Infogram.this);
    }
  }

  @Override
  protected MetricBuilder makeCVMetricBuilder(ModelBuilder<InfogramModel, InfogramParameters, InfogramModelOutput> cvModelBuilder, Futures fs) {
    return null;   //infogram does not support scoring
  }

  // find the best alpha/lambda values used to build the main model moving forward by looking at the devianceValid
  @Override
  protected void cv_computeAndSetOptimalParameters(ModelBuilder[] cvModelBuilders) {
    int nBuilders = cvModelBuilders.length;
    double[][] cmiRaw = new double[nBuilders][];
    List<List<String>> columns = new ArrayList<>();
    long[] nObs = new long[nBuilders];
    for (int i = 0; i < cvModelBuilders.length; i++) {  // run cv for each lambda value
      InfogramModel g = (InfogramModel) cvModelBuilders[i].dest().get();
      Scope.track_generic(g);
      extractInfogramInfo(g, cmiRaw, columns, i);
      nObs[i] = g._output._validNonZeroNumRows;
    }
    calculateMeanInfogramInfo(cmiRaw, columns, nObs);
    for (int i = 0; i < cvModelBuilders.length; i++) {
      Infogram g = (Infogram) cvModelBuilders[i];
      InfogramModel gm = g._model;
      gm.write_lock(_job);
      gm.update(_job);
      gm.unlock(_job);
    }
    _cvDone = true;  // cv is done and we are going to build main model next
  }

  @Override
  protected void cv_mainModelScores(int N, MetricBuilder[] mbs, ModelBuilder<InfogramModel, InfogramParameters, InfogramModelOutput>[] cvModelBuilders) {
    //infogram does not support scoring
  }

  public void calculateMeanInfogramInfo(double[][] cmiRaw, List<List<String>> columns,
                                        long[] nObs) {
    int nFolds = cmiRaw.length;
    Set<String> allNames = new HashSet<>(); // store all names
    for (List<String> oneFold : columns)
      allNames.addAll(oneFold);
    List<String> allNamesList = new ArrayList<>(allNames);
    int nPreds = allNames.size();
    _cmiCV = new double[nPreds];
    _cmiRawCV = new double[nPreds];
    double oneOverNObsSum = 1.0/sum(nObs);
    int foldPredSize = cmiRaw[0].length;
    for (int fIndex = 0; fIndex < nFolds; fIndex++) {  // get sum of each fold
      List<String> oneFoldC = columns.get(fIndex);
      double scale =  nObs[fIndex] * oneOverNObsSum;
      for (int pIndex = 0; pIndex < foldPredSize; pIndex++) { // go through each predictor
        String colName = oneFoldC.get(pIndex);    // use same predictor order as zero fold
        int allNameIndex = allNamesList.indexOf(colName);  // current fold colName order index change
        _cmiRawCV[allNameIndex] += cmiRaw[fIndex][pIndex] * scale;
      }
    }
    // normalize CMI and relevane again
    double maxCMI = _parms._top_n_features == nPreds
            ? ArrayUtils.maxValue(_cmiRawCV)
            : Arrays.stream(_cmiRawCV).sorted().toArray()[Math.min(_parms._top_n_features, nPreds)-1];
    double oneOverMaxCMI = maxCMI == 0 ? 0 : 1.0/maxCMI;
    for (int pIndex = 0; pIndex < nPreds; pIndex++) {
      _cmiCV[pIndex] = _cmiRawCV[pIndex]*oneOverMaxCMI;
    }
    _columnsCV = allNamesList.stream().toArray(String[]::new);
  }
  
  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[] { ModelCategory.Binomial, ModelCategory.Multinomial};
  }

  @Override
  public boolean isSupervised() {
    return true;
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
    if (expensive)
      validateInfoGramParameters();
  }

  private void validateInfoGramParameters() {
    Frame dataset = _parms.train();

    if (!_parms.train().vec(_parms._response_column).isCategorical()) // only classification is supported now
      error("response_column", "Regression is not supported for Infogram. If you meant to do" +
              " classification, convert your response column to categorical/factor type before calling Infogram.");

    // make sure protected attributes are true predictor columns
    if (_parms._protected_columns != null) {
      Set<String> colNames = new HashSet<>(Arrays.asList(dataset.names()));
      for (String senAttribute : _parms._protected_columns)
        if (!colNames.contains(senAttribute))
          error("protected_columns", "protected_columns: "+senAttribute+" is not a valid " +
                  "column in the training dataset.");
    }

    _buildCore = _parms._protected_columns == null;
    
    if (_buildCore) {
      if (_parms._net_information_threshold == -1) { // not set
        _parms._cmi_threshold = 0.1;
        _parms._net_information_threshold = 0.1;
      } else if (_parms._net_information_threshold > 1 || _parms._net_information_threshold < 0) {
        error("net_information_threshold", " should be set to be between 0 and 1.");
      } else {
        _parms._cmi_threshold = _parms._net_information_threshold;
      }

      if (_parms._total_information_threshold == -1) {  // not set
        _parms._relevance_threshold = 0.1;
        _parms._total_information_threshold = 0.1;
      } else if (_parms._total_information_threshold < 0 || _parms._total_information_threshold > 1) {
        error("total_information_threshold", " should be set to be between 0 and 1.");
      } else {
        _parms._relevance_threshold = _parms._total_information_threshold;
      }
      
      if (_parms._safety_index_threshold != -1) {
        warn("safety_index_threshold", "Should not set safety_index_threshold for core infogram " +
                "runs.  Set net_information_threshold instead.  Using default of 0.1 if not set");
      }
      
      if (_parms._relevance_index_threshold != -1) {
        warn("relevance_index_threshold", "Should not set relevance_index_threshold for core " +
                "infogram runs.  Set total_information_threshold instead.  Using default of 0.1 if not set");
      }
    } else { // fair infogram
      if (_parms._safety_index_threshold == -1) {
        _parms._cmi_threshold = 0.1;
        _parms._safety_index_threshold = 0.1;
      } else if (_parms._safety_index_threshold < 0 || _parms._safety_index_threshold > 1) {
        error("safety_index_threshold", " should be set to be between 0 and 1.");
      } else {
        _parms._cmi_threshold = _parms._safety_index_threshold;
      }
      
      if (_parms._relevance_index_threshold == -1) {
        _parms._relevance_threshold = 0.1;
        _parms._relevance_index_threshold = 0.1;
      } else if (_parms._relevance_index_threshold < 0 || _parms._relevance_index_threshold > 1) {
        error("relevance_index_threshold", " should be set to be between 0 and 1.");
      } else {
        _parms._relevance_threshold = _parms._relevance_index_threshold;
      }
      
      if (_parms._net_information_threshold != -1) {
        warn("net_information_threshold", "Should not set net_information_threshold for fair " +
                "infogram runs, set safety_index_threshold instead.  Using default of 0.1 if not set");
      }
      if (_parms._total_information_threshold != -1) {
        warn("total_information_threshold", "Should not set total_information_threshold for fair" +
                " infogram runs, set relevance_index_threshold instead.  Using default of 0.1 if not set");
      }
      
      if (AUTO.equals(_parms._algorithm))
        _parms._algorithm = gbm;
    }

    // check top k to be between 0 and training dataset column number
    if (_parms._top_n_features < 0)
      error("_topk", "topk must be between 0 and the number of predictor columns in your training dataset.");

    _numPredictors = _parms.train().numCols()-1;
    if (_parms._weights_column != null)
      _numPredictors--;
    if (_parms._offset_column != null)
      _numPredictors--;
    if ( _parms._top_n_features > _numPredictors) {
      warn("top_n_features", "The top_n_features exceed the actual number of predictor columns in your training dataset." +
              "  It will be set to the number of predictors in your training dataset.");
      _parms._top_n_features = _numPredictors;
    }

    if (_parms._nparallelism < 0)
      error("nparallelism", "must be >= 0.  If 0, it is adaptive");

    if (_parms._nparallelism == 0) // adaptively set nparallelism
      _parms._nparallelism = H2O.NUMCPUS;
    
    if (_parms._compute_p_values)
      error("compute_p_values", " compute_p_values calculation is not yet implemented.");
    
    if (nclasses() < 2)
      error("distribution", " infogram currently only supports classification models");
    
    if (DistributionFamily.AUTO.equals(_parms._distribution)) {
      _parms._distribution = (nclasses() == 2) ? DistributionFamily.bernoulli : DistributionFamily.multinomial;
    }
    if (_cvDone) {  // disable cv now that we are in main model
      _nFoldOrig = _parms._nfolds;
      _foldColumnOrig = _parms._fold_column;
      _foldAssignmentOrig = _parms._fold_assignment;
      _parms._fold_column = null;
      _parms._nfolds = 0;
      _parms._fold_assignment = null;
    }
  }

  private class InfogramDriver extends Driver {
      void prepareModelTrainingFrame() {
        String[] eligiblePredictors = extractPredictors(_parms, _train, _foldColumnOrig);  // exclude senstive attributes if applicable
      _baseOrSensitiveFrame = extractTrainingFrame(_parms, _parms._protected_columns, 1, _parms.train().clone());
      _parms.extraModelSpecificParams(); // copy over model specific parameters to build infogram
      _topKPredictors = extractTopKPredictors(_parms, _parms.train(), eligiblePredictors); // extract topK predictors
      _numModels = 1 + _topKPredictors.length;
      _modelDescription = generateModelDescription(_topKPredictors, _parms._protected_columns);
    }

    @Override
    public void computeImpl() {
      init(true);
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(Infogram.this);
      _job.update(0, "Initializing model training");
      buildModel();
    }
    // todo:  add max_runtime_secs restrictions
    public final void buildModel() {
      try {
        boolean validPresent = _parms.valid() != null;
        prepareModelTrainingFrame(); // generate training frame with predictors and sensitive features (if specified)
        InfogramModel model = new hex.Infogram.InfogramModel(dest(), _parms, new InfogramModelOutput(Infogram.this));
        _model = model.delete_and_lock(_job);
        _model._output._start_time = System.currentTimeMillis();
        _cmiRaw = new double[_numModels];
        if (_parms.valid() != null)
          _cmiRawValid = new double[_numModels];
        buildInfoGramsNRelevance(validPresent); // calculate mean CMI
        _job.update(1, "finished building models for Infogram ...");
        _model._output.setDistribution(_parms._distribution);
        copyCMIRelevance(_model._output); // copy over cmi, relevance of all predictors to model._output
        _cmi = _model._output._cmi;
        
        if (validPresent)
          copyCMIRelevanceValid(_model._output); // copy over cmi, relevance of all predictors to model._output
        
        _cmiRelKey = setCMIRelFrame(validPresent);
        _model._output.extractAdmissibleFeatures(DKV.getGet(_cmiRelKey), false, false);
        if (validPresent) {
          _cmiRelKeyValid = _model._output._admissible_score_key_valid;
          _model._output.extractAdmissibleFeatures(DKV.getGet(_cmiRelKeyValid), true, false);
          _model._output._validNonZeroNumRows = _validNonZeroNumRows;
        }
        if (_cvDone) {                       // CV is enabled and now we are in main model
          _cmiRelKeyCV = setCMIRelFrameCV(); // generate relevance and CMI frame from cv runs
          _model._output._admissible_score_key_xval = _cmiRelKeyCV;
          _model._output.extractAdmissibleFeatures(DKV.getGet(_cmiRelKeyCV), false, true);
          _parms._nfolds = _nFoldOrig;
          _parms._fold_assignment = _foldAssignmentOrig;
          _parms._fold_column = _foldColumnOrig;
        }
        _job.update(1, "Infogram building completed...");
        _model.update(_job._key);
      } finally {
        Scope.track(_baseOrSensitiveFrame);
        final List<Key> keep = new ArrayList<>();
        if (_model != null) {
          keepFrameKeys(keep, _cmiRelKey);
          if (_cmiRelKeyValid != null)
            keepFrameKeys(keep, _cmiRelKeyValid);
          if (_cmiRelKeyCV != null)
            keepFrameKeys(keep, _cmiRelKeyCV);
          // final model update
          _model.update(_job._key);
          _model.unlock(_job);
        }
        Scope.untrack(keep.toArray(new Key[keep.size()]));
      }
    }
    
    /**
     * Copy over info to model._output for _cmi_raw, _cmi, _topKFeatures,
     * _all_predictor_names.  Derive _admissible for predictors if cmi >= cmi_threshold and 
     * relevance >= relevance_threshold.  Derive _admissible_index as distance from point with cmi = 1 and 
     * relevance = 1.  In addition, all arrays are sorted on _admissible_index.
     */
    private void copyCMIRelevance(InfogramModelOutput modelOutput) {
      modelOutput._cmi_raw = new double[_cmi.length];
      System.arraycopy(_cmiRaw, 0, modelOutput._cmi_raw, 0, modelOutput._cmi_raw.length);
      modelOutput._admissible_index = new double[_cmi.length];
      modelOutput._admissible = new double[_cmi.length];
      modelOutput._cmi = _cmi.clone();
      modelOutput._topKFeatures = _topKPredictors.clone();
      modelOutput._all_predictor_names = _topKPredictors.clone();
      int numRows = _varImp.getRowDim();
      String[] varRowHeaders = _varImp.getRowHeaders();
      List<String> relNames = new ArrayList<>(Arrays.asList(varRowHeaders));
      modelOutput._relevance = new double[numRows];
      copyGenerateAdmissibleIndex(numRows, relNames, modelOutput._cmi, modelOutput._cmi_raw, modelOutput._relevance,
              modelOutput._admissible_index, modelOutput._admissible, modelOutput._all_predictor_names);
    }

    public void copyCMIRelevanceValid(InfogramModelOutput modelOutput) {
      modelOutput._cmi_raw_valid = new double[_cmiValid.length];
      System.arraycopy(_cmiRawValid, 0, modelOutput._cmi_raw_valid, 0, modelOutput._cmi_raw_valid.length);
      modelOutput._admissible_index_valid = new double[_cmiValid.length];
      modelOutput._admissible_valid = new double[_cmiValid.length];
      modelOutput._cmi_valid = _cmiValid.clone();
      int numRows = _varImp.getRowDim();
      String[] varRowHeaders = _varImp.getRowHeaders();
      List<String> relNames = new ArrayList<>(Arrays.asList(varRowHeaders));
      modelOutput._all_predictor_names_valid = modelOutput._topKFeatures.clone();
      modelOutput._relevance_valid = new double[numRows];
      copyGenerateAdmissibleIndex(numRows, relNames, modelOutput._cmi_valid, modelOutput._cmi_raw_valid,
              modelOutput._relevance_valid, modelOutput._admissible_index_valid, modelOutput._admissible_valid, 
              modelOutput._all_predictor_names_valid);
    }

    public void copyGenerateAdmissibleIndex(int numRows, List<String> relNames, double[] cmi,
                                            double[] cmi_raw, double[] relevance, double[] admissible_index, 
                                            double[] admissible, String[] all_predictor_names) {
      for (int index = 0; index < numRows; index++) { // extract predictor with varimp >= threshold
        int newIndex = relNames.indexOf(all_predictor_names[index]);
        relevance[index] = (double) _varImp.get(newIndex, 1);
        double temp1 = relevance[index];
        double temp2 = cmi[index];
        admissible_index[index] =  NORMALIZE_ADMISSIBLE_INDEX*Math.sqrt(temp1*temp1+temp2*temp2);
        admissible[index] = (relevance[index] >= _parms._relevance_threshold && cmi[index] >= _parms._cmi_threshold) ? 1 : 0;
      }
      int[] indices = IntStream.range(0, cmi.length).toArray();
      sort(indices, admissible_index, -1, -1);
      sortCMIRel(indices, relevance, cmi_raw, cmi, all_predictor_names, admissible_index, admissible);
    }

    private Key<Frame> setCMIRelFrame(boolean validPresent) {
      Frame cmiRelFrame = generateCMIRelevance(_model._output._all_predictor_names, _model._output._admissible, 
              _model._output._admissible_index, _model._output._relevance, _model._output._cmi, 
              _model._output._cmi_raw, _buildCore);
      _model._output._admissible_score_key = cmiRelFrame._key;
      if (validPresent) {  // generate relevanceCMI frame for validation dataset
        Frame cmiRelFrameValid = generateCMIRelevance(_model._output._all_predictor_names_valid, 
                _model._output._admissible_valid, _model._output._admissible_index_valid, 
                _model._output._relevance_valid, _model._output._cmi_valid, _model._output._cmi_raw_valid, _buildCore);
        _model._output._admissible_score_key_valid = cmiRelFrameValid._key;
      }
      return cmiRelFrame._key;
    }
    
    private void cleanUpCV() {
      String[] mainModelPredNames = _model._output._all_predictor_names;
      List<String> cvNames = new ArrayList<>(Arrays.asList(_columnsCV));
      int nPred = mainModelPredNames.length;
      String[] newCVNames = new String[nPred];
      double[] cmiCV = new double[nPred];
      double[] cmiRawCV = new double[nPred];
      for (int index=0; index < nPred; index++) {
        String mainPredNames = mainModelPredNames[index];
        int cvIndex = cvNames.indexOf(mainPredNames);
        if (cvIndex >= 0) {
          newCVNames[index] = mainPredNames;
          cmiCV[index] = _cmiCV[cvIndex];
          cmiRawCV[index] = _cmiRawCV[cvIndex];
        }
      }
      _columnsCV = newCVNames.clone();
      _cmiCV = cmiCV.clone();
      _cmiRawCV = cmiRawCV.clone();
    }
    
    private Key<Frame> setCMIRelFrameCV() {
      String[] mainModelPredNames = _model._output._all_predictor_names;
      double[] mainModelRelevance = _model._output._relevance;
      double[] relevanceCV = new double[mainModelRelevance.length];
      int nPred = mainModelPredNames.length;
      double[] admissibleIndex = new double[nPred];
      double[] admissible = new double[nPred];
      
      cleanUpCV();

      // generate admissible, admissibleIndex referring to cvNames
      for (int index=0; index<nPred; index++) {
          relevanceCV[index] = mainModelRelevance[index];
          double temp1 = 1 - relevanceCV[index];
          double temp2 = 1 - _cmiCV[index];
          admissibleIndex[index] = Math.sqrt(temp1 * temp1 + temp2 * temp2)*NORMALIZE_ADMISSIBLE_INDEX;
          admissible[index] = _cmiCV[index] >= _parms._cmi_threshold && relevanceCV[index] >= _parms._relevance_threshold
                  ? 1 : 0;
      }
      int[] indices = IntStream.range(0, relevanceCV.length).toArray();
      _columnsCV = mainModelPredNames.clone();
      sort(indices, admissibleIndex, -1, -1);
      sortCMIRel(indices, relevanceCV, _cmiRawCV, _cmiCV, _columnsCV, admissibleIndex, admissible);
      Frame cmiRelFrame = generateCMIRelevance(_columnsCV, admissible, admissibleIndex, relevanceCV, _cmiCV,
              _cmiRawCV, _buildCore);
      return cmiRelFrame._key;
    }
    
    /***
     * Top level method to break down the infogram process into parts.
     *
     * I have a question here for all of you:  Instead of generating the training frame and model builders for all
     * the predictors, I break this down into several parts with each part generating _parms._nparallelism training
     * frames and model builders.  For each part, after _parms._nparallelism models are built, I extract the entropy
     * for each predictor.  Then, I move to the next part.  My question here is:  is this necessary?  I am afraid of
     * the memory consumption of spinning up so many training frames and model builders.  If this is not an issue,
     * please let me know.
     * 
     * @param validPresent true if there is a validation dataset
     */
    private void buildInfoGramsNRelevance(boolean validPresent) {
      int outerLoop = (int) Math.floor(_numModels/_parms._nparallelism); // last model is build special
      int modelCount = 0;
      int lastModelInd = _numModels - 1;
      if (outerLoop > 0) {  // build parallel models but limit it to parms._nparallelism at a time
        for (int outerInd = 0; outerInd < outerLoop; outerInd++) {
          buildModelCMINRelevance(modelCount, _parms._nparallelism, lastModelInd);
          modelCount += _parms._nparallelism;
          _job.update(_parms._nparallelism, "in the middle of building infogram models.");
        }
      }
      int leftOver = _numModels - modelCount;
      if (leftOver > 0) { // finish building the leftover models
        buildModelCMINRelevance(modelCount, leftOver, lastModelInd);
        _job.update(leftOver, " building the final set of infogram models.");
      }
      _cmi = calculateFinalCMI(_cmiRaw, _buildCore);  // scale cmi to be from 0 to 1, ignore last one
      if (validPresent)
        _cmiValid = calculateFinalCMI(_cmiRawValid, _buildCore);
    }

    /***
     * This method basically go through all the predictors and calculate the cmi associated with each predictor.  For
     * core infogram, refer to https://github.com/h2oai/h2o-3/issues/7830 section I.  For fair infogram, refer to
     * https://github.com/h2oai/h2o-3/issues/7830 section II.
     * 
     * @param modelCount : current model count to build
     * @param numModel : total number of models to build
     * @param lastModelInd : index of last model to build
     */
    private void buildModelCMINRelevance(int modelCount, int numModel, int lastModelInd) {
      boolean lastModelIndcluded = (modelCount+numModel >= lastModelInd);
      Frame[] trainingFrames = buildTrainingFrames(modelCount, numModel, lastModelInd); // generate training frame
      Model.Parameters[] modelParams = buildModelParameters(trainingFrames, _parms._infogram_algorithm_parameters,
              numModel, _parms._algorithm); // generate parameters
      ModelBuilder[] builders = ModelBuilderHelper.trainModelsParallel(buildModelBuilders(modelParams),
              numModel);      // build models in parallel
      if (lastModelIndcluded) // extract relevance here for core infogram
        extractRelevance(builders[numModel-1].get(), modelParams[numModel-1]);
      _validNonZeroNumRows = generateInfoGrams(builders, trainingFrames, modelCount, numModel); // generate infogram
    }
    
  /**
   * For core infogram, training frames are built by omitting the predictor of interest.  For fair infogram, 
   * training frames are built with protected columns plus the predictor of interest.  The very last training frame
   * for core infogram will contain all predictors.  For fair infogram, the very last training frame contains only the
   * protected columns
   *
   * @param startInd : starting index of Frame[] to build
   * @param numFrames : number of frames to build
   * @param lastModelInd : index of last frame to build
   * @return
   */
    private Frame[] buildTrainingFrames(int startInd, int numFrames,
                                        int lastModelInd) {
      Frame[] trainingFrames = new Frame[numFrames];
      Frame trainingFrame = _parms.train();
      int finalFrameInd = startInd + numFrames;
      int frameCount = 0;
      for (int frameInd = startInd; frameInd < finalFrameInd; frameInd++) {
        trainingFrames[frameCount] = new Frame(_baseOrSensitiveFrame);
        if (_buildCore) {
          for (int vecInd = 0; vecInd < _topKPredictors.length; vecInd++) {
            if ((frameInd < lastModelInd) && (vecInd != frameInd)) // skip ith vector except last model
              trainingFrames[frameCount].add(_topKPredictors[vecInd], trainingFrame.vec(_topKPredictors[vecInd]));
            else if (frameInd == lastModelInd)// add all predictors
              trainingFrames[frameCount].add(_topKPredictors[vecInd], trainingFrame.vec(_topKPredictors[vecInd]));
          }
        } else {
          if (frameInd < lastModelInd) // add ith predictor
            trainingFrames[frameCount].prepend(_topKPredictors[frameInd], trainingFrame.vec(_topKPredictors[frameInd]));
        }
        Scope.track(trainingFrames[frameCount]);
       // frameKeys.add(trainingFrames[frameCount]._key);
        //_generatedFrameKeys.add(trainingFrames[frameCount]._key);
        DKV.put(trainingFrames[frameCount++]);
      }
      return trainingFrames;
    }

  /***
   * Calculate the CMI for each predictor.  Refer to https://github.com/h2oai/h2o-3/issues/7830 section I step 2 
   * for core infogram, or section II step 3 for fair infogram 
   *
   */
  private long generateInfoGrams(ModelBuilder[] builders, Frame[] trainingFrames, int startIndex, int numModels) {
    long nonZeroRows = Long.MAX_VALUE;
    for (int index = 0; index < numModels; index++) {
      Model oneModel = builders[index].get();  // extract model
      int nclasses = oneModel._output.nclasses();
      Frame prediction = oneModel.score(trainingFrames[index]); // generate prediction, cmi on training frame
      prediction.add(_parms._response_column, trainingFrames[index].vec(_parms._response_column));
      Scope.track_generic(oneModel);
      if (oneModel._parms._weights_column != null && Arrays.asList(trainingFrames[index].names()).contains(oneModel._parms._weights_column))
        prediction.add(oneModel._parms._weights_column, trainingFrames[index].vec(oneModel._parms._weights_column));
      Scope.track(prediction);
      _cmiRaw[index+startIndex] = new hex.Infogram.EstimateCMI(prediction, nclasses, oneModel._parms._response_column).doAll(prediction)._meanCMI; // calculate raw CMI
      if (_parms.valid() != null) { // generate prediction, cmi on validation frame
        Frame validFrame = _parms.valid();
        Frame predictionValid = oneModel.score(validFrame);  // already contains the response
        predictionValid.add(_parms._response_column, validFrame.vec(_parms._response_column));
        if (oneModel._parms._weights_column != null) { // weight column names are changed if cross-validation is on
          if (Arrays.asList(validFrame.names()).contains("__internal_cv_weights__"))
            predictionValid.add(oneModel._parms._weights_column, validFrame.vec("__internal_cv_weights__"));
          else
            predictionValid.add(oneModel._parms._weights_column, Arrays.asList(validFrame.names()).contains(oneModel._parms._weights_column)?
                    validFrame.vec(oneModel._parms._weights_column):
                    validFrame.anyVec().makeCon(1));
        }
        Scope.track(predictionValid);
        EstimateCMI calCMI = new hex.Infogram.EstimateCMI(predictionValid, nclasses, oneModel._parms._response_column).doAll(predictionValid);
        _cmiRawValid[index + startIndex] = calCMI._meanCMI;
        nonZeroRows = Math.min(nonZeroRows, calCMI._nonZeroRows);
      }
    }
    return nonZeroRows;
  }
  
    /**
     * For core infogram, the last model is the one with all predictors.  In this case, the relevance is basically the
     * variable importance.  For fair infogram, the last model is the one with all the predictors minus the protected
     * columns.  Again, the relevance is the variable importance.
     */
    private void extractRelevance(Model model, Model.Parameters parms) {
      if (_buildCore) {           // full model is last one, just extract varImp
        _varImp = model._output.getVariableImportances();
      } else {                    // need to build model for fair info grame
        Frame fullFrame = subtractAdd2Frame(_baseOrSensitiveFrame, _parms.train(), _parms._protected_columns,
                _topKPredictors); // training frame is topKPredictors minus protected_columns
        parms._train = fullFrame._key;
        Scope.track(fullFrame);
        ModelBuilder builder = ModelBuilder.make(parms);
        Model fairModel = (Model) builder.trainModel().get();
        _varImp = fairModel._output.getVariableImportances();
        Scope.track_generic(fairModel);
      }
    }
  }
}
