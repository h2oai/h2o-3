package hex.gam.MatrixFrameUtils;

import hex.gam.GAMModel;
import hex.gam.GamSplines.ISplines;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

public class GenISplineGamOneColumn extends MRTask<GenCSSplineGamOneColumn> {
    private final double[] _knots;    // knots without duplication
    private final int _order;
    double[] _maxAbsRowSum; // store maximum row sum
    private  double _s_scale;
    private final int _gamColNChunks;
    public double[][] _ZTransp;  // store Z matrix transpose
    public double[][] _penaltyMat;  // store penalty matrix
    
    public GenISplineGamOneColumn(GAMModel.GAMParameters parm, double[] knots, int gamColIndex, Frame gamCol) {
        _knots = knots;
        _order = parm._spline_orders_sorted[gamColIndex];
        _gamColNChunks = gamCol.vec(0).nChunks();
    }

    @Override
    public void map(Chunk[] chk, NewChunk[] newGamCols) {
        ISplines basisFuncs = new ISplines(_order, _knots);
        _maxAbsRowSum = new double[_gamColNChunks];
        int totBasisFuncs = basisFuncs._numIBasis;
        double[] basisVals = new double[totBasisFuncs]; // array to hold each gamified row
        int cIndex = chk[0].cidx();
        _maxAbsRowSum[cIndex] = Double.NEGATIVE_INFINITY;
        int chkRows = chk[0].len();
        for (int rowIndex=0; rowIndex < chkRows; rowIndex++) {
            if (chk[1].atd(rowIndex) != 0) {
                double gamRowSum = 0.0;
                double xval = chk[0].atd(rowIndex);
                if (Double.isNaN(xval)) {
                    for (int colIndex = 0; colIndex < totBasisFuncs; colIndex++)
                        newGamCols[colIndex].addNum(Double.NaN);
                } else {
                    basisFuncs.gamifyVal(basisVals, xval);
                }
                // copy updates to the newChunk row
                for (int colIndex = 0; colIndex < totBasisFuncs; colIndex++) {
                    newGamCols[colIndex].addNum(basisVals[colIndex]);
                    gamRowSum += Double.isNaN(basisVals[colIndex]) ? 0 : Math.abs(basisVals[colIndex]);
                }
                if (gamRowSum > _maxAbsRowSum[cIndex])
                    _maxAbsRowSum[cIndex] = gamRowSum;
            } else {
                for (int colIndex = 0; colIndex < totBasisFuncs; colIndex++)
                    newGamCols[colIndex].addNum(0.0);
            }
        }
    }
}
