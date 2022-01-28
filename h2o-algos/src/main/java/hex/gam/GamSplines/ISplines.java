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
        double[] temp = gamifiedResults;
        if (gamifiedResults == null)
            temp = new double[_numIBasis];
        
        final double tempVal = Math.min(Math.max(_minKnot, val), _maxKnot);
        double lowerKnot = _knotsOriginal.stream().filter(x -> x<=tempVal).reduce((first, second)->second).get();
        double upperKnot = _knotsOriginal.stream().filter(x -> x>=tempVal).findFirst().get(); 
    }
    
    private static class ISplineBasis {
        private List<Double> _knots;    // knots over which function is non-zero
        private int _NSplineBasisStartIndex;    // start index of NB spline function of interest
        private int _order;
        
        public ISplineBasis(int basisInd, int order, NormalizedBSplines bSplines) {
            _NSplineBasisStartIndex = basisInd;
            _order = order;
            //_knots = bSplines._basisFuncs[basisInd]._knots;
        }
    }
}
