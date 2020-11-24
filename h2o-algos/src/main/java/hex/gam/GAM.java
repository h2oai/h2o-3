package hex.gam;

import hex.DataInfo;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.gam.GAMModel.GAMParameters;
import hex.gam.GamSplines.ThinPlateDistanceWithKnots;
import hex.gam.GamSplines.ThinPlatePolynomialWithKnots;
import hex.gam.MatrixFrameUtils.GamUtils;
import hex.gam.MatrixFrameUtils.GenerateGamMatrixOneColumn;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMParameters;
import hex.gram.Gram;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.IcedHashSet;
import water.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static hex.gam.GAMModel.adaptValidFrame;
import static hex.gam.GamSplines.ThinPlateRegressionUtils.*;
import static hex.gam.MatrixFrameUtils.GAMModelUtils.copyGLMCoeffs;
import static hex.gam.MatrixFrameUtils.GAMModelUtils.copyGLMtoGAMModel;
import static hex.gam.MatrixFrameUtils.GamUtils.*;
import static hex.gam.MatrixFrameUtils.GamUtils.AllocateType.*;
import static hex.gam.MatrixFrameUtils.GenerateGamMatrixOneColumn.generateZTransp;
import static hex.genmodel.utils.ArrayUtils.flat;
import static hex.glm.GLMModel.GLMParameters.Family.multinomial;
import static hex.glm.GLMModel.GLMParameters.Family.ordinal;
import static hex.glm.GLMModel.GLMParameters.GLMType.gam;
import static hex.util.LinearAlgebraUtils.generateOrthogonalComplement;
import static hex.util.LinearAlgebraUtils.generateQR;
import static water.util.ArrayUtils.expandArray;
import static water.util.ArrayUtils.subtract;


