package hex.genmodel.algos.gam;

import java.io.Serializable;

import static hex.genmodel.algos.gam.GamUtilsISplines.*;

/**
 * I implemented the spline described in Section III of doc in the GitHub issue https://github.com/h2oai/h2o-3/issues/7261.
 * Any reference to doc I in the code refer to the one here with the http link.
 *
 * The recursive formula in equation 5 is used.  It is implemented as a binary tree with current node with order m
 * and two child nodes with order m-1.
 */
public class NBSplinesTypeI implements Serializable {
    public final int _order;
    public double[][] _nodeCoeffs;  // expanded polynomial coefficients at current node, section VI of doc
    private double[] _coeffLeft;    // represent (t-ti) of equation 5
    private double[] _coeffRight;    // represent (ti+k-t) of equation 5
    double[] _knots;        // knot sequence with duplication
    public double _commonConst;    // k/((k-1)*(ti+k-ti) of equation 5
    private NBSplinesTypeI _left;   // lower order spline Mi,k-1(t) of equation 5
    private NBSplinesTypeI _right;   // lower order spline Mi+1,k-1(t) of equation 5
    public final int _totBasisFuncs;
    public final int _nKnots;

    /**
     * Generate SBSpline Type I in Section III of doc I
     *
     * @param knots : knots that span the whole input range of interest with no duplication
     * @param order : order of spline
     * @param basisIndex : offset added to basis function index, recall lower order splines have index i and i+1 equation 5 of doc
     * @param numKnotInt : integer denoting knot intervals over which polynomial coefficients are defined
     */
    public NBSplinesTypeI(double[] knots, int order, int basisIndex, int numKnotInt) {
        _order= order;
        _knots = extractKnots(basisIndex, order, knots);    // extract knots over basis function is non-zero
        _nodeCoeffs = new double[numKnotInt][];
        setConstNChildCoeffs(knots, basisIndex);
        _left = null;
        _right = null;
        _nKnots = knots.length;
        _totBasisFuncs = _order+_nKnots-2;
    }
    
    protected static MSplineBasis[] genBasisFunctions(int totBasisFuncs, int order, double[] knots) {
        MSplineBasis[] basisFuncs = new MSplineBasis[totBasisFuncs];
        for (int index=0; index<totBasisFuncs; index++)
            basisFuncs[index] = formOneBasisFunc(index, order, knots);
        return basisFuncs;
    }
    
    private static MSplineBasis formOneBasisFunc(int basisIndex, int order, double[] knots) {
        if (order == 1) {
            return new MSplineBasis(basisIndex, order, knots);
        } else {
            MSplineBasis oneBasis = new MSplineBasis(basisIndex, order, knots);
            oneBasis._first = formOneBasisFunc(basisIndex, order-1, knots);
            oneBasis._second = formOneBasisFunc(basisIndex+1, order-1, knots);
            return oneBasis;
        }
    }

    public void setConstNChildCoeffs(double[] knots, int basisIndex) {
        double temp;
        if (_order <= 1) {
            _commonConst = 0.0;
            temp = _knots[1] - _knots[0];
            _coeffLeft = temp == 0 ? new double[]{0} : new double[]{1.0 / temp};
            _coeffRight = new double[]{0.0};
        } else {
            temp = knots[basisIndex] - knots[basisIndex + _order];
            _commonConst = temp == 0 ? 0 :
                    _order / (temp * (_order - 1));
            _coeffLeft = new double[]{-knots[basisIndex], 1};
            _coeffRight = new double[]{knots[_order + basisIndex], -1};
        }
    }

    /**
     * Given root spline, this method will extract the coefficients of spline as described in Section VI in order to
     * generate the penalty matrix using recursion.  This is actually follows recursion of TypeII
     */
    public static void extractNBSplineCoeffs(NBSplinesTypeI root, int order, double[] coeffParent, double constParent,
                                             int basisIndex) {
        if (order == 1) { // reach the bottom of recursion tree
            double temp = root._knots[1] - root._knots[0];
            if (temp != 0) {
                root._nodeCoeffs[basisIndex] = polynomialProduct(
                        new double[]{constParent / temp}, coeffParent);
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
        double temp, temp2;
        if (root._order == 1) { // short cut for tree of order 1
            temp = root._knots[1]-root._knots[0];
            if (temp != 0) {
                temp2 = parentConst / temp;
                root._nodeCoeffs[basisIndex] = new double[]{temp2};
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
     * @param basisIndex : offset to added in order to address spline of lower order
     * @param numKnotInt : length of knots with duplication over the whole range of input of interest.
     * @return NBSplinesTypeI for spline Mi,k(t)
     */
    public static NBSplinesTypeI formBasisDeriv(double[] knots, int order, int basisIndex, int numKnotInt) {
        if (order == 1) {
            return new NBSplinesTypeI(knots, order, basisIndex, numKnotInt);
        } else {
            NBSplinesTypeI nbsplines = new NBSplinesTypeI(knots, order, basisIndex, numKnotInt);
            nbsplines._left = formBasisDeriv(nbsplines._knots, order-1,0, numKnotInt);
            nbsplines._right = formBasisDeriv(nbsplines._knots, order-1,1, numKnotInt);
            return nbsplines;
        }
    }
    
    /**
     * This class describes a M spline using the recursive formula in equation 5 of the doc.
     */
    public static class MSplineBasis implements Serializable {
        double[] _knots; // knots over which basis function is non-zero, may include duplicate
        private double[] _numerator;
        private double[] _oneOverDenominator;
        private MSplineBasis _first;
        private MSplineBasis _second;
        private double _constant;

        /**
         * 
         * @param index: basis function number
         * @param order: order of M-spline
         * @param knots: full knots with duplications already performed.
         */
        public MSplineBasis(int index, int order, double[] knots) {
            _first = null;
            _second = null;
            _knots = extractKnots(index, order, knots);
            _constant = order > 1 ? (order/(order-1.0)) : 1;
            _numerator = formNumerator(order, _knots);
            _oneOverDenominator = formDenominatorMSpline(order, _knots);
        }

        public static double evaluate(double value, MSplineBasis root) {
            if (value < root._knots[0] || value >= root._knots[root._knots.length - 1])
                return 0;   // value outside current basis function non-zero range
            if (root._first != null) {
                return root._constant*((value - root._numerator[0]) * root._oneOverDenominator[0] * evaluate(value, root._first)
                        + (root._numerator[1] - value) * root._oneOverDenominator[1] * evaluate(value, root._second));
            } else {    // arrive at order==1 with null children
                double temp = root._knots[1]-root._knots[0];
                if (temp != 0)
                    return 1.0/temp;
                else
                    return 0.0;
            }
        }        
    }
}
