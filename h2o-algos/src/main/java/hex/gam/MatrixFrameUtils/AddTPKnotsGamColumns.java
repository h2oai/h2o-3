package hex.gam.MatrixFrameUtils;


import hex.gam.GamSplines.ThinPlateDistanceWithKnots;
import hex.gam.GamSplines.ThinPlatePolynomialWithKnots;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;

import static hex.gam.GAMModel.GAMParameters;
import static hex.gam.GamSplines.ThinPlateRegressionUtils.extractColNames;
import static hex.gam.GamSplines.ThinPlateRegressionUtils.genThinPlateNameStart;
import static hex.gam.MatrixFrameUtils.GamUtils.generateGamColNamesThinPlateKnots;
import static hex.gam.MatrixFrameUtils.GamUtils.prepareGamVec;
import static org.apache.commons.math3.util.CombinatoricsUtils.factorial;
import static water.util.ArrayUtils.sum;

// This class generatea all TP gamified columns
public class AddTPKnotsGamColumns {
  final double[][][] _zCS;
  final double[][][] _z;
  final int[][][] _polyBasisList;
  final int[] _numKnots;
  final int[] _d;
  final int[] _M;
  final int[] _m;
  final GAMParameters _parms;
  public final int _gamCols2Add;
  final double[][][] _knots;
  final int _numTPCols;
  final int _numCSCols;
  final double[] _constantTerms;
  final boolean[] _dEven;
  final Frame _adapted;
  public Key<Frame>[] _gamFrameKeysCenter;  // store frame keys of transformed gam columns
  
  public AddTPKnotsGamColumns(GAMParameters parms, double[][][] zcs, double[][][] z, int[][][] polyBasis, 
                              double[][][] knots, Frame fr) {
    _zCS = zcs;
    _z = z;
    _polyBasisList = polyBasis;
    _numKnots = parms._num_knots_tp;
    _d = parms._gamPredSize;
    _M = parms._M;
    _m = parms._m;
    _parms = parms;
    _gamCols2Add = sum(_numKnots) - _numKnots.length;
    _knots = knots;
    _numTPCols = _M.length;
    _numCSCols = parms._gam_columns_sorted.length - _numTPCols;
    _dEven = new boolean[_numTPCols];
    _constantTerms = new double[_numTPCols];
    _gamFrameKeysCenter = new Key[_numTPCols];
    for (int index = 0; index < _numTPCols; index++) {
      _dEven[index] = (_parms._gamPredSize[index] % 2) == 0;
      if (_dEven[index])
        _constantTerms[index] = Math.pow(-1, _m[index]+1+_d[index]/2.0)/(Math.pow(2, _m[index]-1)*Math.pow(Math.PI, 
                _d[index]/2.0)*factorial(_m[index]-_d[index]/2));
      else
        _constantTerms[index] = Math.pow(-1, _m[index])*_m[index]/(factorial(2*_m[index])*Math.pow(Math.PI, 
                (_d[index]-1)/2.0));
    }
    _adapted = fr;
  }

  public void addTPGamCols(double[][] gamColMeansRaw, double[][] oneOColStd) {
    for (int index = 0; index < _numTPCols; index++) { // generate smoothers/splines for each gam smoother
      final int offsetIndex = index + _numCSCols;
      final Frame predictVec = prepareGamVec(offsetIndex, _parms, _adapted);  // extract predictors from training frame
      ApplyTPRegressionSmootherWithKnots addSmoother = new ApplyTPRegressionSmootherWithKnots(predictVec, _parms, offsetIndex,
              _knots[offsetIndex], index, _zCS[index], _z[offsetIndex], _polyBasisList[index], gamColMeansRaw[index],
              oneOColStd[index]);
      addSmoother.applySmoothers();
    }
  }

  public class ApplyTPRegressionSmootherWithKnots {
    final Frame _predictVec;
    final int _numKnots;
    final int _numKnotsM1;
    final int _numKnotsMM;  // store k-M
    final double[][] _knots;
    final GAMParameters _parms;
    final int _gamColIndex;
    final int _thinPlateGamColIndex;
    final int _numPred; // number of predictors == d
    final int _M;
    final double[][] _zCST;
    final double[][] _zT;
    final int[][] _polyBasisList;
    final double[] _gamColMeanRaw;
    final double[] _oneOColStd;

    public ApplyTPRegressionSmootherWithKnots(Frame predV, GAMParameters parms, int gamColIndex, double[][] knots,
                                              int thinPlateInd, double[][] zCST, double[][] zT, int[][] polyBasis, 
                                              double[] gamColMeanRaw, double[] oneOColStd) {
      _predictVec  = predV;
      _knots = knots;
      _numKnots = knots[0].length;
      _numKnotsM1 = _numKnots-1;
      _parms = parms;
      _gamColIndex = gamColIndex;
      _thinPlateGamColIndex = thinPlateInd;
      _numPred = parms._gam_columns_sorted[gamColIndex].length;
      _M = _parms._M[_thinPlateGamColIndex];
      _numKnotsMM = _numKnots-_M;
      _zCST = zCST;
      _zT = zT;
      _polyBasisList = polyBasis;
      _gamColMeanRaw = gamColMeanRaw;
      _oneOColStd = oneOColStd;
    }
    
    void applySmoothers() {
      ThinPlateDistanceWithKnots distanceMeasure =
              new ThinPlateDistanceWithKnots(_knots, _numPred, _oneOColStd, _parms._standardize).doAll(_numKnots, 
                      Vec.T_NUM, _predictVec);                          // Xnmd in 3.1
      String colNameStub = genThinPlateNameStart(_parms, _gamColIndex); // gam column names before processing
      String[] gamColNames = generateGamColNamesThinPlateKnots(_gamColIndex, _parms, _polyBasisList, colNameStub);
      String[] distanceColNames = extractColNames(gamColNames, 0, 0, _numKnots);
      String[] polyNames = extractColNames(gamColNames, _numKnots, 0, _M);
      Frame thinPlateFrame = distanceMeasure.outputFrame(Key.make(), distanceColNames, null);

      thinPlateFrame = ThinPlateDistanceWithKnots.applyTransform(thinPlateFrame, colNameStub
              +"CS_", _parms, _zCST, _numKnotsMM);        // generate Xcs as in 3.3
      ThinPlatePolynomialWithKnots thinPlatePoly = new ThinPlatePolynomialWithKnots(_numPred, _polyBasisList,
              _gamColMeanRaw, _oneOColStd, _parms._standardize).doAll(_M,
              Vec.T_NUM, _predictVec);                    // generate polynomial basis T as in 3.2
      Frame thinPlatePolyBasis = thinPlatePoly.outputFrame(null, polyNames, null);
      thinPlateFrame.add(thinPlatePolyBasis.names(), thinPlatePolyBasis.removeAll());         // concatenate Xcs and T
      thinPlateFrame = ThinPlateDistanceWithKnots.applyTransform(thinPlateFrame, colNameStub+"center_",
              _parms, _zT, _numKnotsM1);                  // generate Xz as in 3.4
      _gamFrameKeysCenter[_thinPlateGamColIndex] = thinPlateFrame._key;
      DKV.put(thinPlateFrame);
    }
  }
}
