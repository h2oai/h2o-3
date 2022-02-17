package hex.gam.GamSplines;

import org.apache.commons.math3.geometry.partitioning.BSPTreeVisitor;

import java.util.ArrayList;
import java.util.List;

import static hex.gam.GamSplines.NBSplinesTypeI.formBasis;
import static hex.gam.GamSplines.NBSplinesUtils.extractKnots;
import static hex.gam.GamSplines.NBSplinesUtils.fillKnots;

public class NBSplinesTypeIDerivative {
    /***
     * This class implements the derivative of NBSpline Type I.  
     */
    private final int _order;
    private final int _index;
    private final List<Double> _knots;
    private final double _commonConst;
    private double[] _leftCoeff;
    private double[] _rightCoeff;
    private double[][] _coeffs; // store coefficients for each knot intervals
    private NBSplinesTypeI _left;
    private NBSplinesTypeI _rite;
    
    
    public NBSplinesTypeIDerivative(int order, int basisIndex, List<Double> knots) {
        _order = order;
        _index = basisIndex;
        _knots = extractKnots(_index, order, knots);;
        double part1 = (_order-1)==0 ? 0 : _order/(_order-1.0);
        _commonConst = part1*((_knots.get(_order) == _knots.get(0)) ? 0 : 1.0/(_knots.get(_order)-_knots.get(0)));
        _leftCoeff = new double[]{-_knots.get(0),1};
        _rightCoeff = new double[]{_knots.get(_order), -1};
        _left = formBasis(knots, _order-1, basisIndex, 0);
        _rite = formBasis(knots, _order-1, basisIndex, 1);
        _coeffs = extractCoeff(_left, _rite, knots, basisIndex);
    }

    public static double[][] extractCoeff(NBSplinesTypeI left, NBSplinesTypeI rite, List<Double> knots, int basisIndex) {
        double[][] coeffs = new double[knots.size()-1][];
        return coeffs;
    }
    
    
    /***
     *
     * @param knots : containing all knots without duplication
     * @param order : order of original I-splines
     * @return double[][] array of size number of basis function by number of total numbers
     */
    public static double[][] genPenaltyMatrix(double[] knots, int order) {
        int numBasis = knots.length+order-2;
        if (order == 1)
            return new double[numBasis][numBasis];  // derivative of order 1 NBSpline will generate 0

        int totKnots = knots.length+order-2;
        List<Double> knotsWithDuplicates = fillKnots(knots, order);
        double[][] penaltyMat = new double[numBasis][numBasis];
        for (int i=0; i < numBasis; i++) {
            for (int j = i; j < numBasis; j++) {

            }
        }
        // 
        return penaltyMat;
    }
}
