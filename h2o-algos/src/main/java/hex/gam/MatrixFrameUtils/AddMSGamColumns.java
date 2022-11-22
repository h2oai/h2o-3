package hex.gam.MatrixFrameUtils;

import hex.genmodel.algos.gam.MSplines;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.ArrayUtils;

import static hex.genmodel.algos.gam.GamMojoModel.MS_SPLINE_TYPE;

/**
 * This task will gamified all gam predictors with bs=3.  It will not generate the penalty matrix or the Z matrix.  
 * Those are assumed to be generated already earlier.
 */
public class AddMSGamColumns extends MRTask<AddMSGamColumns> {
  double[][][] _knotsMat; // knots without duplication for M-spline
  int[] _numKnots;
  int[] _numBasis;
  public int _numGAMCols; // count number of M-Spline gam columns
  int[] _gamColsOffsets;
  Frame _gamFrame;
  int[] _bs;              // for M-spline only
  int[] _splineOrder;     // for M-spline only
  int _totGamifiedCols=0;
  public int _totGamifiedColCentered=0;
  final double[][][] _ztransp;

  public AddMSGamColumns(double[][][] knotsMat, double[][][] ztransp, int[] numKnot, int[] bs, int[] splineOrder,
                         Frame gamColFrames) {
    _gamFrame = gamColFrames;
    _numGAMCols = gamColFrames.numCols();
    _gamColsOffsets = MemoryManager.malloc4(_numGAMCols);
    _knotsMat = new double[_numGAMCols][][];
    _bs = new int[_numGAMCols];
    _splineOrder = new int[_numGAMCols];
    _numKnots = new int[_numGAMCols];
    _numBasis = new int[_numGAMCols];
    _ztransp = new double[_numGAMCols][][];
    int totGamCols = bs.length;
    int countMS = 0;
    int offset = 0;
    for (int index=0; index<totGamCols; index++) {
      if (bs[index]==MS_SPLINE_TYPE) {
        int numBasis = numKnot[index]+splineOrder[index]-2;
        int numBasisM1 = numBasis - 1;
        _totGamifiedCols += numBasis;
        _totGamifiedColCentered += numBasisM1;
        _knotsMat[countMS] = knotsMat[index];
        _bs[countMS] = bs[index];
        _numKnots[countMS] = numKnot[index];
        _numBasis[countMS] = numBasis;
        _splineOrder[countMS] = splineOrder[index];
        _ztransp[countMS] = ztransp[index];
        _gamColsOffsets[countMS++] = offset;
        offset += numBasisM1;   // minus 1 for centering
      }
    }
  }

  @Override
  public void map(Chunk[] chk, NewChunk[] newChunks) {
    MSplines[] msBasis = new MSplines[_numGAMCols];
    double[][] basisVals = new double[_numGAMCols][];
    double[][] basisValsCenter = new double[_numGAMCols][];

    for (int index=0; index<_numGAMCols; index++) {
      msBasis[index] = new MSplines(_splineOrder[index], _knotsMat[index][0]);
      basisVals[index] = MemoryManager.malloc8d(_numBasis[index]);
      basisValsCenter[index] = MemoryManager.malloc8d(_numBasis[index]-1);
    }
    int chkLen = chk[0].len();
    for (int rInd=0; rInd<chkLen; rInd++) {
      for (int cInd=0; cInd<_numGAMCols; cInd++)
        generateOneMSGAMCols(cInd, _gamColsOffsets[cInd], basisVals[cInd], basisValsCenter[cInd], msBasis[cInd], chk[cInd].atd(rInd),
                newChunks);
    }
  }

  /***
   * Perform gamification of one column using I-spline basis function described in Section V of doc I.
   */
  public void generateOneMSGAMCols(int colInd, int colOffset, double[] basisVals, double[] basisValsCenter, 
                                   MSplines msBasis, double xval, NewChunk[] newChunks) {
    int numVals = _numBasis[colInd]-1;  // shrink after centralize
    if (!Double.isNaN(xval)) {
      msBasis.gamifyVal(basisVals, xval);
      basisValsCenter = ArrayUtils.multArrVec(_ztransp[colInd], basisVals, basisValsCenter);
      for (int colIndex=0; colIndex < numVals; colIndex++)
        newChunks[colIndex+colOffset].addNum(basisValsCenter[colIndex]);
    } else {
      for (int colIndex=0; colIndex < numVals; colIndex++)
        newChunks[colIndex+colOffset].addNum(Double.NaN);
    }
  }
}
