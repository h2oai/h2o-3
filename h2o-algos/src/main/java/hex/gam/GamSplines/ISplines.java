package hex.gam.GamSplines;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class ISplines {
    private final List<Double> _knotsOriginal;    // stores knots sequence, not expanded
    private final int _order;         // order of ISplines, starts from 1, 2, ...
    private int _nKnots;        // number of knots not counting duplicates
    public int _numIBasis;     // number of I splines over knot sequence
    private int _totKnots;      // number of knots including duplicates
    NormalizedBSplines _bSplines;   // point to BSpline of order _order+1 over the same knot sequence
    private final double _minKnot;
    private final double _maxKnot;
    private final ISplineBasis[] _iSplines;
    
    public ISplines(int order, double[] knots) {
        _knotsOriginal = Arrays.stream(knots).boxed().collect(Collectors.toList());
        _order = order;
        _bSplines = new NormalizedBSplines(order+1, knots);
        _numIBasis = _bSplines._totBasisFuncs;
        _minKnot = knots[0];
        _maxKnot = knots[knots.length-1];
        _iSplines = new ISplineBasis[_numIBasis];
        for (int index=0; index < _numIBasis; index++)
            _iSplines[index] = new ISplineBasis(index, _order, _bSplines);
    }

    public double[][] gen_penalty_matrix() {
        double[][] penalty_matrix = new double[_numIBasis][_numIBasis];
        return penalty_matrix;
    }
    
    public void gamifyVal(double[] gamifiedResults, double val) {
        if (gamifiedResults == null)
            gamifiedResults = new double[_numIBasis];
        
        for (int basisInd = 0; basisInd < _numIBasis; basisInd++) {
            if (val < _iSplines[basisInd]._knots.get(0))
                gamifiedResults[basisInd] = 0;
            else if (val >= _iSplines[basisInd]._knots.get(_order))
                gamifiedResults[basisInd] = 1;
            else 
                gamifiedResults[basisInd] = sumNBSpline(basisInd, val);
        }
    }
    
    public double sumNBSpline(int startIndex, double val) {
        double gamifiedVal = 0;
        int maxBasisInd = Math.min(startIndex+_order, _bSplines._basisFuncs.length);
        for (int basisInd = startIndex; basisInd < maxBasisInd; basisInd++) {
            if (val < _bSplines._basisFuncs[basisInd]._knots.get(0)) {
                break;  // no more basis function to be activated
            } else if (val >= _bSplines._basisFuncs[basisInd]._knots.get(_order)) {
                gamifiedVal += 1;
            } else {
                gamifiedVal += NormalizedBSplines.BSplineBasis.evaluate(val, _bSplines._basisFuncs[basisInd]);
            }
        }
        return gamifiedVal;
    }
    
    private static class ISplineBasis {
        private List<Double> _knots;    // knots over which function is non-zero
        private int _NSplineBasisStartIndex;    // start index of NB spline function of interest
        private int _order;
        
        public ISplineBasis(int basisInd, int order, NormalizedBSplines bSplines) {
            _NSplineBasisStartIndex = basisInd;
            _order = order;
            _knots = new ArrayList<>(bSplines._basisFuncs[basisInd]._knots);
        }
    }
}
