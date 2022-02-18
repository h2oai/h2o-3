package hex.gam.GamSplines;

import java.util.ArrayList;
import java.util.List;

import static hex.gam.GamSplines.NBSplinesUtils.*;

public class NBSplinesTypeI {
    private static final double EPS = 1e-12;
    private final int _order;
    private int _index;   // start index of its knots off the original knot sequence with duplication
    private double[] _nodeCoeffs;
    private double[] _coeffLeft;   // coefficient to pass down to child
    private double[] _coeffRite;
    private List<Double> _knots;
    private double _commonConst;
    private NBSplinesTypeI _left;
    private NBSplinesTypeI _rite;

    /**
     * 
     * @param knots : containing knots that is from spline of _order+1, or the whole list of knots
     * @param order : parent spline function order
     * @param basisIndex : basis function index
     */
    public NBSplinesTypeI(List<Double> knots, int order, int basisIndex, int offset) {
        _order= order;
        _knots = extractKnots(offset, order, knots);
        if (_order == 1) {
            _commonConst = 0;
            _coeffLeft = Math.abs(_knots.get(1)-_knots.get(0))<EPS?new double[]{0}:new double[]{1.0/(_knots.get(1)-_knots.get(0))};
            _coeffRite = _coeffLeft.clone();
        } else {
            _commonConst = Math.abs(_knots.get(offset) - _knots.get(offset + _order))<EPS ? 0 :
                    _order / ((_knots.get(offset + _order) - _knots.get(offset))*(_order-1));
            _coeffLeft = new double[]{-_knots.get(0), 1};
            _coeffRite = new double[]{_knots.get(_order), -1};
        }
        _left = null;
        _rite = null;
        _index = basisIndex;
    }
    
    public double[] extractNBSplineCoeffs(NBSplinesTypeI root, int order, double[] coeffLeft, double[] coeffRite, List<Double> knots, int basisIndex) {
        if (order == 1) { // reach the bottom of recursion tree
            if (coeffLeft != null)
                return combineCoef(root._coeffLeft, coeffLeft);
            else
                return combineCoef(root._coeffRite, coeffRite);
        } else {
            
        }
        return null;
    }

    public static NBSplinesTypeI formBasis(List<Double> knots, int order, int basisIndex, int offset) {
        if (order == 1) {
            return new NBSplinesTypeI(knots, order, basisIndex, offset);
        } else {
            NBSplinesTypeI nbsplines = new NBSplinesTypeI(knots, order, basisIndex, offset);
            nbsplines._left = formBasis(nbsplines._knots, order-1, basisIndex, 0);
            nbsplines._rite = formBasis(nbsplines._knots, order-1, basisIndex, 1);
            return nbsplines;
        }
    }
}
