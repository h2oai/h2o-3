package hex.gam.MatrixFrameUtils;

import hex.genmodel.algos.gam.ISplines;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

/**
 * class to gamified all gam_columns with bs set to 2.  For details regarding I-spline, please refer to doc in 
 * JIRA: https://h2oai.atlassian.net/browse/PUBDEV-8398
 */
public class AddISGamColumns extends MRTask<AddISGamColumns> {
    double[][][] _knotsMat; // knots without duplication for I-spline
    int[] _numKnots;
    int[] _numBasis;
    public int _numGAMCols; // count number of I-Spline gam columns
    int[] _gamColsOffsets;
    Frame _gamFrame;
    int[] _bs;              // for I-spline only
    int[] _splineOrder;     // for I-spline only
    int _totGamifiedCols=0;
    public int _totGamifiedColCentered=0;
    
    public AddISGamColumns(double[][][] knotsMat, int[] numKnot, int[] bs, int[] splineOrder,
                           Frame gamColFrames) {
        _gamFrame = gamColFrames;
        _numGAMCols = gamColFrames.numCols();
        _gamColsOffsets = MemoryManager.malloc4(_numGAMCols);
        _knotsMat = new double[_numGAMCols][][];
        _bs = new int[_numGAMCols];
        _splineOrder = new int[_numGAMCols];
        _numKnots = new int[_numGAMCols];
        _numBasis = new int[_numGAMCols];
        int totGamCols = bs.length;
        int countIS = 0;
        int offset = 0;
        for (int index=0; index<totGamCols; index++) {
            if (bs[index]==2) {
                int numBasis = numKnot[index]+splineOrder[index]-2;
                _totGamifiedCols += numBasis;
                _totGamifiedColCentered += numBasis;
                _knotsMat[countIS] = knotsMat[index];
                _bs[countIS] = bs[index];
                _numKnots[countIS] = numKnot[index];
                _numBasis[countIS] = numBasis;
                _splineOrder[countIS] = splineOrder[index];
                _gamColsOffsets[countIS++] = offset;
                offset += numBasis;   // minus 1 for centering
            }
        }
    }
    
    @Override
    public void map(Chunk[] chk, NewChunk[] newChunks) {
        ISplines[] isBasis = new ISplines[_numGAMCols];
        double[][] basisVals = new double[_numGAMCols][];

        for (int index=0; index<_numGAMCols; index++) {
            isBasis[index] = new ISplines(_splineOrder[index], _knotsMat[index][0]);
            basisVals[index] = MemoryManager.malloc8d(_numBasis[index]);
        }
        int chkLen = chk[0].len();
        for (int rInd=0; rInd<chkLen; rInd++) {
            for (int cInd=0; cInd<_numGAMCols; cInd++) 
                generateOneISGAMCols(cInd, _gamColsOffsets[cInd], basisVals[cInd], isBasis[cInd], chk[cInd].atd(rInd),
                        newChunks);
        }
    }

    /***
     * Perform gamification of one column using I-spline basis function described in Section V of doc.
     */
    public void generateOneISGAMCols(int colInd, int colOffset, double[] basisVals, ISplines isBasis, double xval,
                                     NewChunk[] newChunks) {
        int numVals = _numBasis[colInd];
        if (!Double.isNaN(xval)) {
            isBasis.gamifyVal(basisVals, xval);
//            basisValsCenter = ArrayUtils.multArrVec(_ztransp[colInd], basisVals, basisValsCenter);
            for (int colIndex=0; colIndex < numVals; colIndex++) 
                newChunks[colIndex+colOffset].addNum(basisVals[colIndex]);
        } else {
            for (int colIndex=0; colIndex < numVals; colIndex++)
                newChunks[colIndex+colOffset].addNum(Double.NaN);
        }
    }
}
