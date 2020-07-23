package hex.gam.MatrixFrameUtils;

import hex.DataInfo;
import hex.gam.GAMModel.GAMParameters;
import hex.gam.GamSplines.CubicRegressionSplines;
import hex.genmodel.algos.gam.GamUtilsCubicRegression;
import hex.glm.GLMModel.GLMParameters.MissingValuesHandling;
import hex.util.LinearAlgebraUtils.BMulInPlaceTask;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;

import static hex.genmodel.algos.gam.GamUtilsCubicRegression.locateBin;

public class GenerateGamMatrixOneColumn extends MRTask<GenerateGamMatrixOneColumn> {
  int _splineType;
  public int _numKnots;      // number of knots
  public double[][] _bInvD;  // store inv(B)*D
  Frame _gamX;
  double[] _u; // store transpose(X)*1, sum across rows per column
  public double[][] _ZTransp;  // store Z matrix transpose
  public double[][] _penaltyMat;  // store penalty matrix
  public double[] _knots;
  double[] _maxAbsRowSum; // store maximum row sum
  double _s_scale;

  public GenerateGamMatrixOneColumn(int splineType, int numKnots, double[] knots, Frame gamx, boolean standardize) {
    _splineType = splineType;
    _numKnots = numKnots;
     CubicRegressionSplines crSplines = new CubicRegressionSplines(numKnots, knots);
    _bInvD = crSplines.gen_BIndvD(crSplines._hj);
    _penaltyMat = crSplines.gen_penalty_matrix(crSplines._hj, _bInvD);
    _gamX = gamx;
    _knots = knots;
  }

  @Override
  public void map(Chunk[] chk, NewChunk[] newGamCols) {
    _maxAbsRowSum = new double[_gamX.vec(0).nChunks()];
    int cIndex = chk[0].cidx();
    _maxAbsRowSum[cIndex] = Double.NEGATIVE_INFINITY;
    int chunkRows = chk[0].len(); // number of rows in chunk
    CubicRegressionSplines crSplines = new CubicRegressionSplines(_numKnots, _knots); // not iced, must have own
    double[] basisVals = new double[_numKnots];
    for (int rowIndex=0; rowIndex < chunkRows; rowIndex++) {
      double gamRowSum = 0.0;
      // find index of knot bin where row value belongs to
      double xval = chk[0].atd(rowIndex);
      int binIndex = locateBin(xval,_knots); // location to update
      // update from F matrix F matrix = [0;invB*D;0] and c functions
      GamUtilsCubicRegression.updateFMatrixCFunc(basisVals, xval, binIndex, _knots, crSplines._hj, _bInvD);
      // update from a+ and a- functions
      GamUtilsCubicRegression.updateAFunc(basisVals, xval, binIndex, _knots, crSplines._hj);
      // copy updates to the newChunk row
      for (int colIndex = 0; colIndex < _numKnots; colIndex++) {
        newGamCols[colIndex].addNum(basisVals[colIndex]);
        gamRowSum += Math.abs(basisVals[colIndex]);
      }
      if (gamRowSum > _maxAbsRowSum[cIndex])
        _maxAbsRowSum[cIndex] = gamRowSum;
    }
  }

  @Override
  public void reduce(GenerateGamMatrixOneColumn other) {
    ArrayUtils.add(_maxAbsRowSum, other._maxAbsRowSum);
  }

  @Override
  public void postGlobal() {  // scale the _penalty function according to R
    double tempMaxValue = ArrayUtils.maxValue(_maxAbsRowSum);
    _s_scale = tempMaxValue*tempMaxValue/ArrayUtils.rNorm(_penaltyMat, 'i');
    ArrayUtils.mult(_penaltyMat, _s_scale);
    _s_scale = 1/ _s_scale;
  }

  public void generateZtransp(Frame gamX) {
    _u = new double[_numKnots];
    for (int cind = 0; cind < _numKnots; cind++)
      _u[cind] = gamX.vec(cind).mean();
    _ZTransp = new double[_numKnots - 1][_numKnots];
    double mag = ArrayUtils.innerProduct(_u, _u);
    _u[0] = _u[0] - (_u[0] > 0 ? -1 : 1) * Math.sqrt(mag); // form a = u-v and stored back in _u
    double twoOmagSq = 2.0 / ArrayUtils.innerProduct(_u, _u);
    for (int rowIndex = 0; rowIndex < _numKnots; rowIndex++) {  // form Z matrix transpose here
      for (int colIndex = 0; colIndex < _numKnots; colIndex++) {  // skip the first column
        if (colIndex > 0)
          _ZTransp[colIndex - 1][rowIndex] = (colIndex == rowIndex ? 1 : 0) - _u[rowIndex] * _u[colIndex] * twoOmagSq;
      }
    }
  }
  
  public Frame centralizeFrame(Frame fr, String colNameStart, GAMParameters parms) {
    generateZtransp(fr);
    int numCols = fr.numCols();
    int ncolExp = numCols-1;
    DataInfo frInfo = new DataInfo(fr, null, 0, false,  DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
            MissingValuesHandling.Skip == parms._missing_values_handling,
            (parms._missing_values_handling == MissingValuesHandling.MeanImputation) ||
                    (parms._missing_values_handling == MissingValuesHandling.PlugValues), parms.makeImputer(),
            false, false, false, false, null);
    for (int index=0; index < ncolExp; index++) {
      fr.add(colNameStart+"_"+index, fr.anyVec().makeZero()); // add numCols-1 columns to fr
    }
    new BMulInPlaceTask(frInfo, _ZTransp, numCols, false).doAll(fr);
    for (int index=0; index < numCols; index++) { // remove the original gam columns
      Vec temp = fr.remove(0);
      temp.remove();
    }
    return fr;
  }
}
