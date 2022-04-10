package hex.gam.GamSplines;

import static hex.gam.GamSplines.NBSplinesUtils.*;
import static hex.genmodel.algos.gam.GamUtilsISplines.extractKnots;

/**
 * I implemented the spline described in Section III of doc in JIRA: https://h2oai.atlassian.net/browse/PUBDEV-8398.
 * Any reference to doc in the code refer to the one here with the http link.
 * 
 * The recursive formula in equation 5 is used.  It is implemented as a binary tree with current node with order m
 * and two child nodes with order m-1.
 */
public class NBSplinesTypeI {
    private static final double EPS = 1e-12;
    public final int _order;
    public double[][] _nodeCoeffs;  // expanded polynomial coefficients at current node, section VI of doc
    private double[] _coeffLeft;    // represent (t-ti) of equation 5
    private double[] _coeffRight;    // represent (ti+k-t) of equation 5
    private double[] _knots;        // knot sequence with duplication
    private double _commonConst;    // k/((k-1)*(ti+k-ti) of equation 5
    private NBSplinesTypeI _left;   // lower order spline Mi,k-1(t) of equation 5
    private NBSplinesTypeI _right;   // lower order spline Mi+1,k-1(t) of equation 5
    
    /**
     * Generate SBSpline Type I in Section III of doc 
     * 
     * @param knots : knots that span the whole input range of interest with no duplication
     * @param order : order of spline
     * @param basisIndex : basis function index
     * @param offset : offset added to basis function index, recall lower order splines have index i and i+1 equation 5 of doc
     * @param numKnotInt : integer denoting knot intervals over which polynomial coefficients are defined
     */
    public NBSplinesTypeI(double[] knots, int order, int basisIndex, int offset, int numKnotInt) {
        _order= order;
        _knots = extractKnots(offset, order, knots);    // extract knots over basis function is non-zero
        _nodeCoeffs = new double[numKnotInt][];
        setConstNChildCoeffs(knots, offset);
        _left = null;
        _right = null;
    }

    public void setConstNChildCoeffs(double[] knots, int offset) {
        if (_order == 1) {
            _commonConst = 0;
            _coeffLeft = Math.abs(_knots[1] - _knots[0]) < EPS ? new double[]{0} : new double[]{1.0 / 
                    (_knots[1] - _knots[0])};
            _coeffRight = _coeffLeft.clone();
        } else {
            _commonConst = Math.abs(knots[offset] - knots[offset + _order]) < EPS ? 0 :
                    _order / ((knots[offset + _order] - knots[offset]) * (_order - 1));
            _coeffLeft = new double[]{-knots[offset], 1};
            _coeffRight = new double[]{knots[_order + offset], -1};
        }
    }

    /**
     * Given root spline, this method will extract the coefficients of spline as described in Section VI in order to
     * generate the penalty matrix using recursion.
     */
    public static void extractNBSplineCoeffs(NBSplinesTypeI root, int order, double[] coeffParent, double constParent, int basisIndex) {
        if (order == 1) { // reach the bottom of recursion tree
            if (Math.abs(root._knots[1] - root._knots[0]) > EPS) {
                root._nodeCoeffs[basisIndex] = polynomialProduct(
                        new double[]{constParent / (root._knots[1] - root._knots[0])}, coeffParent);
            }
        } else {
            extractNBSplineCoeffs(root._left, order-1, root._coeffLeft, root._commonConst, basisIndex);
            extractNBSplineCoeffs(root._right, order-1, root._coeffRight, root._commonConst, basisIndex+1);
            sumCoeffs(root._left._nodeCoeffs, root._right._nodeCoeffs, root._nodeCoeffs);
            combineParentCoef(coeffParent, constParent, root._nodeCoeffs);
        }

    }

    /**
     * extract coefficients of NBSplineType I in the process of generating penalty matrix in Section VI of doc.
     */
    public static double[][] extractCoeffs(NBSplinesTypeI root, int basisIndex, double parentConst) {
        if (root._order == 1) { // short cut for tree of order 1
            if (Math.abs(root._knots[1]-root._knots[0]) > EPS) {
                double temp = parentConst / (root._knots[1] - root._knots[0]);
                root._nodeCoeffs[basisIndex] = new double[]{temp};
            }
            return root._nodeCoeffs;
        } else {
            extractNBSplineCoeffs(root, root._order, new double[]{1.0}, parentConst, basisIndex);
            return root._nodeCoeffs;
        }
    }

    /**
     * Given an basis function index, order and knot sequence with duplication over the input range of interest, 
     * we build the whole binary tree for NBSplineType I for Mi,k(t) down to the lowest level with splines of order
     * 1 as in Mi,1(t).  This is done using recursion following equation 5.
     * 
     * @param knots : knot sequence with duplication 
     * @param order : order (k) of spline to build
     * @param basisIndex : index i
     * @param offset : offset to added in order to address spline of lower order
     * @param numKnotInt : length of knots with duplication over the whole range of input of interest.
     * @return NBSplinesTypeI for spline Mi,k(t)
     */
    public static NBSplinesTypeI formBasis(double[] knots, int order, int basisIndex, int offset, int numKnotInt) {
        if (order == 1) {
            return new NBSplinesTypeI(knots, order, basisIndex, offset, numKnotInt);
        } else {
            NBSplinesTypeI nbsplines = new NBSplinesTypeI(knots, order, basisIndex, offset, numKnotInt);
            nbsplines._left = formBasis(nbsplines._knots, order-1, basisIndex, 0, numKnotInt);
            nbsplines._right = formBasis(nbsplines._knots, order-1, basisIndex, 1, numKnotInt);
            return nbsplines;
        }
    }
}
