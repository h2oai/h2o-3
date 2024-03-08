package hex.gam;

import hex.*;
import hex.gam.GAMModel.GAMParameters;
import hex.gam.GamSplines.ThinPlateDistanceWithKnots;
import hex.gam.GamSplines.ThinPlatePolynomialWithKnots;
import hex.gam.MatrixFrameUtils.GamUtils;
import hex.gam.MatrixFrameUtils.GenCSSplineGamOneColumn;
import hex.gam.MatrixFrameUtils.GenISplineGamOneColumn;
import hex.gam.MatrixFrameUtils.GenMSplineGamOneColumn;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMParameters;
import hex.gram.Gram;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import org.apache.commons.lang.NotImplementedException;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.ArrayUtils;
import water.util.IcedHashSet;
import water.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static hex.gam.GAMModel.adaptValidFrame;
import static hex.gam.GamSplines.ThinPlateRegressionUtils.*;
import static hex.gam.MatrixFrameUtils.GAMModelUtils.*;
import static hex.gam.MatrixFrameUtils.GamUtils.AllocateType.*;
import static hex.gam.MatrixFrameUtils.GamUtils.*;
import static hex.gam.MatrixFrameUtils.GenCSSplineGamOneColumn.generateZTransp;
import static hex.genmodel.algos.gam.GamMojoModel.*;
import static hex.genmodel.utils.ArrayUtils.flat;
import static hex.glm.GLMModel.GLMParameters.Family.multinomial;
import static hex.glm.GLMModel.GLMParameters.Family.ordinal;
import static hex.glm.GLMModel.GLMParameters.GLMType.gam;
import static hex.util.LinearAlgebraUtils.generateOrthogonalComplement;
import static hex.util.LinearAlgebraUtils.generateQR;
import static water.util.ArrayUtils.expandArray;
import static water.util.ArrayUtils.subtract;


