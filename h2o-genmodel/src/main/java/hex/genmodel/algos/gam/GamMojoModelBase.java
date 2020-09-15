package hex.genmodel.algos.gam;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.*;
import hex.genmodel.utils.ArrayUtils;
import hex.genmodel.utils.DistributionFamily;
import hex.genmodel.utils.LinkFunctionType;

import java.util.Map;

import static hex.genmodel.algos.gam.GamUtilsThinPlateRegression.*;
import static hex.genmodel.utils.ArrayUtils.multArray;
import static hex.genmodel.utils.ArrayUtils.nanArray;

public abstract class GamMojoModelBase extends MojoModel implements Cloneable {
  public LinkFunctionType _link_function;
  boolean _useAllFactorLevels;
  int _cats;
  int[] _catNAFills;
  int[] _catOffsets;
  int _nums;
  int _numsCenter;
  double[] _numNAFillsCenter;
  boolean _meanImputation;
  double[] _beta_no_center;
  double[] _beta_center;
  double[][] _beta_multinomial;
  double[][] _beta_multinomial_no_center; // coefficients not centered for multinomial/ordinal
  double[][] _beta_multinomial_center; // coefficients not centered for multinomial/ordinal
  int[] _spline_orders;
  int[] _spline_orders_sorted;
  DistributionFamily _family;
  String[][] _gam_columns;
  String[][] _gam_columns_sorted;
  int[] _d;
  int[] _m;
  int[] _M;
  int[] _gamPredSize;
  int _num_gam_columns;
  int[] _bs;
  int[] _bs_sorted;
  int[] _num_knots;
  int[] _num_knots_sorted;
  int[] _num_knots_sorted_minus1;
  int[] _numBasisSize;  // number of basis function sizes
  int[] _num_knots_TP;
  double[][][] _knots;
  double[][][] _binvD;
  double[][][] _zTranspose;
  double[][][] _zTransposeCS;
  String[][] _gamColNames;  // expanded gam column names
  String[][] _gamColNamesCenter;
  String[] _names_no_centering; // column names of features with no centering
  int _totFeatureSize; // Gam Algo predictors numbers that include: predictors, expanded gam columns no centered
  int _betaSizePerClass;
  int _betaCenterSizePerClass;
  double _tweedieLinkPower;
  double[][] _hj;   // difference between knot values
  int _numExpandedGamCols; // number of expanded gam columns
  int _numExpandedGamColsCenter; // number of expanded gam columns centered
  int _lastClass;
  int[][][] _allPolyBasisList;
  int _numTPCol;
  int _numCSCol;
  int _numISCol;
  // following arrays are pre-allocated to avoid repeated memory allocation per row of scoring
  int[] _tpDistzCSSize;
  boolean[] _dEven;
  double[] _constantTerms;
  double[][] _gamColMeansRaw;
  double[][] _oneOGamColStd;
  boolean _standardize;
  ISplines[] _iSplineBasis;
  
  GamMojoModelBase(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    if (_meanImputation) {
      imputeMissingWithMeans(row);  // perform imputation for each row
    }
    return gamScore0(row, preds);
  }

  void init() {
    _num_knots_sorted_minus1 = new int[_num_knots_sorted.length];
    for (int index = 0; index < _num_knots_sorted.length; index++)
      _num_knots_sorted_minus1[index] = _num_knots_sorted[index]-1;
    if (_numCSCol > 0) {
      _hj = new double[_numCSCol][];
      for (int ind = 0; ind < _numCSCol; ind++)
        _hj[ind] = ArrayUtils.eleDiff(_knots[ind][0]);
    }
    if (_numISCol > 0) {
      _numBasisSize = new int[_numISCol];
      _iSplineBasis = new ISplines[_numISCol];
      for (int ind=0; ind<_numISCol;ind++) {
        int absIndex = ind + _numCSCol;
        _numBasisSize[ind] = _num_knots_sorted[absIndex]+_spline_orders_sorted[absIndex]-2;
        _iSplineBasis[ind] = new ISplines(_spline_orders_sorted[absIndex], _knots[absIndex][0]);
      }
    }
    if (_numTPCol > 0) {
      _tpDistzCSSize = new int[_numTPCol];
      _dEven = new boolean[_numTPCol];
      _constantTerms = new double[_numTPCol];
      for (int index = 0; index < _numTPCol; index++) {
        int absIndex = index+ _numCSCol+_numISCol;
        _tpDistzCSSize[index] = _num_knots_sorted[absIndex]-_M[index];
        _dEven[index] = (_d[absIndex] % 2) == 0;
        _constantTerms[index] = calTPConstantTerm(_m[index], _d[absIndex], _dEven[index]);
      }
    }
    _lastClass = _nclasses - 1;
  }

