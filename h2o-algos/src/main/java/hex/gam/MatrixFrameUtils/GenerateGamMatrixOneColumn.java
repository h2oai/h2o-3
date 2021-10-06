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
  public int _initChunks;
  double[] _u; // store transpose(X)*1, sum across rows per column
  public double[][] _ZTransp;  // store Z matrix transpose
  public double[][] _penaltyMat;  // store penalty matrix
  public double[] _knots;
  double[] _maxAbsRowSum; // store maximum row sum
  public double _s_scale;

  public GenerateGamMatrixOneColumn(int splineType, int numKnots, double[] knots, Frame gamx) {
    _splineType = splineType;
    _numKnots = numKnots;
     CubicRegressionSplines crSplines = new CubicRegressionSplines(numKnots, knots);
    _bInvD = crSplines.gen_BIndvD(crSplines._hj);
    _penaltyMat = crSplines.gen_penalty_matrix(crSplines._hj, _bInvD);
    _initChunks = gamx.vec(0).nChunks();
    _knots = knots;
  }

  @Override
  public void map(Chunk[] chk, NewChunk[] newGamCols) {
    _maxAbsRowSum = new double[_initChunks];
    int cIndex = chk[0].cidx();
    _maxAbsRowSum[cIndex] = Double.NEGATIVE_INFINITY;
    int chunkRows = chk[0].len(); // number of rows in chunk
    CubicRegressionSplines crSplines = new CubicRegressionSplines(_numKnots, _knots); // not iced, must have own
    double[] basisVals = new double[_numKnots];
    for (int rowIndex = 0; rowIndex < chunkRows; rowIndex++) {
      double gamRowSum = 0.0;
      // find index of knot bin where row value belongs to
      if (chk[1].atd(rowIndex) != 0) {  // consider weight column value during gamification.  If 0, insert rows of zeros.
        double xval = chk[0].atd(rowIndex);
        if (Double.isNaN(xval)) { // fill with NaN
          for (int colIndex = 0; colIndex < _numKnots; colIndex++)
            newGamCols[colIndex].addNum(Double.NaN);
        } else {
          int binIndex = locateBin(xval, _knots); // location to update
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
      } else {  // zero weight, fill entries with zeros and skip all that processing
        for (int colIndex = 0; colIndex < _numKnots; colIndex++)
          newGamCols[colIndex].addNum(0.0);
      }
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
    _s_scale = 1.0/ _s_scale;
  }

  public static double[][] generateZTransp(Frame gamX, int numKnots) {
    double[] u = new double[numKnots];
    for (int cind = 0; cind < numKnots; cind++)
      u[cind] = gamX.vec(cind).mean();
    double[][] ZTransp = new double[numKnots - 1][numKnots];
    double mag = ArrayUtils.innerProduct(u, u);
    u[0] = u[0] - (u[0] > 0 ? -1 : 1) * Math.sqrt(mag); // form a = u-v and stored back in _u
    double twoOmagSq = 2.0 / ArrayUtils.innerProduct(u, u);
    for (int rowIndex = 0; rowIndex < numKnots; rowIndex++) {  // form Z matrix transpose here
      for (int colIndex = 0; colIndex < numKnots; colIndex++) {  // skip the first column
        if (colIndex > 0)
          ZTransp[colIndex - 1][rowIndex] = (colIndex == rowIndex ? 1 : 0) - u[rowIndex] * u[colIndex] * twoOmagSq;
      }
    }
    return ZTransp;
  }
  
  public Frame centralizeFrame(Frame fr, String colNameStart, GAMParameters parms) {
    _ZTransp = generateZTransp(fr, _numKnots);
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
