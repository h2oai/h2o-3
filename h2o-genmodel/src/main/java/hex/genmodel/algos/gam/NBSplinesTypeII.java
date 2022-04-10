package hex.genmodel.algos.gam;

import java.io.Serializable;
import static hex.genmodel.algos.gam.GamUtilsISplines.*;

public class NBSplinesTypeII implements Serializable {
    public final int _order;   // order of splines
    private final int _nKnots;   // number of knots of multiplicity 1
    private final double[] _knots;
    public final int _totBasisFuncs;
    public final BSplineBasis[] _basisFuncs;

    public NBSplinesTypeII(int m, double[] knots) {
        _order = m;
        _nKnots = knots.length;
        _totBasisFuncs = _nKnots + _order - 2;
        _knots = fillKnots(knots, m);
        _basisFuncs = genBasisFunctions(_totBasisFuncs, _order, _knots);

    }

    private static BSplineBasis[] genBasisFunctions(int totBasisFuncs, int order, double[] knots) {
        BSplineBasis[] basisFuncs = new BSplineBasis[totBasisFuncs];
        for (int index = 0; index < totBasisFuncs; index++) {
            basisFuncs[index] = formOneBasisFunc(index, order, knots);
        }
        return basisFuncs;
    }

    private static BSplineBasis formOneBasisFunc(int knotIndex, int order, double[] knots) {
        if (order == 1) {
            return new BSplineBasis(knotIndex, order, knots);
        } else {
            BSplineBasis oneBasis = new BSplineBasis(knotIndex, order, knots);
            oneBasis._first = formOneBasisFunc(knotIndex, order - 1, knots);
            oneBasis._second = formOneBasisFunc(knotIndex + 1, order - 1, knots);
            return oneBasis;
        }
    }

    public void gamify(double[] gamifiedValues, double value) {
        if (gamifiedValues == null)
            gamifiedValues = new double[_totBasisFuncs];
        for (int index = 0; index < _totBasisFuncs; index++)
            gamifiedValues[index] = BSplineBasis.evaluate(value, _basisFuncs[index]);
    }

    public static class BSplineBasis implements Serializable {
        public double[] _knots;     // knots over which basis function is non-zero, include possible duplicates
        private double[] _numerator;
        private double[] _oneOverdenominator;
        private BSplineBasis _first;  // first part of basis function
        private BSplineBasis _second;

        public BSplineBasis(int index, int order, double[] knots) {
            _first = null;
            _second = null;
            _knots = extractKnots(index, order, knots);
            int knotsizeDiff = order + 1 - _knots.length;
            if (knotsizeDiff > 0) {
                double[] extendKnots = new double[knots.length + knotsizeDiff];
                System.arraycopy(_knots, 0, extendKnots, 0, _knots.length);
                double lastKnot = _knots[_knots.length - 1];
                for (int kIndex = _knots.length; kIndex < extendKnots.length; kIndex++)
                    extendKnots[kIndex] = lastKnot;    // extend last index
                _knots = extendKnots;
            }

            _numerator = formNumerator(order, _knots);
            _oneOverdenominator = formDenominator(order, _knots);
        }

        public static double evaluate(double value, BSplineBasis root) {
            if (value < root._knots[0] || value >= root._knots[root._knots.length - 1])
                return 0;   // value outside current basis function non-zero range
            if (root._first != null) {
                return (value - root._numerator[0]) * root._oneOverdenominator[0] * evaluate(value, root._first)
                        + (root._numerator[1] - value) * root._oneOverdenominator[1] * evaluate(value, root._second);
            } else {    // arrive at order==1 with null children
                return 1;
            }
        }
    }
}