public class GAM extends ModelBuilder<GAMModel, GAMModel.GAMParameters, GAMModel.GAMModelOutput> {
  private double[][][] _knots; // Knots for splines
  private double[] _cv_alpha = null;  // best alpha value found from cross-validation
  private double[] _cv_lambda = null; // bset lambda value found from cross-validation
  private int _thinPlateSmoothersWithKnotsNum = 0;
  private int _cubicSplineNum = 0;
  double[][] _gamColMeansRaw; // store raw gam column means in gam_column_sorted order and only for thin plate smoothers
  public double[][] _oneOGamColStd;
  public double[] _penaltyScale;
  private boolean _cvOn = false;
  private Frame _origTrain = null;
  
  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{ModelCategory.Regression};
  }

  @Override
  public boolean isSupervised() {
    return true;
  }

  @Override
  public BuilderVisibility builderVisibility() {
    return BuilderVisibility.Experimental;
  }

  @Override
  public boolean havePojo() {
    return false;
  }

  @Override
  public boolean haveMojo() {
    return true;
  }

  public GAM(boolean startup_once) {
    super(new GAMModel.GAMParameters(), startup_once);
  }

  public GAM(GAMModel.GAMParameters parms) {
    super(parms);
    init(false);
  }

  public GAM(GAMModel.GAMParameters parms, Key<GAMModel> key) {
    super(parms, key);
    init(false);
  }

  // cross validation can be used to choose the best alpha/lambda values among a whole collection of alpha
  // and lambda values.  Future hyperparameters can be added for cross-validation to choose as well.
  @Override
  public void computeCrossValidation() {
    _cvOn = true;
    _origTrain = _parms.train();  // store original training frame before it is changed
    validateGamParameters();  // perform GAM initialization once here and reduce operations performed during the folds
    if (error_count() > 0) {
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GAM.this);
    }
    super.computeCrossValidation();
  }

  // find the best alpha/lambda values used to build the main model moving forward by looking at the devianceValid
  @Override
  public void cv_computeAndSetOptimalParameters(ModelBuilder[] cvModelBuilders) {
    double deviance_valid = Double.POSITIVE_INFINITY;
    double best_alpha = 0;
    double best_lambda = 0;
    for (int i = 0; i < cvModelBuilders.length; ++i) {  // run cv for each lambda value
      GAMModel g = (GAMModel) cvModelBuilders[i].dest().get();
      if (g._output._devianceValid < deviance_valid) {
        best_alpha= g._output._best_alpha;
        best_lambda = g._output._best_lambda;
      }
    }
    _cv_alpha = new double[]{best_alpha};
    _cv_lambda = new double[]{best_lambda};
  }
  
  /***
   * This method will look at the keys of knots stored in _parms._knot_ids and copy them over to double[][][]
   * array.  Note that we have smoothers that take different number of columns.  We will keep the gam columns
   * of single predictor smoothers to the front and multiple predictor smoothers to the back of the array.  For
   * smoothers that take more than one predictor column, knot location is determined by first sorting the first 
   * gam_column and then extract the quantiles of that sorted gam_columns.  Here, instead of taking the value for
   * one gam column, we take the whole row with all the predictors for that smoother.
   *
   * @return double[][][] array containing the knots specified by users
   */
  public double[][][] generateKnotsFromKeys() { // todo: parallize this operation
    int numGamCols = _parms._gam_columns.length; // total number of predictors in all smoothers
    double[][][] knots = new double[numGamCols][][]; // 1st index into gam column, 2nd index number of knots for the row
    boolean allNull = _parms._knot_ids == null;
    int csInd = 0;
    int tpInd = _cubicSplineNum;
    int gamIndex; // index into the sorted arrays with CS front, TP back.
    for (int outIndex = 0; outIndex < _parms._gam_columns.length; outIndex++) { // go through each gam_column group
      String tempKey = allNull ? null : _parms._knot_ids[outIndex]; // one knot_id for each smoother
      if (_parms._bs[outIndex] == 1) // thin plate regression
        gamIndex = tpInd++;
      else
        gamIndex = csInd++;
      knots[gamIndex] = new double[_parms._gam_columns[outIndex].length][];
      if (tempKey != null && (tempKey.length() > 0)) {  // read knots location from Frame given by user      
        final Frame knotFrame = Scope.track((Frame) DKV.getGet(Key.make(tempKey)));
        double[][] knotContent = new double[(int) knotFrame.numRows()][_parms._gam_columns[outIndex].length];
        final ArrayUtils.FrameToArray f2a = new ArrayUtils.FrameToArray(0,
                _parms._gam_columns[outIndex].length - 1, knotFrame.numRows(), knotContent);
        knotContent = f2a.doAll(knotFrame).getArray();  // first index is row, second index is column

        final double[][] knotCTranspose = ArrayUtils.transpose(knotContent);// change knots to correct order
        for (int innerIndex = 0; innerIndex < knotCTranspose.length; innerIndex++) {
          knots[gamIndex][innerIndex] = new double[knotContent.length];
          System.arraycopy(knotCTranspose[innerIndex], 0, knots[gamIndex][innerIndex], 0,
                  knots[gamIndex][innerIndex].length);
          if (knotCTranspose.length == 1 && _parms._bs[outIndex] == 0) // only check for order to single smoothers
            failVerifyKnots(knots[gamIndex][innerIndex], outIndex);
        }
        _parms._num_knots[outIndex] = knotContent.length;

      } else {  // current column knot key is null, we will use default method to generate knots
        final Frame predictVec = new Frame(_parms._gam_columns[outIndex], 
                _parms.train().vecs(_parms._gam_columns[outIndex]));
        if (_parms._bs[outIndex] == 0) {
          knots[gamIndex][0] = generateKnotsOneColumn(predictVec, _parms._num_knots[outIndex]);
          failVerifyKnots(knots[gamIndex][0], outIndex);
        } else {  // generate knots for multi-predictor smoothers
          knots[gamIndex] = genKnotsMultiplePreds(predictVec, _parms, outIndex);
          failVerifyKnots(knots[gamIndex][0], outIndex);
        }
      }
    }
    return knots;
  }
  
  // this function will check and make sure the knots location specified in knots are valid in the following sense:
  // 1. They do not contain NaN
  // 2. They are sorted in ascending order.
  public void failVerifyKnots(double[] knots, int gam_column_index) {
    for (int index = 0; index < knots.length; index++) {
      if (Double.isNaN(knots[index])) {
        error("gam_columns/knots_id", String.format("Knots generated by default or specified in knots_id " +
                        "ended up containing a NaN value for gam_column %s.   Please specify alternate knots_id" +
                        " or choose other columns.", _parms._gam_columns[gam_column_index][0]));
        return;
      }
      if (index > 0 && knots[index - 1] > knots[index]) {
        error("knots_id", String.format("knots not sorted in ascending order for gam_column %s. " +
                        "Knots at index %d: %f.  Knots at index %d: %f",_parms._gam_columns[gam_column_index][0], index-1, 
                knots[index-1], index, knots[index]));
        return;
      }
      if (index > 0 && knots[index - 1] == knots[index]) {
        error("gam_columns/knots_id", String.format("chosen gam_column %s does have not enough values to " +
                        "generate well-defined knots. Please choose other columns or reduce " +
                        "the number of knots.  If knots are specified in knots_id, choose alternate knots_id as the" +
                        " knots are not in ascending order.  Knots at index %d: %f.  Knots at index %d: %f", 
                _parms._gam_columns[gam_column_index][0], index-1, knots[index-1], index, knots[index]));
        return;
      }
    }
  }
  
  @Override
  public void init(boolean expensive) {
    super.init(expensive);
    if (expensive && (_knots == null))  // add GAM specific check here, only do it once especially during CV
      validateGamParameters();
  }
  
  private void validateGamParameters() {
    if (_parms._max_iterations == 0)
      error("_max_iterations", H2O.technote(2, "if specified, must be >= 1."));
    if (_parms._family == GLMParameters.Family.AUTO) {
      if (nclasses() == 1 & _parms._link != GLMParameters.Link.family_default && _parms._link != GLMParameters.Link.identity
              && _parms._link != GLMParameters.Link.log && _parms._link != GLMParameters.Link.inverse && _parms._link != null) {
        error("_family", H2O.technote(2, "AUTO for undelying response requires the link to be family_default, identity, log or inverse."));
      } else if (nclasses() == 2 & _parms._link != GLMParameters.Link.family_default && _parms._link != GLMParameters.Link.logit
              && _parms._link != null) {
        error("_family", H2O.technote(2, "AUTO for undelying response requires the link to be family_default or logit."));
      } else if (nclasses() > 2 & _parms._link != GLMParameters.Link.family_default & _parms._link != GLMParameters.Link.multinomial
              && _parms._link != null) {
        error("_family", H2O.technote(2, "AUTO for undelying response requires the link to be family_default or multinomial."));
      }
    }
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GAM.this);
    if (_parms._gam_columns == null) { // check _gam_columns contains valid columns
      error("_gam_columns", "must specify columns names to apply GAM to.  If you don't have any," +
              " use GLM.");
    } else { // check and make sure gam_columns column types are legal
      if (_parms._bs == null)
        setDefaultBSType(_parms);
      if ((_parms._bs != null) && (_parms._gam_columns.length != _parms._bs.length))  // check length
        error("gam colum number", "Number of gam columns implied from _bs and _gam_columns do not " +
                "match.");
      assertLegalGamColumnsNBSTypes();  // number of CS and TP smoothers determined.
    }
    if (_parms._scale == null)
      setDefaultScale(_parms);
    setGamPredSize(_parms, _cubicSplineNum);
    if (_thinPlateSmoothersWithKnotsNum > 0)
      setThinPlateParameters(_parms, _thinPlateSmoothersWithKnotsNum); // set the m, M for thin plate regression smoothers
    checkOrChooseNumKnots(); // check valid num_knot assignment or choose num_knots
    for (int index = 0; index < _parms._gam_columns.length; index++) {
      Frame dataset = _parms.train();
      String cname = _parms._gam_columns[index][0]; // only check the first gam_column
      if (dataset.vec(cname).isInt() && ((dataset.vec(cname).max() - dataset.vec(cname).min() + 1) < _parms._num_knots[index]))
        error("gam_columns", "column " + cname + " has cardinality lower than the number of knots and cannot be used as a gam" +
                " column.");
    }
    if ((_parms._num_knots.length != _parms._gam_columns.length))
      error("gam colum number", "Number of gam columns implied from _num_knots and _gam_columns do" +
              " not match.");
    if (_parms._knot_ids != null) { // check knots location specification
      if (_parms._knot_ids.length != _parms._gam_columns.length)
        error("gam colum number", "Number of gam columns implied from _num_knots and _knot_ids do" +
                " not match.");
    }
    _knots = generateKnotsFromKeys(); // generate knots and verify that they are given correctly
    sortGAMParameters(_parms, _cubicSplineNum, _thinPlateSmoothersWithKnotsNum); // move cubic spline to the front and thin plate to the back
    checkThinPlateParams();
    if (_parms._saveZMatrix && ((_train.numCols() - 1 + _parms._num_knots.length) < 2))
      error("_saveZMatrix", "can only be enabled if the number of predictors plus" +
              " Gam columns in gam_columns exceeds 2");
    if ((_parms._lambda_search || !_parms._intercept || _parms._lambda == null || _parms._lambda[0] > 0))
      _parms._use_all_factor_levels = true;
    if (_parms._link == null) {
      _parms._link = GLMParameters.Link.family_default;
    }
    if (_parms._family == GLMParameters.Family.AUTO) {
      if (_nclass == 1) {
        _parms._family = GLMParameters.Family.gaussian;
      } else if (_nclass == 2) {
        _parms._family = GLMParameters.Family.binomial;
      } else {
        _parms._family = GLMParameters.Family.multinomial;
      }
    }
    if (_parms._link == null || _parms._link.equals(GLMParameters.Link.family_default))
      _parms._link = _parms._family.defaultLink;
    
    if ((_parms._family == GLMParameters.Family.multinomial || _parms._family == GLMParameters.Family.ordinal ||
            _parms._family == GLMParameters.Family.binomial)
            && response().get_type() != Vec.T_CAT) {
      error("_response_column", String.format("For given response family '%s', please provide a categorical" +
              " response column. Current response column type is '%s'.", _parms._family, response().get_type_str()));
    }
  }

  /**
   *   verify and check thin plate regression smoothers specific parameters
   **/
  public void checkThinPlateParams() {
    if (_thinPlateSmoothersWithKnotsNum ==0)
      return;
    
    _parms._num_knots_tp = new int[_thinPlateSmoothersWithKnotsNum];
    System.arraycopy(_parms._num_knots_sorted, _cubicSplineNum, _parms._num_knots_tp, 0,
            _thinPlateSmoothersWithKnotsNum);
    int tpIndex = 0;
    for (int index = 0; index < _parms._gam_columns.length; index++) {
      if (_parms._bs_sorted[index] == 1) {
        if (_parms._num_knots_sorted[index] < _parms._M[tpIndex] + 1) {
          error("num_knots", "num_knots for gam column start with  " + _parms._gam_columns_sorted[index][0] +
                  " did not specify enough num_knots.  It should be equal or greater than " + (_parms._M[tpIndex] + 1) + ".");
        }
        tpIndex++;
      }
    }
  }
  
  // set default num_knots to 10 for gam_columns where there is no knot_id specified for CS smoothers
  // for TP smoothers, default is set to be max of 10 or _M+2.
  public void checkOrChooseNumKnots() {
    if (_parms._num_knots == null)
      _parms._num_knots = new int[_parms._gam_columns.length];  // different columns may have different
    int tpCount = 0;
    for (int index = 0; index < _parms._num_knots.length; index++) {  // set zero value _num_knots
      if (_parms._knot_ids == null || (_parms._knot_ids != null && _parms._knot_ids[index] == null)) {  // knots are not specified
        int numKnots = _parms._num_knots[index];
        int naSum = 0;
        for (int innerIndex = 0; innerIndex < _parms._gam_columns[index].length; innerIndex++) {
          naSum += _parms.train().vec(_parms._gam_columns[index][innerIndex]).naCnt();
        }
        long eligibleRows = _train.numRows()-naSum;
        if (_parms._num_knots[index] == 0) {  // set num_knots to default
          int defaultRows = 10;
          if (_parms._bs[index] == 1) {
            defaultRows = Math.max(defaultRows, _parms._M[tpCount] + 2);
            tpCount++;
          }
          _parms._num_knots[index] = eligibleRows < defaultRows ? (int) eligibleRows : defaultRows;
        } else {  // num_knots assigned by user and check to make sure it is legal
          if (numKnots > eligibleRows) {
            error("_num_knots", " number of knots specified in _num_knots: "+numKnots+" for smoother" +
                    " with first predictor "+_parms._gam_columns[index][0]+".  Reduce _num_knots.");
          }
        }
      }
    }
  }
  
  // Check and make sure correct BS type is assigned to the various gam_columns specified.  In addition, the number
  // of CS and TP smoothers are counted here as well.
  public void assertLegalGamColumnsNBSTypes() {
    Frame dataset = _parms.train();
    List<String> cNames = Arrays.asList(dataset.names());
    for (int index = 0; index < _parms._gam_columns.length; index++) {
      if (_parms._bs != null) { // check and make sure the correct bs type is chosen
        if (_parms._gam_columns[index].length > 1 && _parms._bs[index] != 1)
          error("bs", "Smother with multiple predictors can only use bs = 1");
        if (_parms._bs[index] == 1)
          _thinPlateSmoothersWithKnotsNum++; // record number of thin plate
        if (_parms._bs[index] == 0)
          _cubicSplineNum++;

        for (int innerIndex = 0; innerIndex < _parms._gam_columns[index].length; innerIndex++) {
          String cname = _parms._gam_columns[index][innerIndex];
          if (!cNames.contains(cname))
            error("gam_columns", "column name: " + cname + " does not exist in your dataset.");
          if (dataset.vec(cname).isCategorical())
            error("gam_columns", "column " + cname + " is categorical and cannot be used as a gam " +
                    "column.");
          if (dataset.vec(cname).isBad() || dataset.vec(cname).isTime() || dataset.vec(cname).isUUID() ||
                  dataset.vec(cname).isConst())
            error("gam_columns", String.format("Column '%s' of type '%s' cannot be used as GAM column. Column types " +
                    "BAD, TIME, CONSTANT and UUID cannot be used.", cname, dataset.vec(cname).get_type_str()));
          if (!dataset.vec(cname).isNumeric())
            error("gam_columns", "column " + cname + " is not numerical and cannot be used as a gam" +
                    " column.");
        }
      }
    }
  }

  @Override
  protected boolean computePriorClassDistribution() {
    return (_parms._family== multinomial)||(_parms._family== ordinal);
  }

  @Override
  protected GAMDriver trainModelImpl() {
    return new GAMDriver();
  }

  @Override
  protected int nModelsInParallel(int folds) {
    return nModelsInParallel(folds,2);
  }

  private class GAMDriver extends Driver {
    double[][][] _zTranspose;         // store transpose(Z) matrices for CS and TP smoothers
    double[][][] _zTransposeCS;       // store transpose(zCS) for thin plate smoother to remove optimization constraint
    double[][][] _penaltyMatCenter; // store centered penalty matrices of all smoothers
    double[][][] _penaltyMat;        // penalty matrix before any kind of processing
    double[][][] _penaltyMatCS;     // penalty matrix after removing optimization constraint, only for thin plate
    double[][][] _starT;              // store T* as in 3.2.3
    public double[][][] _binvD;       // store BinvD for each CS smoother specified for scoring
    public int[] _numKnots;           // store number of knots per smoother
    String[][] _gamColNames;          // store column names of all smoothers before any processing
    String[][] _gamColNamesCenter;    // gamColNames after centering is performed.
    Key<Frame>[] _gamFrameKeys;
    Key<Frame>[] _gamFrameKeysCenter;
    double[][] _gamColMeans;          // store gam column means without centering.
    int[][][] _allPolyBasisList;      // store polynomial basis function for all TP smoothers
    /***
     * This method will take the _train that contains the predictor columns and response columns only and add to it
     * the following:
     * 1. For each smoother included in gam_columns, expand it out to calculate the f(x) and attach to the frame.
     * 2. For TP smoothers, it will calculate the zCS transpose
     * 3. It will calculate the ztranspose that is used to center each smoother.
     * 4. It will calculate a penalty matrix used to control the smoothness of GAM.
     *
     * @return
     */
    Frame adaptTrain() {
      int numGamFrame = _parms._gam_columns.length;
      _zTranspose = GamUtils.allocate3DArray(numGamFrame, _parms, firstOneLess);  // for centering for all smoothers 
      _penaltyMat = _parms._savePenaltyMat?GamUtils.allocate3DArray(numGamFrame, _parms, sameOrig):null;
      _penaltyMatCenter = GamUtils.allocate3DArray(numGamFrame, _parms, bothOneLess);
      if (_cubicSplineNum > 0)
        _binvD = GamUtils.allocate3DArrayCS(_cubicSplineNum, _parms, firstTwoLess);
      _numKnots = MemoryManager.malloc4(numGamFrame);
      _gamColNames = new String[numGamFrame][];
      _gamColNamesCenter = new String[numGamFrame][];
      _gamFrameKeys = new Key[numGamFrame];
      _gamFrameKeysCenter = new Key[numGamFrame];
      _gamColMeans = new double[numGamFrame][];   // means of gamified columns
      _penaltyScale = new double[numGamFrame];
      if (_thinPlateSmoothersWithKnotsNum > 0) {  // only allocate if there are thin plate smoothers
        int[] kMinusM = subtract(_parms._num_knots_tp, _parms._M);
        _zTransposeCS = GamUtils.allocate3DArrayTP(_thinPlateSmoothersWithKnotsNum, _parms, kMinusM, _parms._num_knots_tp);
        _penaltyMatCS = GamUtils.allocate3DArrayTP(_thinPlateSmoothersWithKnotsNum, _parms, kMinusM, kMinusM);
        _allPolyBasisList = new int[_thinPlateSmoothersWithKnotsNum][][];
        _gamColMeansRaw = new double[_thinPlateSmoothersWithKnotsNum][];
        _oneOGamColStd = new double[_thinPlateSmoothersWithKnotsNum][];
        if (_parms._savePenaltyMat)
          _starT = GamUtils.allocate3DArrayTP(_thinPlateSmoothersWithKnotsNum, _parms, _parms._num_knots_tp, _parms._M);
      }
      addGAM2Train();  // add GAM columns to training frame
      return buildGamFrame(_parms, _train, _gamFrameKeysCenter); // add gam cols to _train
    }
    
    // This class generate the thin plate regression smoothers as denoted in GamThinPlateRegressionH2O.pdf
    public class ThinPlateRegressionSmootherWithKnots extends RecursiveAction {
      final Frame _predictVec;
      final int _numKnots;
      final int _numKnotsM1;
      final int _numKnotsMM;  // store k-M
      final int _splineType;
      final boolean _savePenaltyMat;
      final double[][] _knots;
      final GAMParameters _parms;
      final int _gamColIndex;
      final int _thinPlateGamColIndex;
      final int _numPred;     // number of predictors (d)
      final int _M;
      
      public ThinPlateRegressionSmootherWithKnots(Frame predV, GAMParameters parms, int gamColIndex, double[][] knots,
                                                  int thinPlateInd) {
        _predictVec  = predV;
        _knots = knots;
        _numKnots = parms._num_knots_sorted[gamColIndex];
        _numKnotsM1 = _numKnots-1;
        _parms = parms;
        _splineType = _parms._bs_sorted[gamColIndex];
        _gamColIndex = gamColIndex;
        _thinPlateGamColIndex = thinPlateInd;
        _savePenaltyMat = _parms._savePenaltyMat;
        _numPred = parms._gam_columns_sorted[gamColIndex].length;
        _M = _parms._M[_thinPlateGamColIndex];
        _numKnotsMM = _numKnots-_M;
      }

      @Override
      protected void compute() {
        double[] rawColMeans = new double[_numPred];        
        double[] oneOverColStd = new double[_numPred];
        for (int colInd = 0; colInd < _numPred; colInd++) {
          rawColMeans[colInd] = _predictVec.vec(colInd).mean();
          oneOverColStd[colInd] = 1.0/_predictVec.vec(colInd).sigma();  // std
        }
        System.arraycopy(rawColMeans, 0, _gamColMeansRaw[_thinPlateGamColIndex], 0, rawColMeans.length);
        System.arraycopy(oneOverColStd, 0, _oneOGamColStd[_thinPlateGamColIndex], 0, oneOverColStd.length);
        ThinPlateDistanceWithKnots distanceMeasure = 
                new ThinPlateDistanceWithKnots(_knots, _numPred, oneOverColStd, 
                        _parms._standardize_tp_gam_cols).doAll(_numKnots, Vec.T_NUM, _predictVec); // Xnmd in 3.1
        List<Integer[]> polyBasisDegree = findPolyBasis(_numPred, calculatem(_numPred));// polynomial basis lists in 3.2
        int[][] polyBasisArray = convertList2Array(polyBasisDegree, _M, _numPred);
        copy2DArray(polyBasisArray, _allPolyBasisList[_thinPlateGamColIndex]);
        String colNameStub = genThinPlateNameStart(_parms, _gamColIndex); // gam column names before processing
        String[] gamColNames = generateGamColNamesThinPlateKnots(_gamColIndex, _parms, polyBasisArray, colNameStub);
        System.arraycopy(gamColNames, 0, _gamColNames[_gamColIndex], 0, gamColNames.length);
        String[] distanceColNames = extractColNames(gamColNames, 0, 0, _numKnots);
        String[] polyNames = extractColNames(gamColNames, _numKnots, 0, _M);
        Frame thinPlateFrame = distanceMeasure.outputFrame(Key.make(), distanceColNames, null);
        for (int index = 0; index < _numKnots; index++)
          _gamColMeans[_gamColIndex][index] = thinPlateFrame.vec(index).mean();
        double[][] starT = generateStarT(_knots, polyBasisDegree, rawColMeans, oneOverColStd, 
                _parms._standardize_tp_gam_cols); // generate T* in 3.2.3
        double[][] qmat = generateQR(starT);
        double[][] penaltyMat = distanceMeasure.generatePenalty(qmat);  // penalty matrix 3.1.1
        double[][] zCST = generateOrthogonalComplement(qmat, starT, _numKnotsMM, _parms._seed);
        copy2DArray(zCST, _zTransposeCS[_thinPlateGamColIndex]);
        ThinPlatePolynomialWithKnots thinPlatePoly = new ThinPlatePolynomialWithKnots(_numPred,
                polyBasisArray, rawColMeans, oneOverColStd, 
                _parms._standardize_tp_gam_cols).doAll(_M, Vec.T_NUM, _predictVec);// generate polynomial basis T in 3.2
        Frame thinPlatePolyBasis = thinPlatePoly.outputFrame(null, polyNames, null);
        for (int index = 0; index < _M; index++)  // calculate gamified column means
          _gamColMeans[_gamColIndex][index+_numKnots] = thinPlatePolyBasis.vec(index).mean();
        thinPlateFrame = ThinPlateDistanceWithKnots.applyTransform(thinPlateFrame, colNameStub
                +"TPKnots_", _parms, zCST, _numKnotsMM);        // generate Xcs as in 3.3
        thinPlateFrame.add(thinPlatePolyBasis.names(), thinPlatePolyBasis.removeAll());        // concatenate Xcs and T
        double[][] ztranspose =  generateZTransp(thinPlateFrame, _numKnots);     // generate Z for centering as in 3.4
        copy2DArray(ztranspose, _zTranspose[_gamColIndex]);
        double[][] penaltyMatCS = ArrayUtils.multArrArr(ArrayUtils.multArrArr(zCST, penaltyMat),
                ArrayUtils.transpose(zCST));  // transform penalty matrix to transpose(Zcs)*Xnmd*Zcs, 3.3
        if (_parms._scale_tp_penalty_mat) {   // R does this scaling of penalty matrix.  I left it to users to choose
          ScaleTPPenalty scaleTPPenaltyCS = new ScaleTPPenalty(penaltyMatCS, thinPlateFrame).doAll(thinPlateFrame);
          _penaltyScale[_gamColIndex] = scaleTPPenaltyCS._s_scale;
          penaltyMatCS = scaleTPPenaltyCS._penaltyMat;
        }
        double[][] expandPenaltyCS = expandArray(penaltyMatCS, _numKnots);  // used for penalty matrix
        if (_savePenaltyMat) {                                              // save intermediate steps for debugging
          copy2DArray(penaltyMat, _penaltyMat[_gamColIndex]);
          copy2DArray(starT, _starT[_thinPlateGamColIndex]);
          copy2DArray(penaltyMatCS, _penaltyMatCS[_thinPlateGamColIndex]);
        }
        double[][] penaltyCenter = ArrayUtils.multArrArr(ArrayUtils.multArrArr(ztranspose, expandPenaltyCS),
                ArrayUtils.transpose(ztranspose));
        copy2DArray(penaltyCenter, _penaltyMatCenter[_gamColIndex]);
        thinPlateFrame = ThinPlateDistanceWithKnots.applyTransform(thinPlateFrame, colNameStub+"center_", 
                _parms, ztranspose, _numKnotsM1);          // generate Xz as in 3.4
        _gamFrameKeysCenter[_gamColIndex] = thinPlateFrame._key;
        DKV.put(thinPlateFrame);
        System.arraycopy(thinPlateFrame.names(), 0, _gamColNamesCenter[_gamColIndex], 0, _numKnotsM1);
      }
    }
    
    public class CubicSplineSmoother extends RecursiveAction {
      final Frame _predictVec;
      final int _numKnots;
      final int _numKnotsM1;
      final int _splineType;
      final boolean _savePenaltyMat;
      final String[] _newColNames;
      final double[] _knots;
      final GAMParameters _parms;
      final AllocateType _fileMode;
      final int _gamColIndex;
      final int _csIndex;
      
      public CubicSplineSmoother(Frame predV, GAMParameters parms, int gamColIndex, String[] gamColNames, double[] knots,
                                 AllocateType fileM, int csInd) {
        _predictVec = predV;
        _numKnots = parms._num_knots_sorted[gamColIndex];
        _numKnotsM1 = _numKnots-1;
        _splineType = parms._bs_sorted[gamColIndex];
        _savePenaltyMat = parms._savePenaltyMat;
        _newColNames = gamColNames;
        _knots = knots;
        _parms = parms;
        _gamColIndex = gamColIndex;
        _fileMode = fileM;
        _csIndex = csInd;
      }

      @Override
      protected void compute() {
        GenerateGamMatrixOneColumn genOneGamCol = new GenerateGamMatrixOneColumn(_splineType, _numKnots,
                _knots, _predictVec).doAll(_numKnots, Vec.T_NUM, _predictVec);
        if (_savePenaltyMat) {                                  // only save this for debugging
          copy2DArray(genOneGamCol._penaltyMat, _penaltyMat[_gamColIndex]); // copy penalty matrix
          _penaltyScale[_gamColIndex] = genOneGamCol._s_scale;  // penaltyMat is scaled by 1/_s_scale
        }
        Frame oneAugmentedColumnCenter = genOneGamCol.outputFrame(Key.make(), _newColNames,
                null);
        for (int index = 0; index < _numKnots; index++)
          _gamColMeans[_gamColIndex][index] = oneAugmentedColumnCenter.vec(index).mean();
        oneAugmentedColumnCenter = genOneGamCol.centralizeFrame(oneAugmentedColumnCenter,
                _predictVec.name(0) + "_" + _splineType + "_center_", _parms);
        copy2DArray(genOneGamCol._ZTransp, _zTranspose[_gamColIndex]); // copy transpose(Z)
        double[][] transformedPenalty = ArrayUtils.multArrArr(ArrayUtils.multArrArr(genOneGamCol._ZTransp,
                genOneGamCol._penaltyMat), ArrayUtils.transpose(genOneGamCol._ZTransp));  // transform penalty as zt*S*z
        copy2DArray(transformedPenalty, _penaltyMatCenter[_gamColIndex]);
        _gamFrameKeysCenter[_gamColIndex] = oneAugmentedColumnCenter._key;
        DKV.put(oneAugmentedColumnCenter);
        System.arraycopy(oneAugmentedColumnCenter.names(), 0, _gamColNamesCenter[_gamColIndex], 0,
                _numKnotsM1);
        copy2DArray(genOneGamCol._bInvD, _binvD[_csIndex]);
      }
    }
    
    // During CV, _parms.train() will contain only predictors, response and cv weights.  Gam columns that are not
    // part of the predictors will not be there.  Hence, I stored the original _parms.train() in _origTrain and then
    // restore it when we need to restore the orighinal _parms.train().
    public Frame getTrainFrame(Frame trainFrame) {
      if (_cvOn && trainFrame.numRows() == _origTrain.numRows()) {  // special treatment for cv training frame
        if (Arrays.asList(trainFrame.names()).contains("__internal_cv_weights__")) {
          Frame origTrain = _origTrain.clone();
          origTrain.add("__internal_cv_weights__", trainFrame.vec("__internal_cv_weights__"));
          return origTrain;
        } else {
          return _origTrain;
        }
      }
      return trainFrame;
    }

    void addGAM2Train() {
      final int numGamFrame = _parms._gam_columns.length; // number of smoothers to generate
      RecursiveAction[] generateGamColumn = new RecursiveAction[numGamFrame];
      int thinPlateInd = 0;
      int csInd = 0;
      Frame trainFrame = getTrainFrame(_parms.train());
      for (int index = 0; index < numGamFrame; index++) { // generate smoothers/splines
        final Frame predictVec = prepareGamVec(index, _parms, trainFrame);// extract predictors from frame
        final int numKnots = _parms._num_knots_sorted[index];
        final int numKnotsM1 = numKnots - 1;
        if (_parms._bs_sorted[index] == 0) {  // for CS smoothers
          _gamColNames[index] = generateGamColNames(index, _parms);
          _gamColNamesCenter[index] = new String[numKnotsM1];
          _gamColMeans[index] = new double[numKnots];
          generateGamColumn[index] = new CubicSplineSmoother(predictVec, _parms, index, _gamColNames[index],
                  _knots[index][0], firstTwoLess, csInd++);
        } else {  // TP splines with knots
          final int kPlusM = _parms._num_knots_sorted[index]+_parms._M[thinPlateInd];
          _gamColNames[index] = new String[kPlusM];
          _gamColNamesCenter[index] = new String[numKnotsM1];
          _gamColMeans[index] = new double[kPlusM];
          _allPolyBasisList[thinPlateInd] = new int[_parms._M[thinPlateInd]][_parms._gamPredSize[index]];
          _gamColMeansRaw[thinPlateInd] = new double[_parms._gamPredSize[index]];
          _oneOGamColStd[thinPlateInd] = new double[_parms._gamPredSize[index]];
          generateGamColumn[index] = new ThinPlateRegressionSmootherWithKnots(predictVec, _parms, index, _knots[index], 
                  thinPlateInd++);
        }
      }
      ForkJoinTask.invokeAll(generateGamColumn);
    }

    void verifyGamTransformedFrame(Frame gamTransformed) {
      final int numGamFrame = _parms._gam_columns.length;
      for (int findex = 0; findex < numGamFrame; findex++) {
        final int numGamCols = _gamColNamesCenter[findex].length;
        for (int index = 0; index < numGamCols; index++) {
          if (gamTransformed.vec(_gamColNamesCenter[findex][index]).isConst())
            error(_gamColNamesCenter[findex][index], "gam column transformation generated constant columns" +
                    " for " + _parms._gam_columns[findex]);
        }
      }
    }
    
    @Override
    public void computeImpl() {
      init(true);
      if (error_count() > 0)   // if something goes wrong, let's throw a fit
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GAM.this);
      // add gamified columns to training frame
      Frame newTFrame = new Frame(rebalance(adaptTrain(), false, _result+".temporary.train"));
      verifyGamTransformedFrame(newTFrame);
      
      if (error_count() > 0)   // if something goes wrong during gam transformation, let's throw a fit again!
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GAM.this);
      
      if (valid() != null)  // transform the validation frame if present
        _valid = rebalance(adaptValidFrame(getTrainFrame(_parms.valid()), _valid,  _parms, _gamColNamesCenter, _binvD,
                _zTranspose, _knots, _zTransposeCS, _allPolyBasisList, _gamColMeansRaw, _oneOGamColStd), 
                false, _result+".temporary.valid");
      DKV.put(newTFrame); // This one will cause deleted vectors if add to Scope.track
      Frame newValidFrame = _valid == null ? null : new Frame(_valid);
      if (newValidFrame != null) {
        DKV.put(newValidFrame);
      }
      _job.update(0, "Initializing model training");
      buildModel(newTFrame, newValidFrame); // build gam model
    }

    public final void buildModel(Frame newTFrame, Frame newValidFrame) {
      GAMModel model = null;
      DataInfo dinfo = null;
      final IcedHashSet<Key<Frame>> validKeys = new IcedHashSet<>();
      try {
        _job.update(0, "Adding GAM columns to training dataset...");
        dinfo = new DataInfo(_train.clone(), _valid, 1, _parms._use_all_factor_levels 
                || _parms._lambda_search, _parms._standardize ? 
                DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                _parms.missingValuesHandling() == GLMParameters.MissingValuesHandling.Skip,
                _parms.missingValuesHandling() == GLMParameters.MissingValuesHandling.MeanImputation 
                        || _parms.missingValuesHandling() == GLMParameters.MissingValuesHandling.PlugValues,
                _parms.makeImputer(), false, hasWeightCol(), hasOffsetCol(), hasFoldCol(),
                _parms.interactionSpec());
        DKV.put(dinfo._key, dinfo);
        model = new GAMModel(dest(), _parms, new GAMModel.GAMModelOutput(GAM.this, dinfo));
        model.write_lock(_job);
        if (_parms._keep_gam_cols) {  // save gam column keys
          model._output._gamTransformedTrainCenter = newTFrame._key;
        }
        _job.update(1, "calling GLM to build GAM model...");
        GLMModel glmModel = buildGLMModel(_parms, newTFrame, newValidFrame); // obtained GLM model
        if (model.evalAutoParamsEnabled) {
          model.initActualParamValuesAfterGlmCreation();
        }
        Scope.track_generic(glmModel);
        _job.update(0, "Building out GAM model...");
        fillOutGAMModel(glmModel, model); // build up GAM model by copying over results in glmModel
        model.update(_job);
        // build GAM Model Metrics
        _job.update(0, "Scoring training frame");
        scoreGenModelMetrics(model, train(), true); // score training dataset and generate model metrics
        if (valid() != null) {
          scoreGenModelMetrics(model, valid(), false); // score validation dataset and generate model metrics
        }
      } catch(Gram.NonSPDMatrixException exception) {
        throw new Gram.NonSPDMatrixException("Consider enable lambda_search, decrease scale parameter value for TP " +
                "smoothers, \ndisable scaling for TP penalty matrics, or not use thin plate regression smoothers at all.");
      } finally {
        try {
          final List<Key<Vec>> keep = new ArrayList<>();
          if (model != null) {
            if (_parms._keep_gam_cols) {
              keepFrameKeys(keep, newTFrame._key);
            } else {
              DKV.remove(newTFrame._key);
            }
          }
          if (dinfo != null)
            dinfo.remove();

          if (newValidFrame != null && validKeys != null) {
            keepFrameKeys(keep, newValidFrame._key);  // save valid frame keys for scoring later
            validKeys.addIfAbsent(newValidFrame._key);   // save valid frame keys from folds to remove later
            model._validKeys = validKeys;  // move valid keys here to model._validKeys to be removed later
          }
          Scope.exit(keep.toArray(new Key[keep.size()]));
        } finally {
          // Make sure Model is unlocked, as if an exception is thrown, the `ModelBuilder` expects the underlying model to be unlocked.
          model.update(_job);
          model.unlock(_job);
        }
      }
    }

    /**
     * This part will perform scoring and generate the model metrics for training data and validation data if 
     * provided by user.
     *      
     * @param model
     * @param scoreFrame
     * @param forTraining true for training dataset and false for validation dataset
     */
    private void scoreGenModelMetrics(GAMModel model, Frame scoreFrame, boolean forTraining) {
      Frame scoringTrain = new Frame(scoreFrame);
      model.adaptTestForTrain(scoringTrain, true, true);
      Frame scoredResult = model.score(scoringTrain);
      scoredResult.delete();
      ModelMetrics mtrain = ModelMetrics.getFromDKV(model, scoringTrain);
      if (mtrain!=null) {
        if (forTraining)
          model._output._training_metrics = mtrain;
        else 
          model._output._validation_metrics = mtrain;
        Log.info("GAM[dest="+dest()+"]"+mtrain.toString());
      } else {
        Log.info("Model metrics is empty!");
      }
    }

    GLMModel buildGLMModel(GAMParameters parms, Frame trainData, Frame validFrame) {
      GLMParameters glmParam = copyGAMParams2GLMParams(parms, trainData, validFrame);  // copy parameter from GAM to GLM
      if (_cv_lambda != null) { // use best alpha and lambda values from cross-validation to build GLM main model 
        glmParam._lambda = _cv_lambda;
        glmParam._alpha = _cv_alpha;
        glmParam._lambda_search = false;
      }
      int numGamCols = _parms._gam_columns.length;
      for (int find = 0; find < numGamCols; find++) {
        if ((_parms._scale != null) && (_parms._scale[find] != 1.0))
          _penaltyMatCenter[find] = ArrayUtils.mult(_penaltyMatCenter[find], _parms._scale[find]);
      }
      glmParam._glmType = gam;
      return new GLM(glmParam, _penaltyMatCenter,  _gamColNamesCenter).trainModel().get();
    }

    void fillOutGAMModel(GLMModel glm, GAMModel model) {
      model._gamColNamesNoCentering = _gamColNames;  // copy over gam column names
      model._gamColNames = _gamColNamesCenter;
      model._output._gamColNames = _gamColNamesCenter;
      model._output._zTranspose = _zTranspose;
      model._output._zTransposeCS = _zTransposeCS;
      model._output._allPolyBasisList = _allPolyBasisList;
      model._gamFrameKeysCenter = _gamFrameKeysCenter;
      model._nclass = _nclass;
      model._output._binvD = _binvD;
      model._output._knots = _knots;
      model._output._numKnots = _numKnots;
      model._cubicSplineNum = _cubicSplineNum;
      model._thinPlateSmoothersWithKnotsNum = _thinPlateSmoothersWithKnotsNum;
      model._output._gamColMeansRaw = _gamColMeansRaw;
      model._output._oneOGamColStd = _oneOGamColStd;
      // extract and store best_alpha/lambda/devianceTrain/devianceValid from best submodel of GLM model
      model._output._best_alpha = glm._output.getSubmodel(glm._output._selected_submodel_idx).alpha_value;
      model._output._best_lambda = glm._output.getSubmodel(glm._output._selected_submodel_idx).lambda_value;
      model._output._devianceTrain = glm._output.getSubmodel(glm._output._selected_submodel_idx).devianceTrain;
      model._output._devianceValid = glm._output.getSubmodel(glm._output._selected_submodel_idx).devianceValid;
      model._gamColMeans = flat(_gamColMeans);
      if (_parms._lambda == null) // copy over lambdas used
        _parms._lambda = glm._parms._lambda.clone();
      if (_parms._keep_gam_cols)
        model._output._gam_transformed_center_key = model._output._gamTransformedTrainCenter.toString();
      if (_parms._savePenaltyMat) {
        model._output._penaltyMatricesCenter = _penaltyMatCenter;
        model._output._penaltyMatrices = _penaltyMat;
        model._output._penaltyScale = _penaltyScale;
        if (_thinPlateSmoothersWithKnotsNum > 0) {
          model._output._penaltyMatCS = _penaltyMatCS;
          model._output._starT = _starT;
        }
      }
      copyGLMCoeffs(glm, model, _parms, nclasses());  // copy over coefficient names and generate coefficients as beta = z*GLM_beta
      copyGLMtoGAMModel(model, glm, _parms, valid());  // copy over fields from glm model to gam model
    }
  }
}