public class GAM extends ModelBuilder<GAMModel, GAMModel.GAMParameters, GAMModel.GAMModelOutput> {
  private static final int MIN_CSPLINE_NUM_KNOTS = 3;
  private static final int MIN_MorI_SPLINE_KNOTS = 2;
  private double[][][] _knots; // Knots for splines
  private int _thinPlateSmoothersWithKnotsNum = 0;
  private int _cubicSplineNum = 0;
  private int _iSplineNum = 0;
  private int _mSplineNum = 0;
  double[][] _gamColMeansRaw; // store raw gam column means in gam_column_sorted order and only for thin plate smoothers
  public double[][] _oneOGamColStd;
  public double[] _penaltyScale;
  public int _glmNFolds = 0;
  Model.Parameters.FoldAssignmentScheme _foldAssignment = null;
  String _foldColumn = null;
  boolean _cvOn = false;
  
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
    int isInd = _cubicSplineNum;
    int msInd = _cubicSplineNum+_iSplineNum;
    int tpInd = _cubicSplineNum+_iSplineNum+_mSplineNum;
    int gamIndex; // index into the sorted arrays with CS/I-splines/M front, TP back.
    for (int outIndex = 0; outIndex < _parms._gam_columns.length; outIndex++) { // go through each gam_column group
      String tempKey = allNull ? null : _parms._knot_ids[outIndex]; // one knot_id for each smoother
      if (_parms._bs[outIndex] == TP_SPLINE_TYPE) { // thin plate regression
        gamIndex = tpInd++;
      } else if (_parms._bs[outIndex] == CS_SPLINE_TYPE) {
        gamIndex = csInd++;
      } else if (_parms._bs[outIndex] == IS_SPLINE_TYPE) {
        gamIndex = isInd++;
      } else if (_parms._bs[outIndex] == MS_SPLINE_TYPE) { // m-spline
        gamIndex = msInd++;
      } else {
        throw new NotImplementedException(SPLINENOTIMPL);
      }
      knots[gamIndex] = new double[_parms._gam_columns[outIndex].length][];
      if (tempKey != null) {  // read knots location from Frame given by user      
        final Frame knotFrame = DKV.getGet(tempKey);
        double[][] knotContent = new double[(int) knotFrame.numRows()][_parms._gam_columns[outIndex].length];
        final ArrayUtils.FrameToArray f2a = new ArrayUtils.FrameToArray(0,
                _parms._gam_columns[outIndex].length - 1, knotFrame.numRows(), knotContent);
        knotContent = f2a.doAll(knotFrame).getArray();  // first index is row, second index is column

        final double[][] knotCTranspose = ArrayUtils.transpose(knotContent);// change knots to correct order
        for (int innerIndex = 0; innerIndex < knotCTranspose.length; innerIndex++) {
          knots[gamIndex][innerIndex] = new double[knotContent.length];
          System.arraycopy(knotCTranspose[innerIndex], 0, knots[gamIndex][innerIndex], 0,
                  knots[gamIndex][innerIndex].length);
          if (knotCTranspose.length == 1 && (_parms._bs[outIndex] == CS_SPLINE_TYPE ||
                  _parms._bs[outIndex] == MS_SPLINE_TYPE || _parms._bs[outIndex] == IS_SPLINE_TYPE)) // only check for order to single smoothers
            failVerifyKnots(knots[gamIndex][innerIndex], outIndex);
        }
        _parms._num_knots[outIndex] = knotContent.length;

      } else {  // current column knot key is null, we will use default method to generate knots
        final Frame predictVec = new Frame(_parms._gam_columns[outIndex], 
                _parms.train().vecs(_parms._gam_columns[outIndex]));
        if (_parms._bs[outIndex] == CS_SPLINE_TYPE || _parms._bs[outIndex] == IS_SPLINE_TYPE ||
                _parms._bs[outIndex] == MS_SPLINE_TYPE) {
          knots[gamIndex][0] = generateKnotsOneColumn(predictVec, _parms._num_knots[outIndex]);
          failVerifyKnots(knots[gamIndex][0], outIndex);
        } else {  // generate knots for multi-predictor smoothers
          knots[gamIndex] = genKnotsMultiplePreds(predictVec, _parms, outIndex);
          failVerifyKnots(knots[gamIndex][0], outIndex);
        }
      }
      if (_parms._bs[outIndex] == MS_SPLINE_TYPE) {
        int numBasis = _parms._spline_orders[outIndex] + _parms._num_knots[outIndex] - 2;
        if (numBasis < 2)
          error("spline_orders and num_knots", "M-spline for column "+
                  _parms._gam_columns[outIndex][0]+" with spline_orders=1 must have more than 2 knots.");        
      }
    }
    return knots; // CS/I-splines come first, TP is at the back
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
    if (_parms._nfolds > 0 || _parms._fold_column != null) {
      _parms._glmCvOn = true; // added for client mode
      _parms._glmNFolds = _parms._fold_column == null ? _parms._nfolds
              : _parms.train().vec(_parms._fold_column).toCategoricalVec().domain().length;
      _cvOn = true;
      _glmNFolds = _parms._glmNFolds;

      if (_parms._fold_assignment != null) {
        _parms._glmFoldAssignment = _parms._fold_assignment; // added for client mode
        _foldAssignment = _parms._fold_assignment;
        _parms._fold_assignment = null;
      }
      if (_parms._fold_column != null) {
        _parms._glmFoldColumn = _parms._fold_column; // added for client mode
        _foldColumn = _parms._fold_column;
        _parms._fold_column = null;
      }
      _parms._nfolds = 0;
    }
    super.init(expensive);
    if (_parms._bs != null) {
      boolean allMonotoneSplines = Arrays.stream(_parms._bs).filter(x -> x == 2).count() == _parms._bs.length;
      boolean containsMonotoneSplines = Arrays.stream(_parms._bs).filter(x -> x == 2).count() > 0;
      if (allMonotoneSplines && containsMonotoneSplines && !_parms._non_negative) {
        warn("non_negative", " is not set to true when I-spline/monotone-spline (bs=2) is chosen." +
                "  You will not get monotonic output in this case even though you choose I-spline.");
      }
    }
    if (expensive && (_knots == null))  // add GAM specific check here, only do it once especially during CV
      validateGAMParameters();
  }
  
  private void validateGAMParameters() {
    if (_parms._max_iterations == 0)
      error("_max_iterations", H2O.technote(2, "if specified, must be >= 1."));
    if (_parms._gam_columns == null) { // check _gam_columns contains valid columns
      error("_gam_columns", "must specify columns names to apply GAM to.  If you don't have any," +
              " use GLM.");
    } else { // check and make sure gam_columns column types are legal
      checkGAMParamsLengths();
      if (_parms._bs == null)
        setDefaultBSType(_parms); // default to cs spline and thin plate for higher dimension
      assertValidGAMColumnsCountSplineTypes(); // also number of CS, TP, I-spline, M-splines smoothers determined.
    }
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GAM.this);
    if (_parms._scale == null)
      setDefaultScale(_parms);
    setGamPredSize(_parms, _cubicSplineNum+_iSplineNum+_mSplineNum);
    if (_thinPlateSmoothersWithKnotsNum > 0)
      setThinPlateParameters(_parms, _thinPlateSmoothersWithKnotsNum); // set the m, M for thin plate regression smoothers
    checkOrChooseNumKnots(); // check valid num_knot assignment or choose num_knots
    checkTrainRowNumKnots();
    _knots = generateKnotsFromKeys(); // generate knots and verify that they are given correctly
    sortGAMParameters(_parms, _cubicSplineNum, _iSplineNum, _mSplineNum); // move single predictor spline to front and thin plate to back
    checkThinPlateParams();
    if (_parms._saveZMatrix && ((_train.numCols() - 1 + _parms._num_knots.length) < 2))
      error("_saveZMatrix", "can only be enabled if the number of predictors plus" +
              " Gam columns in gam_columns exceeds 2");
    if ((_parms._lambda_search || !_parms._intercept || _parms._lambda == null || _parms._lambda[0] > 0))
      _parms._use_all_factor_levels = true;
    checkNFamilyNLinkAssignment();
  }

  /**
   * Check and make sure the there are enough number of rows in the training dataset to accomodate the num_knot 
   * settings.
   */
  public void checkTrainRowNumKnots() {
    for (int index = 0; index < _parms._gam_columns.length; index++) {
      Frame dataset = _parms.train();
      String cname = _parms._gam_columns[index][0]; // only check the first gam_column
      if (_parms._bs[index] < 0 && _parms._bs[index] > 3)
        error("bs", " bs can only be 0, 1, 2 and 3 but is "+_parms._bs[index]);
      if (dataset.vec(cname).isInt() && ((dataset.vec(cname).max() - dataset.vec(cname).min() + 1) < _parms._num_knots[index]))
        error("gam_columns", "column " + cname + " has cardinality lower than the number of knots and cannot be used as a gam" +
                " column.");
    }
  }

  /***
   * Check and make sure if related parameters are defined, they must be of correct length.  Their length must
   * equal to the number of gam columns specified which is the length of _parms._gam_columns.length.
   */
  public void checkGAMParamsLengths() {
    if ((_parms._bs != null) && (_parms._gam_columns.length != _parms._bs.length))  // check length
      error("bs", "Number of spline types in bs must match the number of gam column groups " +
              "(gam_columns.length) specified in gam_columns");
    if (_parms._knot_ids != null && (_parms._knot_ids.length != _parms._gam_columns.length)) // check knots location specification
      error("knot_ids", "Number of knot_ids specified must match the number of gam column groups " +
              "(gam_columns.length) specified in gam_columns");
    if (_parms._num_knots != null && (_parms._num_knots.length != _parms._gam_columns.length))
      error("num_knots", "Number of num_knots specified must match the number of gam column groups " +
              "(gam_columns.length) specified in gam_columns");
    if (_parms._scale != null && (_parms._scale.length != _parms._gam_columns.length))
      error("scale", "Number of scale specified must match the number of gam column groups " +
              "(gam_columns.length) specified in gam_columns");
    if (_parms._splines_non_negative != null && (_parms._splines_non_negative.length != _parms._gam_columns.length))
      error("splines_non_negative", "Number of splines_non_negative specified must match the number" +
              " of gam column groups (gam_columns.length) specified in gam_columns");
  }

  /***
   * check if _parms._family = AUTO, the correct link functions are assigned according to the response type.  If no
   * family type is assigned, they will be assigned automatically according to the response type.
   */
  public void checkNFamilyNLinkAssignment() {
    if (_parms._family == GLMParameters.Family.AUTO) {
      if (nclasses() == 1 & _parms._link != GLMParameters.Link.family_default && _parms._link != GLMParameters.Link.identity
              && _parms._link != GLMParameters.Link.log && _parms._link != GLMParameters.Link.inverse && _parms._link != null) {
        error("_family", H2O.technote(2, "AUTO for undelying response requires the link to" +
                " be family_default, identity, log or inverse."));
      } else if (nclasses() == 2 & _parms._link != GLMParameters.Link.family_default && _parms._link != GLMParameters.Link.logit
              && _parms._link != null) {
        error("_family", H2O.technote(2, "AUTO for undelying response requires the link to" +
                " be family_default or logit."));
      } else if (nclasses() > 2 & _parms._link != GLMParameters.Link.family_default & _parms._link != GLMParameters.Link.multinomial
              && _parms._link != null) {
        error("_family", H2O.technote(2, "AUTO for undelying response requires the link to" +
                " be family_default or multinomial."));
      }
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
    System.arraycopy(_parms._num_knots_sorted, _cubicSplineNum+_iSplineNum+_mSplineNum, _parms._num_knots_tp, 0,
            _thinPlateSmoothersWithKnotsNum);
    int tpIndex = 0;
    for (int index = 0; index < _parms._gam_columns.length; index++) {
      if (_parms._bs_sorted[index] == TP_SPLINE_TYPE) {
        if (_parms._num_knots_sorted[index] < _parms._M[tpIndex] + 1) {
          error("num_knots", "num_knots for gam column start with  " + _parms._gam_columns_sorted[index][0] +
                  " did not specify enough num_knots.  It should be equal or greater than " + (_parms._M[tpIndex] + 1) + ".");
        }
        tpIndex++;
      }
    }
  }

  /**
   * set default num_knots to 10 for gam_columns where there is no knot_id specified for CS smoothers
   * for TP smoothers, default is set to be max of 10 or _M+2.
   * for I-splines, default set to 2 which is minimum.
   * for M-splines, default set to 2 which is minimum.
   */
  public void checkOrChooseNumKnots() {
    if (_parms._num_knots == null)
      _parms._num_knots = new int[_parms._gam_columns.length];  // different columns may have different num knots
    if (_parms._spline_orders == null) {
      _parms._spline_orders = new int[_parms._gam_columns.length];
      Arrays.fill(_parms._spline_orders, 3);
    } else {
      for (int index=0; index<_parms._spline_orders.length; index++)
        if ((_parms._bs[index] == IS_SPLINE_TYPE || _parms._bs[index] == MS_SPLINE_TYPE) && _parms._spline_orders[index] < 1)
          error("spline_orders", "GAM I-spline spline_orders must be >= 1");
    }
    int tpCount = 0;
    for (int index = 0; index < _parms._num_knots.length; index++) {  // set zero value _num_knots
      if (_parms._knot_ids == null || (_parms._knot_ids != null && _parms._knot_ids[index] == null)) {  // knots are not specified
        int numKnots = _parms._num_knots[index];
        if (_parms._bs[index] == IS_SPLINE_TYPE || _parms._bs[index] == MS_SPLINE_TYPE) {
          if (_parms._num_knots[index] == 0) {
            _parms._num_knots[index] = MIN_MorI_SPLINE_KNOTS;
            if (_parms._bs[index] == MS_SPLINE_TYPE && _parms._spline_orders[index] == 1)
              _parms._num_knots[index] += 1;
          } else if (_parms._num_knots[index] < MIN_MorI_SPLINE_KNOTS) {
            error("num_knots", " must >= "+MIN_MorI_SPLINE_KNOTS+" for M or I-splines.");
          }
        }
        int naSum = 0;
        for (int innerIndex = 0; innerIndex < _parms._gam_columns[index].length; innerIndex++) {
          naSum += _parms.train().vec(_parms._gam_columns[index][innerIndex]).naCnt();
        }
        long eligibleRows = _train.numRows()-naSum;
        if (_parms._num_knots[index] == 0) {  // set num_knots to default
          int defaultRows = 10;
          if (_parms._bs[index] == TP_SPLINE_TYPE) {
            defaultRows = Math.max(defaultRows, _parms._M[tpCount] + 2);
            tpCount++;
          }
          if (_parms._bs[index] == IS_SPLINE_TYPE || _parms._bs[index] == MS_SPLINE_TYPE)
            defaultRows = MIN_MorI_SPLINE_KNOTS;
          _parms._num_knots[index] = eligibleRows < defaultRows ? (int) eligibleRows : defaultRows;
        } else {  // num_knots assigned by user and check to make sure it is legal
          if (numKnots > eligibleRows) {
            error("num_knots", " number of knots specified in num_knots: "+numKnots+" for smoother" +
                    " with first predictor "+_parms._gam_columns[index][0]+".  Reduce _num_knots.");
          }
          if (_parms._bs[index] == CS_SPLINE_TYPE && _parms._num_knots[index] < MIN_CSPLINE_NUM_KNOTS)
            error("num_knots", " number of knots specified in num_knots "+numKnots+
                    " for cs splines must be >= " + MIN_CSPLINE_NUM_KNOTS + ".");

          if ((_parms._bs[index] == IS_SPLINE_TYPE || _parms._bs[index] == MS_SPLINE_TYPE) 
                  && _parms._num_knots[index] < MIN_MorI_SPLINE_KNOTS)
            error("num_knots", " number of knots specified "+numKnots+" for M or I-splines must be" +
                    " >= "+MIN_MorI_SPLINE_KNOTS);
        }
      }
    }
  }
  


  /**
   * Check and make sure correct BS type is assigned to the various gam_columns specified.  Make sure the gam columns
   * specified are actually found in the training dataset.  In addition, the number of CS and TP smoothers are 
   * counted here as well.
   */
  public void assertValidGAMColumnsCountSplineTypes() {
    Frame dataset = _parms.train();
    List<String> cNames = Arrays.asList(dataset.names());
    for (int index = 0; index < _parms._gam_columns.length; index++) {
      if (_parms._bs != null) { // check and make sure the correct bs type is chosen
        if (_parms._gam_columns[index].length > 1 && _parms._bs[index] != 1)
          error("bs", "Smoother with multiple predictors can only use with thin plate spines, i.e., " +
                  "bs = 1");
        if (_parms._bs[index] == TP_SPLINE_TYPE)
          _thinPlateSmoothersWithKnotsNum++; // record number of thin plate
        if (_parms._bs[index] == CS_SPLINE_TYPE)
          _cubicSplineNum++;
        if (_parms._bs[index] == IS_SPLINE_TYPE) {
          if (multinomial.equals(_parms._family) || ordinal.equals(_parms._family))
            error("family", "multinomial and ordinal families cannot be used with I-splines.");
          _iSplineNum++;
        }
        if (_parms._bs[index] == MS_SPLINE_TYPE)
          _mSplineNum++;
        
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
    if (_parms._glmCvOn) {  // for client mode, copy over the cv settings
      _cvOn = true;
      if (_parms._glmFoldAssignment != null)
        _foldAssignment = _parms._glmFoldAssignment;
      if (_parms._glmFoldColumn != null)
        _foldColumn = _parms._glmFoldColumn;
      _glmNFolds = _parms._glmNFolds;
    }
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
    Key<Frame>[] _gamFrameKeysCenter;
    double[][] _gamColMeans;          // store gam column means without centering.
    int[][][] _allPolyBasisList;      // store polynomial basis function for all TP smoothers
    DataInfo _dinfo = null;
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
      zeroOutIStranspose(_parms._bs_sorted, _zTranspose);
      _penaltyMat = _parms._savePenaltyMat?GamUtils.allocate3DArray(numGamFrame, _parms, sameOrig):null;
      _penaltyMatCenter = GamUtils.allocate3DArray(numGamFrame, _parms, bothOneLess);
      removeCenteringIS(_penaltyMatCenter, _parms);
      if (_cubicSplineNum > 0)  // CS-spline only
        _binvD = GamUtils.allocate3DArrayCS(_cubicSplineNum, _parms, firstTwoLess);
      _numKnots = MemoryManager.malloc4(numGamFrame);
      _gamColNames = new String[numGamFrame][];
      _gamColNamesCenter = new String[numGamFrame][];
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
      return buildGamFrame(_parms, _train, _gamFrameKeysCenter, _foldColumn); // add gam cols to _train
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
        double[][] penaltyMat = distanceMeasure.generatePenalty();  // penalty matrix 3.1.1
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
        thinPlateFrame = ThinPlateDistanceWithKnots.applyTransform(thinPlateFrame, colNameStub+"center", 
                _parms, ztranspose, _numKnotsM1);          // generate Xz as in 3.4
        _gamFrameKeysCenter[_gamColIndex] = thinPlateFrame._key;
        DKV.put(thinPlateFrame);
        System.arraycopy(thinPlateFrame.names(), 0, _gamColNamesCenter[_gamColIndex], 0, _numKnotsM1);
      }
    }
    
    public class ISplineSmoother extends RecursiveAction {
      final Frame _predictVec;
      final int _numKnots;  // not counting knot duplication here
      final int _order;
      final double[] _knots;  // not counting knot duplication here
      final boolean _savePenaltyMat;
      final String[] _newGAMColNames;
      final int _gamColIndex; // gam column order from user input
      final int _singlePredSplineInd; // gam column index after moving tp to the back
      final int _splineType;
      
      public ISplineSmoother(Frame gamPred, GAMParameters parms, int gamColIndex, String[] gamColNames, double[] knots,
                             int singlePredInd) {
        _predictVec = gamPred;
        _numKnots = parms._num_knots_sorted[gamColIndex];
        _knots = knots;
        _order = parms._spline_orders_sorted[gamColIndex];
        _savePenaltyMat = parms._savePenaltyMat;
        _newGAMColNames = gamColNames;
        _gamColIndex = gamColIndex;
        _singlePredSplineInd = singlePredInd;
        _splineType = parms._bs_sorted[gamColIndex];
      }

      @Override
      protected void compute() {
        // generate GAM basis functions
        int order = _parms._spline_orders_sorted[_gamColIndex];
        int numBasis = _knots.length+order-2;
        int totKnots = numBasis + order;
        GenISplineGamOneColumn oneGAMCol = new GenISplineGamOneColumn(_parms, _knots, _gamColIndex, _predictVec, 
                numBasis, totKnots);
        oneGAMCol.doAll(oneGAMCol._numBasis, Vec.T_NUM, _predictVec);
        if (_savePenaltyMat) {
          copy2DArray(oneGAMCol._penaltyMat, _penaltyMat[_gamColIndex]);
          _penaltyScale[_gamColIndex] = oneGAMCol._s_scale;
        }
        // extract generated gam columns
        Frame oneGamifiedColumn = oneGAMCol.outputFrame(Key.make(), _newGAMColNames, null);
        for (int index=0; index<numBasis; index++)
          _gamColMeans[_gamColIndex][index] = oneGamifiedColumn.vec(index).mean();
        DKV.put(oneGamifiedColumn);
        _gamFrameKeysCenter[_gamColIndex] = oneGamifiedColumn._key;
        System.arraycopy(oneGamifiedColumn.names(), 0, _gamColNamesCenter[_gamColIndex], 0,
                numBasis);
        copy2DArray(oneGAMCol._penaltyMat, _penaltyMatCenter[_gamColIndex]);
      }
    }

    public class MSplineSmoother extends RecursiveAction {
      final Frame _predictVec;
      final int _numKnots;  // not counting knot duplication here
      final int _order;
      final double[] _knots;  // not counting knot duplication here
      final boolean _savePenaltyMat;
      final String[] _newGAMColNames;
      final int _gamColIndex; // gam column order from user input
      final int _singlePredSplineInd; // gam column index after moving tp to the back
      final int _splineType;

      public MSplineSmoother(Frame gamPred, GAMParameters parms, int gamColIndex, String[] gamColNames, double[] knots,
                             int singlePredInd) {
        _predictVec = gamPred;
        _numKnots = parms._num_knots_sorted[gamColIndex];
        _knots = knots;
        _order = parms._spline_orders_sorted[gamColIndex];
        _savePenaltyMat = parms._savePenaltyMat;
        _newGAMColNames = gamColNames;
        _gamColIndex = gamColIndex;
        _singlePredSplineInd = singlePredInd;
        _splineType = parms._bs_sorted[gamColIndex];
      }

      @Override
      protected void compute() {
        // generate GAM basis functions
        int order = _parms._spline_orders_sorted[_gamColIndex];
        int numBasis = _knots.length+order-2;
        int numBasisM1 = numBasis-1;
        int totKnots = numBasis + order;
        GenMSplineGamOneColumn oneGAMCol = new GenMSplineGamOneColumn(_parms, _knots, _gamColIndex, _predictVec,
                numBasis, totKnots);
        oneGAMCol.doAll(oneGAMCol._numBasis, Vec.T_NUM, _predictVec);
        if (_savePenaltyMat) {
          copy2DArray(oneGAMCol._penaltyMat, _penaltyMat[_gamColIndex]);
          _penaltyScale[_gamColIndex] = oneGAMCol._s_scale;
        }
        // extract generated gam columns
        Frame oneGamifiedColCenter = oneGAMCol.outputFrame(Key.make(), _newGAMColNames, null);
        for (int index=0; index<numBasis; index++)
          _gamColMeans[_gamColIndex][index] = oneGamifiedColCenter.vec(index).mean();
        oneGamifiedColCenter = oneGAMCol.centralizeFrame(oneGamifiedColCenter, 
                _predictVec.name(0)+"_"+_splineType+"_center", _parms);
        copy2DArray(oneGAMCol._ZTransp, _zTranspose[_gamColIndex]); // copy transpose(Z)
        DKV.put(oneGamifiedColCenter);
        _gamFrameKeysCenter[_gamColIndex] = oneGamifiedColCenter._key;
        System.arraycopy(oneGamifiedColCenter.names(), 0, _gamColNamesCenter[_gamColIndex], 0,
                numBasisM1);
        double[][] transformedPenalty = ArrayUtils.multArrArr(ArrayUtils.multArrArr(oneGAMCol._ZTransp,
                oneGAMCol._penaltyMat), ArrayUtils.transpose(oneGAMCol._ZTransp));  // transform penalty as zt*S*z
        copy2DArray(transformedPenalty, _penaltyMatCenter[_gamColIndex]);
        System.arraycopy(oneGamifiedColCenter.names(), 0, _gamColNamesCenter[_gamColIndex], 0,
                numBasisM1);
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
      final int _gamColIndex;
      final int _singlePredSplineInd;
      
      public CubicSplineSmoother(Frame predV, GAMParameters parms, int gamColIndex, String[] gamColNames, double[] knots,
                                 int csInd) {
        _predictVec = predV;
        _numKnots = parms._num_knots_sorted[gamColIndex];
        _numKnotsM1 = _numKnots-1;
        _splineType = parms._bs_sorted[gamColIndex];
        _savePenaltyMat = parms._savePenaltyMat;
        _newColNames = gamColNames;
        _knots = knots;
        _parms = parms;
        _gamColIndex = gamColIndex;
        _singlePredSplineInd = csInd;
      }

      @Override
      protected void compute() {
        GenCSSplineGamOneColumn genOneGamCol = new GenCSSplineGamOneColumn(_splineType, _numKnots,
                _knots, _predictVec).doAll(_numKnots, Vec.T_NUM, _predictVec);
        if (_savePenaltyMat) {                                  // only save this for debugging
          copy2DArray(genOneGamCol._penaltyMat, _penaltyMat[_gamColIndex]); // copy penalty matrix
          _penaltyScale[_gamColIndex] = genOneGamCol._s_scale;  // penaltyMat is scaled by 1/_s_scale
        }
        Frame oneAugmentedColumnCenter = genOneGamCol.outputFrame(Key.make(), _newColNames,
                null);  // one gamified frame
        for (int index = 0; index < _numKnots; index++)
          _gamColMeans[_gamColIndex][index] = oneAugmentedColumnCenter.vec(index).mean();
        oneAugmentedColumnCenter = genOneGamCol.centralizeFrame(oneAugmentedColumnCenter,
                _predictVec.name(0) + "_" + _splineType + "_center_cs_", _parms);
        copy2DArray(genOneGamCol._ZTransp, _zTranspose[_gamColIndex]); // copy transpose(Z)
        double[][] transformedPenalty = ArrayUtils.multArrArr(ArrayUtils.multArrArr(genOneGamCol._ZTransp,
                genOneGamCol._penaltyMat), ArrayUtils.transpose(genOneGamCol._ZTransp));  // transform penalty as zt*S*z
        copy2DArray(transformedPenalty, _penaltyMatCenter[_gamColIndex]);
        _gamFrameKeysCenter[_gamColIndex] = oneAugmentedColumnCenter._key;
        DKV.put(oneAugmentedColumnCenter);
        System.arraycopy(oneAugmentedColumnCenter.names(), 0, _gamColNamesCenter[_gamColIndex], 0,
                _numKnotsM1);
        copy2DArray(genOneGamCol._bInvD, _binvD[_singlePredSplineInd]);
      }
    }

    void addGAM2Train() {
      final int numGamFrame = _parms._gam_columns.length; // number of smoothers to generate
      RecursiveAction[] generateGamColumn = new RecursiveAction[numGamFrame];
      int thinPlateInd = 0;
      int singlePredictorSmootherInd = 0;
      Frame trainFrame = _parms.train();
      for (int index = 0; index < numGamFrame; index++) { // generate smoothers/splines
        final Frame predictVec = prepareGamVec(index, _parms, trainFrame);// extract predictors from frame
        // numKnots for M or I-spline will be the number of basis
        final int numKnots = _parms._bs_sorted[index] == IS_SPLINE_TYPE || _parms._bs_sorted[index] == MS_SPLINE_TYPE ? 
                _parms._num_knots_sorted[index] + _parms._spline_orders_sorted[index] - 2 : 
                _parms._num_knots_sorted[index];
        final int numKnotsM1 = numKnots - 1;
        if (_parms._bs_sorted[index] == TP_SPLINE_TYPE) {  // for TP splines
          final int kPlusM = _parms._num_knots_sorted[index]+_parms._M[thinPlateInd];
          _gamColNames[index] = new String[kPlusM];
          _gamColNamesCenter[index] = new String[numKnotsM1];
          _gamColMeans[index] = new double[kPlusM];
          _allPolyBasisList[thinPlateInd] = new int[_parms._M[thinPlateInd]][_parms._gamPredSize[index]];
          _gamColMeansRaw[thinPlateInd] = new double[_parms._gamPredSize[index]];
          _oneOGamColStd[thinPlateInd] = new double[_parms._gamPredSize[index]];
          generateGamColumn[index] = new ThinPlateRegressionSmootherWithKnots(predictVec, _parms, index, _knots[index],
                  thinPlateInd++);
        }  else {  // for single predictor GAM columns
          _gamColNames[index] = generateGamColNames(index, _parms);
          _gamColMeans[index] = new double[numKnots];
          if (_parms._bs_sorted[index] == CS_SPLINE_TYPE) { // cs spline
            _gamColNamesCenter[index] = new String[numKnotsM1];
            generateGamColumn[index] = new CubicSplineSmoother(predictVec, _parms, index, _gamColNames[index],
                    _knots[index][0], singlePredictorSmootherInd++);
          } else if (_parms._bs_sorted[index] == IS_SPLINE_TYPE){ // I-splines
            _gamColNamesCenter[index] = new String[numKnots];
            generateGamColumn[index] = new ISplineSmoother(predictVec, _parms, index, _gamColNames[index],
                    _knots[index][0], singlePredictorSmootherInd++);
          } else if (_parms._bs_sorted[index] == MS_SPLINE_TYPE){  // M-spline here
            _gamColNamesCenter[index] = new String[numKnotsM1];
            generateGamColumn[index] = new MSplineSmoother(predictVec, _parms, index, _gamColNames[index], 
                  _knots[index][0], singlePredictorSmootherInd++);
          } else 
            throw new NotImplementedException(SPLINENOTIMPL);
        }
      }
      ForkJoinTask.invokeAll(generateGamColumn);
      if (_iSplineNum > 0 && !_parms._betaConstraintsOff) { // set up coefficient constraints >= 0 or <= 0 for I-splines
        Frame constraintF = genConstraints();
        Scope.track(constraintF);
        if (_parms._beta_constraints != null) {
          DKV.put(constraintF);
          Frame origConstraints = DKV.getGet(_parms._beta_constraints);
          String tree = "(rbind "+origConstraints.getKey().toString()+" "+constraintF.getKey().toString()+" )";
          Val val = Rapids.exec(tree);
          Frame newConstraints = new Frame(val.getFrame());
          DKV.put(newConstraints);
          Scope.track(newConstraints);
          _parms._beta_constraints = newConstraints._key;
        } else {
          _parms._beta_constraints = constraintF._key;
          DKV.put(constraintF);
        }
      }
    }

    /**
     * For all gamified columns with I-spline, put in beta constraints to make sure the coefficients are non-negative
     * or non-positive.  This will ensure contribution from I-splines are either monontonically increasing or 
     * decreasing.
     */
    public Frame genConstraints() {
      int numGamCols = _parms._gam_columns.length;
      String[] colNames = new String[]{"names", "lower_bounds", "upper_bounds"};
      Vec.VectorGroup vg = Vec.VectorGroup.VG_LEN1;
      List<String> iSplineColNames = new ArrayList<>();
      List<Double> upperBList = new ArrayList<>();
      List<Double> lowerBList = new ArrayList<>();
      for (int index=0; index<numGamCols; index++) {
        if (_parms._bs_sorted[index] == IS_SPLINE_TYPE) { // I-splines
          int numCols = _gamColNamesCenter[index].length;
          iSplineColNames.addAll(Stream.of(_gamColNamesCenter[index]).collect(Collectors.toList()));
          if (_parms._splines_non_negative_sorted[index]) { // monotonically increasing
            upperBList.addAll(DoubleStream.generate(()->Double.POSITIVE_INFINITY ).limit(numCols).boxed().collect(Collectors.toList()));
            lowerBList.addAll(DoubleStream.generate(()->0.0).limit(numCols).boxed().collect(Collectors.toList()));
          } else {  // monotonically decreasing
            upperBList.addAll(DoubleStream.generate(()->0.0).limit(numCols).boxed().collect(Collectors.toList()));
            lowerBList.addAll(DoubleStream.generate(()->Double.NEGATIVE_INFINITY).limit(numCols).boxed().collect(Collectors.toList()));
          }
        }
      }
      int numConstraints = iSplineColNames.size();
      if (numConstraints > 0) {
        String[] constraintNames = iSplineColNames.stream().toArray(String[]::new);
        double[] lowerBounds = lowerBList.stream().mapToDouble(Double::doubleValue).toArray();
        double[] upperBounds = upperBList.stream().mapToDouble(Double::doubleValue).toArray();
        Vec gamNames = Scope.track(Vec.makeVec(constraintNames, vg.addVec()));
        Vec lowBounds = Scope.track(Vec.makeVec(lowerBounds, vg.addVec()));
        Vec upBounds = Scope.track(Vec.makeVec(upperBounds, vg.addVec()));
        return new Frame(Key.<Frame>make(), colNames, new Vec[]{gamNames, lowBounds, upBounds});
      }
      return null;
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
      
      if (valid() != null) { // transform the validation frame if present
        int[] singleGamColsCount = new int[]{_cubicSplineNum, _iSplineNum, _mSplineNum};
        _valid = rebalance(adaptValidFrame(_parms.valid(), _valid, _parms, _gamColNamesCenter, _binvD,
                        _zTranspose, _knots, _zTransposeCS, _allPolyBasisList, _gamColMeansRaw, _oneOGamColStd, singleGamColsCount),
                false, _result + ".temporary.valid");
      }
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
      final IcedHashSet<Key<Frame>> validKeys = new IcedHashSet<>();
      try {
        _job.update(0, "Adding GAM columns to training dataset...");
        if (_foldColumn != null)
          _parms._fold_column = _foldColumn;
        _dinfo = new DataInfo(_train.clone(), _valid, 1, _parms._use_all_factor_levels 
                || _parms._lambda_search, _parms._standardize ? 
                DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                _parms.missingValuesHandling() == GLMParameters.MissingValuesHandling.Skip,
                _parms.missingValuesHandling() == GLMParameters.MissingValuesHandling.MeanImputation 
                        || _parms.missingValuesHandling() == GLMParameters.MissingValuesHandling.PlugValues,
                _parms.makeImputer(), false, hasWeightCol(), hasOffsetCol(), hasFoldCol(),
                _parms.interactionSpec());
        DKV.put(_dinfo._key, _dinfo);
        if (_foldColumn != null)
          _parms._fold_column = null;
        model = new GAMModel(dest(), _parms, new GAMModel.GAMModelOutput(GAM.this, _dinfo));
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
        model.update(_job);
        fillOutGAMModel(glmModel, model); // build up GAM model by copying over results in glmModel
        // build GAM Model Metrics
        _job.update(0, "Scoring training frame");
        scoreGenModelMetrics(model, glmModel,train(), true); // score training dataset and generate model metrics
        if (valid() != null) {
          scoreGenModelMetrics(model, glmModel, valid(), false); // score validation dataset and generate model metrics
        }
      } catch(Gram.NonSPDMatrixException exception) {
        throw new Gram.NonSPDMatrixException("Consider enable lambda_search, decrease scale parameter value for TP " +
                "smoothers, \ndisable scaling for TP penalty matrics, or not use thin plate regression smoothers at all.");
      } finally {
        try {
          final List<Key> keep = new ArrayList<>();
          if (model != null) {
            if (_parms._keep_gam_cols) {
              keepFrameKeys(keep, newTFrame._key);
            } else {
              DKV.remove(newTFrame._key);
            }
            if (_cvOn) {
              if (_parms._keep_cross_validation_predictions) {
                keepFrameKeys(keep, model._output._cross_validation_holdout_predictions_frame_id);
                for (int fInd = 0; fInd < _glmNFolds; fInd++)
                  keepFrameKeys(keep, model._output._cross_validation_predictions[fInd]);
              }
              if (_parms._keep_cross_validation_fold_assignment)
                keepFrameKeys(keep, model._output._cross_validation_fold_assignment_frame_id);
            }
          }
          if (_dinfo != null)
            _dinfo.remove();

          if (newValidFrame != null && validKeys != null) {
            keepFrameKeys(keep, newValidFrame._key);  // save valid frame keys for scoring later
            validKeys.addIfAbsent(newValidFrame._key);   // save valid frame keys from folds to remove later
            model._validKeys = validKeys;  // move valid keys here to model._validKeys to be removed later
          }
          Scope.untrack(keep.toArray(new Key[keep.size()]));
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
    private void scoreGenModelMetrics(GAMModel model, GLMModel glmModel, Frame scoreFrame, boolean forTraining) {
      Frame scoringTrain = new Frame(scoreFrame);
      model.adaptTestForTrain(scoringTrain, true, true);
      Frame scoredResult = model.score(scoringTrain);
      scoredResult.delete();
      ModelMetrics glmMetrics = forTraining ? glmModel._output._training_metrics : glmModel._output._validation_metrics;
      if (forTraining) {
        model._output.copyMetrics(model, scoringTrain, forTraining, glmMetrics);
        Log.info("GAM[dest=" + dest() + "]" + model._output._training_metrics.toString());
      } else {
        model._output.copyMetrics(model, scoringTrain, forTraining, glmMetrics);
        Log.info("GAM[dest=" + dest() + "]" + model._output._validation_metrics.toString());
      }
    }

    GLMModel buildGLMModel(GAMParameters parms, Frame trainData, Frame validFrame) {
      GLMParameters glmParam = copyGAMParams2GLMParams(parms, trainData, validFrame);  // copy parameter from GAM to GLM
      int numGamCols = _parms._gam_columns.length;
      for (int find = 0; find < numGamCols; find++) {
        if ((_parms._scale_sorted != null) && (_parms._scale_sorted[find] != 1.0))
          _penaltyMatCenter[find] = ArrayUtils.mult(_penaltyMatCenter[find], _parms._scale_sorted[find]);
      }
      glmParam._glmType = gam;
      if (_foldColumn == null) {
        glmParam._nfolds = _glmNFolds;
      } else {
        glmParam._fold_column = _foldColumn;
        glmParam._nfolds = 0;
      }
      glmParam._fold_assignment = _foldAssignment;
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
      model._mSplineNum = _mSplineNum;
      model._iSplineNum = _iSplineNum;
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
      if (_parms._store_knot_locations)
        model._output.copyKnots(_knots, _parms._gam_columns_sorted);
      copyGLMCoeffs(glm, model, _parms, nclasses());  // copy over coefficient names and generate coefficients as beta = z*GLM_beta
      copyGLMtoGAMModel(model, glm, _parms, valid()!=null);  // copy over fields from glm model to gam model
      if (_cvOn) {
        _parms._betaConstraintsOff = true;
        copyCVGLMtoGAMModel(model, glm, _parms, _foldColumn);  // copy over fields from cross-validation
        _parms._betaConstraintsOff = false;
        _parms._nfolds = _foldColumn == null ? _glmNFolds : 0;  // restore original cross-validation parameter values
        _parms._fold_assignment = _foldAssignment;
        _parms._fold_column = _foldColumn;
      }
    }

    public GLMParameters copyGAMParams2GLMParams(GAMParameters parms, Frame trainData, Frame valid) {
      GLMParameters glmParam = new GLMParameters();
      List<String> gamOnlyList = Arrays.asList(
              "_num_knots", "_gam_columns", "_bs", "_scale", "_train",
              "_saveZMatrix", "_saveGamCols", "_savePenaltyMat"
      );
      Field[] field1 = GAMParameters.class.getDeclaredFields();
      setParamField(parms, glmParam, false, field1, gamOnlyList);
      Field[] field2 = Model.Parameters.class.getDeclaredFields();
      setParamField(parms, glmParam, true, field2, gamOnlyList);
      glmParam._train = trainData._key;
      glmParam._valid = valid==null?null:valid._key;
      glmParam._nfolds = _glmNFolds; // will do cv in GLM and not in GAM
      glmParam._fold_assignment = _foldAssignment;
      return glmParam;
    }
  }
}
