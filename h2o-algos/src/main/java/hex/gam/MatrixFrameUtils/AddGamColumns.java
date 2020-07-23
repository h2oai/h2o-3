package hex.gam.MatrixFrameUtils;

import hex.gam.GamSplines.CubicRegressionSplines;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.ArrayUtils;

import static hex.genmodel.algos.gam.GamUtilsCubicRegression.*;

/**
 * Given a Frame, the class will add all the gam columns to the end of the Frame right before
 * the response column if it exists
 */
public class AddGamColumns extends MRTask<AddGamColumns> {
  double[][][] _binvD;
  double[][] _knotsMat;
  double[][][] _ztransp;
  int[] _numKnots;
  public int _numGAMcols;
  public int _gamCols2Add = 0;
  double[] _vmax;
  double[] _vmin;
  int[] _gamColsOffsets;
  Frame _gamFrame;


  public AddGamColumns(double[][][] binvD, double[][][] ztransp, double[][] knotsMat, int[] numKnots, 
                       Frame gamColFrames) {
    _binvD = binvD;
    _knotsMat = knotsMat;
    _numKnots = numKnots;
    _numGAMcols = numKnots.length;
    _vmax = MemoryManager.malloc8d(_numGAMcols);
    _vmin = MemoryManager.malloc8d(_numGAMcols);
    _gamColsOffsets = MemoryManager.malloc4(_numGAMcols);
    _gamFrame = gamColFrames; // contain predictor columns, response column
    _ztransp = ztransp;
    int firstOffset = 0;
    for (int ind = 0; ind < _numGAMcols; ind++) {
      _vmax[ind] = gamColFrames.vec(ind).max();
      _vmin[ind] = gamColFrames.vec(ind).min();
      _gamCols2Add += _numKnots[ind]-1; // minus one from centering
      _gamColsOffsets[ind] += firstOffset;
      firstOffset += _numKnots[ind]-1;
    }
  }

  @Override
  public void map(Chunk[] chk, NewChunk[] newChunks) {
    CubicRegressionSplines[] crSplines = new CubicRegressionSplines[_numGAMcols];
    double[][] basisVals = new double[_numGAMcols][];
    double[][] basisValsCenter = new double[_numGAMcols][];
    for (int gcolInd = 0; gcolInd < _numGAMcols; gcolInd++) { // prepare splines
      crSplines[gcolInd] = new CubicRegressionSplines(_numKnots[gcolInd], _knotsMat[gcolInd]);
      basisValsCenter[gcolInd] = MemoryManager.malloc8d(_numKnots[gcolInd]-1); // with centering, it is one less
      basisVals[gcolInd] = MemoryManager.malloc8d(_numKnots[gcolInd]); // without centering
    }
    int chkLen = chk[0]._len;
    for (int rInd = 0; rInd < chkLen; rInd++) { // go through each row
      for (int cInd = 0; cInd < _numGAMcols; cInd++) {  // add each column
        generateOneGAMcols(cInd, _gamColsOffsets[cInd], basisVals[cInd], basisValsCenter[cInd], _binvD[cInd], crSplines[cInd],
                chk[cInd].atd(rInd), newChunks);
      }
    }
  }

  public void generateOneGAMcols(int colInd, int colOffset, double[] basisVals, double[] basisValCenter, 
                                 double[][] bInvD, CubicRegressionSplines splines, double xval, NewChunk[] newChunks) {
    int centerKnots = _numKnots[colInd]-1;
    if (!Double.isNaN(xval)) {
      int binIndex = locateBin(xval, splines._knots); // location to update
      // update from F matrix F matrix = [0;invB*D;0] and c functions
      updateFMatrixCFunc(basisVals, xval, binIndex, splines._knots, splines._hj, bInvD);
      // update from a+ and a- functions
      updateAFunc(basisVals, xval, binIndex, splines._knots, splines._hj);
      // add centering
      basisValCenter = ArrayUtils.multArrVec(_ztransp[colInd], basisVals, basisValCenter);
      // copy updates to the newChunk row
      for (int colIndex = 0; colIndex < centerKnots; colIndex++) {
        newChunks[colIndex + colOffset].addNum(basisValCenter[colIndex]);
      }
    } else {  // set NaN
      for (int colIndex = 0; colIndex < centerKnots; colIndex++)
        newChunks[colIndex + colOffset].addNum(Double.NaN);
    }
  }
}
