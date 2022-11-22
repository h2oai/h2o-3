package hex.genmodel.algos.gam;

import java.io.Serializable;

import static hex.genmodel.algos.gam.GamUtilsISplines.fillKnots;

public class MSplines implements Serializable {
    private final double[] _knotsWDuplicates;   // expanded knots with duplicates
    private final int _order;         // order of ISplines, starts from 1, 2, ...
    public int _numMBasis;     // number of I splines over knot sequence
    private final NBSplinesTypeI.MSplineBasis[] _mSplines;

    /***
     * 
     * @param order: order of the spline.  However, polynomial order is order - 1
     * @param knots: all knots (boundary and interior) without duplication
     */
    public MSplines(int order, double[] knots) {
        _knotsWDuplicates = fillKnots(knots, order);
        _order = order;
        _numMBasis = knots.length + order - 2;
        _mSplines = NBSplinesTypeI.genBasisFunctions(_numMBasis,  order, _knotsWDuplicates);
    }

    public void gamifyVal(double[] gamifiedResults, double val) {
        if (gamifiedResults == null)
            gamifiedResults = new double[_numMBasis];

        for (int basisInd = 0; basisInd < _numMBasis; basisInd++) {
            if (val < _mSplines[basisInd]._knots[0]) {
                gamifiedResults[basisInd] = 0;
            } else if (val >= _mSplines[basisInd]._knots[_order]) {
                gamifiedResults[basisInd] = 0;
            } else {
                gamifiedResults[basisInd] = NBSplinesTypeI.MSplineBasis.evaluate(val, _mSplines[basisInd]);  //NBSplinesTypeI
            }
        }
    }
}
