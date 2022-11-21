package hex.gam.MatrixFrameUtils;

import hex.gam.GAMModel;
import hex.genmodel.algos.gam.MSplines;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.ArrayUtils;

import static hex.gam.GamSplines.NBSplinesTypeIDerivative.genMSPenaltyMatrix;
import static hex.gam.MatrixFrameUtils.GenCSSplineGamOneColumn.generateZTransp;

public class GenMSplineGamOneColumn extends MRTask<GenMSplineGamOneColumn> {
    private final double[] _knots;  // knots without duplication/extension
    private final int _order;       // actual polynomial spline has order _order-1
    double[] _maxAbsRowSum;
    public double _s_scale;
    private final int _gamColNChunks;
    public double[][] _ZTransp;         // store Z matrix transpose, keep for now
    public double[][] _penaltyMat;      // store penalty matrix
    public final int _numBasis;
    public final int _totKnots;

    /**
     * Perform gamification on one predictor.
     * 
     * @param parm: GAM parameters
     * @param knots: double array of knots without duplication
     * @param gamColIndex: index of which sorted gam columns we are dealing with.
     * @param gamCol: frame containing predictor to be gamified
     * @param nBasis: number of basis function
     * @param totKnots: total number of knots with duplication
     */
    public GenMSplineGamOneColumn(GAMModel.GAMParameters parm, double[] knots, int gamColIndex, Frame gamCol,
                                  int nBasis, int totKnots) {
        _knots = knots;
        _order = parm._spline_orders_sorted[gamColIndex];
        _numBasis = nBasis > 0 ? nBasis : knots.length+_order-2;
        _totKnots = totKnots > 0 ? totKnots : knots.length+2*_order-2;
        _gamColNChunks = gamCol.vec(0).nChunks();
        _penaltyMat = genMSPenaltyMatrix(knots, parm._spline_orders_sorted[gamColIndex]);
    }

    @Override
    public void map(Chunk[] chk, NewChunk[] newGamCols) {
        MSplines basisFuncs = new MSplines(_order, _knots);
        _maxAbsRowSum = new double[_gamColNChunks];
        double[] basisVals = new double[_numBasis]; // array to hold each gamified row
        int cIndex = chk[0].cidx();
        _maxAbsRowSum[cIndex] = Double.NEGATIVE_INFINITY;
        int chkRows = chk[0].len();
        for (int rowIndex=0; rowIndex < chkRows; rowIndex++) {
            double gamRowSum = 0.0;
            if (chk[1].atd(rowIndex) != 0) {
                double xval = chk[0].atd(rowIndex);
                if (Double.isNaN(xval)) {
                    for (int colIndex = 0; colIndex < _numBasis; colIndex++)
                        newGamCols[colIndex].addNum(Double.NaN);
                } else {
                    basisFuncs.gamifyVal(basisVals, xval);
                    // copy updates to the newChunk row
                    for (int colIndex = 0; colIndex < _numBasis; colIndex++) {
                        newGamCols[colIndex].addNum(basisVals[colIndex]);
                        gamRowSum += Math.abs(basisVals[colIndex]);
                    }
                    if (gamRowSum > _maxAbsRowSum[cIndex])
                        _maxAbsRowSum[cIndex] = gamRowSum;
                }
            } else {
                for (int colIndex = 0; colIndex < _numBasis; colIndex++)
                    newGamCols[colIndex].addNum(0.0);
            }
        }
    }


    public void reduce(GenMSplineGamOneColumn other) {
        ArrayUtils.add(_maxAbsRowSum, other._maxAbsRowSum);
    }

    @Override
    public void postGlobal() {  // scale the _penalty function according to R
        double tempMaxValue = ArrayUtils.maxValue(_maxAbsRowSum);
        _s_scale = tempMaxValue*tempMaxValue/ArrayUtils.rNorm(_penaltyMat, 'i');
        if (Double.isFinite(_s_scale))
            ArrayUtils.mult(_penaltyMat, _s_scale);
        _s_scale = 1.0/ _s_scale;
    }
    
    public Frame centralizeFrame(Frame fr, String colNameStart, GAMModel.GAMParameters parms) {
        _ZTransp = generateZTransp(fr, _numBasis);
        return  GenCSSplineGamOneColumn.centralizeFrame(fr, colNameStart, parms, _ZTransp);
    }
    
}
