package hex.gam.GamSplines;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static hex.gam.GamSplines.NBSplinesUtils.*;

public class NormalizedBSplines {
    private final int _m;   // order of splines
    private final int _nKnots;   // number of knots of multiplicity 1
    private final List<Double> _knots;
    private final int _totKnots;    // number of knots including duplication at beginning and end
    private final int _totBasisFuncs;
    private BSplineBasis[] _basisFuncs;
    
    public NormalizedBSplines(int m, int N, double[] knots) {
        _m = m;
        _nKnots = N;
        _totKnots = _nKnots +2*_m-2;
        _totBasisFuncs = _nKnots +_m-2;
        if (N == knots.length)
            _knots = fillKnots(knots, m, N);
        else  // array already contains knot duplicates
            _knots = Arrays.stream(knots).boxed().collect(Collectors.toList());
        _basisFuncs = genBasisFunctions(_totBasisFuncs, _m, _knots);
        
    }
    
    private static BSplineBasis[] genBasisFunctions(int totBasisFuncs, int order, List<Double> knots) {
        BSplineBasis[] basisFuncs = new BSplineBasis[totBasisFuncs];
        for (int index=0; index<totBasisFuncs; index++) {
            basisFuncs[index] = formOneBasisFunc(index, order, knots, true);
        }
        
        return basisFuncs;
    }
    
    private static BSplineBasis formOneBasisFunc(int knotIndex, int order, List<Double> knots, boolean firstChild) {
        if (order == 1) {
            BSplineBasis oneBasis = new BSplineBasis(knotIndex, order, knots, firstChild);
            return oneBasis;
        } else {
            BSplineBasis oneBasis = new BSplineBasis(knotIndex, order, knots, firstChild);
            oneBasis._first = formOneBasisFunc(knotIndex, order-1, knots, true);
            oneBasis._second = formOneBasisFunc(knotIndex+1, order-1, knots, false);
            return oneBasis;
        }
    }
    
    private static class BSplineBasis {
        private List<Double> _knots;    // knots over which basis function is non-zero, expanded
        private double[] _numerator;    // only length 2
        private double[] _oneOverdenominator;  // only length 2
        private BSplineBasis _first;  // first part of basis function
        private BSplineBasis _second; 
        private final int _k; // order of basis function
        private int _index; // starting index of knots with duplication
        private final boolean _firstChild; // true if it is first child
        
        public BSplineBasis(int index, int order, List<Double> knots, boolean firstChild) {
            _k = order;
            _index = index;
            _first = null;
            _second = null;
            _firstChild = firstChild;
            _knots = extractKnots(index, order, knots);
            _numerator = formNumerator(index, order, knots);
            _oneOverdenominator = formDenominator(index, order, knots);
        }
    }
}
