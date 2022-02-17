package hex.gam.GamSplines;

import java.util.List;

import static hex.gam.GamSplines.NBSplinesUtils.*;

public class NBSplinesTypeII {
    public final int _order;   // order of splines
    private final int _nKnots;   // number of knots of multiplicity 1
    private final List<Double> _knots;
    private final int _totKnots;    // number of knots including duplication at beginning and end
    public final int _totBasisFuncs;
    public final BSplineBasis[] _basisFuncs;
    
    public NBSplinesTypeII(int m, double[] knots) {
        _order = m;
        _nKnots = knots.length;
        _totKnots = _nKnots +2* _order -2;
        _totBasisFuncs = _nKnots + _order -2;
        _knots = fillKnots(knots, m);
        _basisFuncs = genBasisFunctions(_totBasisFuncs, _order, _knots);
        
    }
    
    private static BSplineBasis[] genBasisFunctions(int totBasisFuncs, int order, List<Double> knots) {
        BSplineBasis[] basisFuncs = new BSplineBasis[totBasisFuncs];
        for (int index=0; index<totBasisFuncs; index++) {
            basisFuncs[index] = formOneBasisFunc(index, order, knots);
        }
        return basisFuncs;
    }
    
    private static BSplineBasis formOneBasisFunc(int knotIndex, int order, List<Double> knots) {
        if (order == 1) {
            return new BSplineBasis(knotIndex, order, knots);
        } else {
            BSplineBasis oneBasis = new BSplineBasis(knotIndex, order, knots);
            oneBasis._first = formOneBasisFunc(knotIndex, order-1, knots);
            oneBasis._second = formOneBasisFunc(knotIndex+1, order-1, knots);
            return oneBasis;
        }
    }
    
    public void gamify(double[] gamifiedValues, double value) {
        if (gamifiedValues == null)
            gamifiedValues = new double[_totBasisFuncs];
        for (int index=0; index < _totBasisFuncs; index++)
            gamifiedValues[index] = BSplineBasis.evaluate(value, _basisFuncs[index]);
    }
    
    public static class BSplineBasis {
        public List<Double> _knots;     // knots over which basis function is non-zero, include possible duplicates
        private double[] _numerator;
        private double[] _oneOverdenominator;
        private BSplineBasis _first;  // first part of basis function
        private BSplineBasis _second; 
        private final int _order; // order of basis function
        private int _index; // starting index of knots/basis function
        
        public BSplineBasis(int index, int order, List<Double> knots) {
            _order = order;
            _index = index;
            _first = null;
            _second = null;
            _knots = extractKnots(index, order, knots);
            int knotsizeDiff = order+1-_knots.size();
            if (knotsizeDiff > 0) {
                double lastKnot = _knots.get(_knots.size() - 1);
                for (int kIndex=0; kIndex<knotsizeDiff;kIndex++)
                    _knots.add(lastKnot);    // extend last index
            }
            _numerator = formNumerator(order, _knots);
            _oneOverdenominator = formDenominator(order, _knots);
        }
        
        public static double evaluate(double value, BSplineBasis root) {
            if (value < root._knots.get(0) || value >= root._knots.get(root._knots.size()-1))
                return 0;   // value outside current basis function non-zero range
            if (root._first != null) {
                return (value-root._numerator[0])*root._oneOverdenominator[0]*evaluate(value, root._first)
                        +(root._numerator[1]-value)*root._oneOverdenominator[1]*evaluate(value, root._second);
            } else {    // arrive at order==1 with null children
                return 1;
            }
        }
    }
}
