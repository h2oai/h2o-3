package hex.gam.GamSplines;

import java.util.List;

import static hex.gam.GamSplines.NBSplinesUtils.*;

public class NBSplinesTypeI {
    private static final double EPS = 1e-12;
    public final int _order;
    private int _basisIndex;   // start index of its knots off the original knot sequence with duplication
    public double[][] _nodeCoeffs;   // expanded coefficients at current node
    private double[] _coeffLeft;   // coefficient to pass down to child
    private double[] _coeffRite;
    private List<Double> _knots;
    private double _commonConst;    // pass down to child
    private NBSplinesTypeI _left;
    private NBSplinesTypeI _rite;

    /**
     * 
     * @param knots : containing knots that is from spline of _order+1, or the whole list of knots
     * @param order : parent spline function order
     * @param basisIndex : basis function index
     */
    public NBSplinesTypeI(List<Double> knots, int order, int basisIndex, int offset, int numKnotInt) {
        _order= order;
        _knots = extractKnots(offset, order, knots);
        _nodeCoeffs = new double[numKnotInt][];
        if (_order == 1) {
            _commonConst = 0;
            _coeffLeft = Math.abs(_knots.get(1)-_knots.get(0))<EPS?new double[]{0}:new double[]{1.0/(_knots.get(1)-_knots.get(0))};
            _coeffRite = _coeffLeft.clone();
        } else {
            _commonConst = Math.abs(knots.get(offset) - knots.get(offset + _order))<EPS ? 0 :
                    _order / ((knots.get(offset + _order) - knots.get(offset))*(_order-1));
            _coeffLeft = new double[]{-knots.get(offset), 1};
            _coeffRite = new double[]{knots.get(_order+offset), -1};
        }
        _left = null;
        _rite = null;
        _basisIndex = basisIndex;
    }
    
    public static void extractNBSplineCoeffs(NBSplinesTypeI root, int order, double[] coeffParent, double constParent, int basisIndex) {
        if (order == 1) { // reach the bottom of recursion tree
            if (Math.abs(root._knots.get(1) - root._knots.get(0)) > EPS) {
                root._nodeCoeffs[basisIndex] = polynomialProduct(
                        new double[]{constParent / (root._knots.get(1) - root._knots.get(0))}, coeffParent);
            }
        } else {
            extractNBSplineCoeffs(root._left, order-1, root._coeffLeft, root._commonConst, basisIndex);
            extractNBSplineCoeffs(root._rite, order-1, root._coeffRite, root._commonConst, basisIndex+1);
            sumCoeffs(root._left._nodeCoeffs, root._rite._nodeCoeffs, root._nodeCoeffs);
            combineParentCoef(coeffParent, constParent, root._nodeCoeffs);
        }

    }
    
    public static double[][] extractCoeffs(NBSplinesTypeI root, int basisIndex, double parentConst) {
        if (root._order == 1) { // short cut for tree of order 1
            if (Math.abs(root._knots.get(1)-root._knots.get(0)) > EPS) {
                double temp = parentConst / (root._knots.get(1) - root._knots.get(0));
                root._nodeCoeffs[basisIndex] = new double[]{temp};
            }
            return root._nodeCoeffs;
        } else {
            extractNBSplineCoeffs(root, root._order, new double[]{1.0}, parentConst, basisIndex);
            return root._nodeCoeffs;
        }
    }

    public static NBSplinesTypeI formBasis(List<Double> knots, int order, int basisIndex, int offset, int numKnotInt) {
        if (order == 1) {
            return new NBSplinesTypeI(knots, order, basisIndex, offset, numKnotInt);
        } else {
            NBSplinesTypeI nbsplines = new NBSplinesTypeI(knots, order, basisIndex, offset, numKnotInt);
            nbsplines._left = formBasis(nbsplines._knots, order-1, basisIndex, 0, numKnotInt);
            nbsplines._rite = formBasis(nbsplines._knots, order-1, basisIndex, 1, numKnotInt);
            return nbsplines;
        }
    }
}