  @Override
  public GenModel internal_threadSafeInstance() {
    try {
      GamMojoModelBase clonedMojo = (GamMojoModelBase) clone();
      clonedMojo.init();
      return clonedMojo;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  abstract double[] gamScore0(double[] row, double[] preds);
  
  private void imputeMissingWithMeans(double[] data) {
    for (int ind=0; ind < _cats; ind++)
      if (Double.isNaN(data[ind])) data[ind] = _catNAFills[ind];
      for (int ind = 0; ind < _numsCenter; ind++)
        if (Double.isNaN(data[ind + _cats])) data[ind + _cats] = _numNAFillsCenter[ind];
  }
  
  double evalLink(double val) {
    switch (_link_function) {
      case identity: return GenModel.GLM_identityInv(val);
      case logit: return GenModel.GLM_logitInv(val);
      case log: return GenModel.GLM_logInv(val);
      case inverse: return GenModel.GLM_inverseInv(val);
      case tweedie: return GenModel.GLM_tweedieInv(val, _tweedieLinkPower);
      default: throw new UnsupportedOperationException("Unexpected link function "+_link_function);
    }
  }

  // This method will read in categorical value and adjust for when useAllFactorLevels = true or false
  int readCatVal(double data, int dataIndex) {
    int ival = _useAllFactorLevels ? ((int) data) : ((int) data - 1);
    if (ival < 0)
      return -1;
    ival += _catOffsets[dataIndex];
    return ival;
  }

  // this method will generate the beta*data+intercept
  double generateEta(double[] beta, double[] data) {
    double eta = 0.0;
    int catOffsetLength = _catOffsets.length - 1;
    for (int i = 0; i < catOffsetLength; ++i) {  // take care of contribution from categorical columns
      int ival = readCatVal(data[i], i);
      if ((ival < _catOffsets[i + 1]) && (ival >= 0))
        eta += beta[ival];
    }
    int noff = _catOffsets[_cats] - _cats;
    int numColLen = beta.length - 1 - noff;
    for (int i = _cats; i < numColLen; ++i)
      eta += beta[noff + i] * data[i];
    eta += beta[beta.length - 1]; // add intercept
    return eta;
  }
  
  // check if gamificationis needed.  If all gamified column values are NaN, we need to add gamification.  Otherwise,
  // gamification is already done.
  private boolean gamificationNeeded(double[] rawData, int gamColStart) {
      for (int cind = gamColStart; cind < rawData.length; cind++)
        if (!Double.isNaN(rawData[cind])) {
          return false;
        }
    return true;  
  }

  int addCSGamification(final RowData rowData, int cind, int dataIndEnd, double[] dataWithGamifiedColumns) {
    Object dataObject = rowData.get(_gam_columns_sorted[cind][0]); // read predictor column
    double gamColData = Double.NaN;
    if (dataObject == null) {  // NaN, skip column gamification
      return dataIndEnd;
    } else { // can only test this with Python/R client
      gamColData = (dataObject instanceof String) ? Double.parseDouble((String) dataObject) : (double) dataObject;
    }
    double[] basisVals = new double[_num_knots_sorted[cind]];
    double[] basisValsCenter = new double[_num_knots_sorted_minus1[cind]];
    GamUtilsCubicRegression.expandOneGamCol(gamColData, _binvD[cind], basisVals, _hj[cind], _knots[cind][0]);
    multArray(basisVals, _zTranspose[cind], basisValsCenter);
    System.arraycopy(basisValsCenter, 0, dataWithGamifiedColumns, dataIndEnd, _num_knots_sorted_minus1[cind]); // copy expanded gam to rawData
    return dataIndEnd;
  }

  int addISGamification(final RowData rowData, int cind, int csCounter, int dataIndEnd, double[] dataWithGamifiedColumns) {
    Object dataObject = rowData.get(_gam_columns_sorted[cind][0]); // read predictor column
    double gamColData = Double.NaN;
    if (dataObject == null)  // NaN, skip column gamification
      return dataIndEnd;
    else // can only test this with Python/R client
      gamColData = (dataObject instanceof String) ? Double.parseDouble((String) dataObject) : (double) dataObject;

    double[] basisVals = new double[_numBasisSize[csCounter]];
    _iSplineBasis[csCounter].gamifyVal(basisVals, gamColData);
    System.arraycopy(basisVals, 0, dataWithGamifiedColumns, dataIndEnd, _numBasisSize[csCounter]); // copy expanded gam to rawData
    return dataIndEnd;
  }
  
  // this method will add to each data row the expanded gam columns with centering
  double[] addExpandGamCols(double[] rawData, final RowData rowData) { // add all expanded gam columns here
    int dataIndEnd = _nfeatures - _numExpandedGamColsCenter; // starting index to fill out the rawData
    if (!gamificationNeeded(rawData, dataIndEnd))
       return rawData;     // already contain gamified columns.  Nothing needs to be done.
    // add expanded gam columns to rowData
    double[] dataWithGamifiedColumns = nanArray(_nfeatures);  // store gamified columns
    System.arraycopy(rawData, 0, dataWithGamifiedColumns, 0, dataIndEnd);
    int tpCounter = 0;
    int isCounter = 0;
    for (int cind = 0; cind < _num_gam_columns; cind++) { // go through all gam_columns, CS and TP
      if (_bs_sorted[cind] == 0) { // to generate basis function values for cubic regression spline
        dataIndEnd = addCSGamification(rowData, cind, dataIndEnd, dataWithGamifiedColumns);
      } else if (_bs_sorted[cind] == 1) { // tp regression
        addTPGamification(rowData, cind, tpCounter, dataIndEnd, dataWithGamifiedColumns);
        tpCounter++;
      } else if (_bs_sorted[cind]==2) { // perform I-spline gamification
        addISGamification(rowData, cind, isCounter, dataIndEnd, dataWithGamifiedColumns);
        isCounter++;
      } else {
        throw new IllegalArgumentException("spline type not implemented!");
      }
      dataIndEnd += _num_knots_sorted_minus1[cind]; 
    }
    return dataWithGamifiedColumns;
  }
  
  int addTPGamification(final RowData rowData, int cind, int tpCounter, int dataIndEnd, double[] dataWithGamifiedColumns) {
    String[] gamCols = _gam_columns_sorted[cind];
    double[] gamPred = grabPredictorVals(gamCols, rowData); // grabbing multiple predictors
    if (gamPred == null) 
      return dataIndEnd;
    
    double[] tpDistance = new double[_num_knots_sorted[cind]];
    calculateDistance(tpDistance, gamPred, _num_knots_sorted[cind], _knots[cind],
            _d[cind], _m[tpCounter], _dEven[tpCounter], _constantTerms[tpCounter], _oneOGamColStd[tpCounter],
            _standardize); // calculate distance between row and knots, result in rowValues
    double[] tpDistzCS = new double[_tpDistzCSSize[tpCounter]];
    multArray(tpDistance, _zTransposeCS[tpCounter], tpDistzCS); // distance * zCS
    double[] tpPoly = new double[_M[tpCounter]];
    calculatePolynomialBasis(tpPoly, gamPred, _d[cind], _M[tpCounter],
            _allPolyBasisList[tpCounter], _gamColMeansRaw[tpCounter], _oneOGamColStd[tpCounter], _standardize);  // generate polynomial basis
    // concatenate distance zCS and poly basis.
    double[] tpDistzCSPoly = new double[_num_knots_sorted[cind]];
    double[] tpDistzCSPolyzT = new double[_num_knots_sorted_minus1[cind]];
    System.arraycopy(tpDistzCS, 0, tpDistzCSPoly, 0, tpDistzCS.length);
    System.arraycopy(tpPoly, 0, tpDistzCSPoly, tpDistzCS.length, _M[tpCounter]);
    multArray(tpDistzCSPoly, _zTranspose[cind], tpDistzCSPolyzT);
    System.arraycopy(tpDistzCSPolyzT, 0, dataWithGamifiedColumns, dataIndEnd,
            tpDistzCSPolyzT.length);
    return dataIndEnd;
  }
  
  double[] grabPredictorVals(String[] gamCols, final RowData rowData) {
    int numCol = gamCols.length;
    double[] predVals = new double[numCol];
    for (int index = 0; index < numCol; index++) {
      Object data = rowData.get(gamCols[index]);
      if (data == null)
        return null;
      predVals[index] = (data instanceof String) ? Double.parseDouble((String) data) : (double) data;
    }
    return predVals;
  }

  @Override
  public RowToRawDataConverter makeDefaultRowConverter(Map<String, Integer> columnToOffsetIdx,
                                                       Map<Integer, CategoricalEncoder> offsetToEncoder,
                                                       EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                                                       EasyPredictModelWrapper.Config config) {
    return new GamRowToRawDataConverter(this, columnToOffsetIdx, offsetToEncoder, errorConsumer, config);
  }
}
