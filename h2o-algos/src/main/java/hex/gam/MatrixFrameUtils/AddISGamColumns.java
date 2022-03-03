package hex.gam.MatrixFrameUtils;

import hex.gam.GamSplines.ISplines;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

public class AddISGamColumns extends MRTask<AddISGamColumns> {
    double[][][] _knotsMat; // with knots without duplication for I-spline only
    double[][][] _ztransp;  // for I-spline only
    int[] _numKnots;  // for I-spline only
    int[] _numBasis;
    public int _numGAMCols; // only number of I-Spline gam columns
    int[] _gamColsOffsets;
    Frame _gamFrame;
    int[] _bs;       // for I-spline only
    int[] _splineOrder;    // for I-spline only
    int _totGamifiedCols=0;
    public int _totGamifiedColCentered=0;
    
    public AddISGamColumns(double[][][] ztransp, double[][][] knotsMat, int[] numKnot, int[] bs, int[] splineOrder,
                           Frame gamColFrames) {
        _gamFrame = gamColFrames;
        _numGAMCols = gamColFrames.numCols();
        _gamColsOffsets = MemoryManager.malloc4(_numGAMCols);
        _knotsMat = new double[_numGAMCols][][];
        _ztransp = new double[_numGAMCols][][];
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
                _totGamifiedColCentered += numBasis-1;
                _knotsMat[countIS] = knotsMat[index];
                _ztransp[countIS] = ztransp[index];
                _bs[countIS] = bs[index];
                _numKnots[countIS] = numKnot[index];
                _numBasis[countIS] = numBasis;
                _splineOrder[countIS] = splineOrder[index];
                _gamColsOffsets[countIS++] = offset;
                offset += numBasis-1;   // minus 1 for centering
            }
        }
    }
    
    @Override
    public void map(Chunk[] chk, NewChunk[] newChunks) {
        ISplines[] isBasis = new ISplines[_numGAMCols];
        double[][] basisVals = new double[_numGAMCols][];
        double[][] basisValsCenter = new double[_numGAMCols][];
/*        for (int index=0; index<_numGAMCols; index++) {
            isBasis = new ISplines();
        }*/
    }
}
