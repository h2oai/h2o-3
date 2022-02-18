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
    private double[][] _coeffs; // store coefficients for basis function of _index for all knot intervals
    private NBSplinesTypeI _left;
    private NBSplinesTypeI _rite;

    /**
     * 
     * @param basisIndex
     * @param order
     * @param knots : list containing full list of knots including duplicate
     */
    public NBSplinesTypeIDerivative(int basisIndex, int order, List<Double> knots) {
        _order = order;
        _index = basisIndex;
        _knots = extractKnots(_index, order, knots);;
        _commonConst = _order/(_order-1.0)*((_knots.get(_order) == _knots.get(0)) ? 0 : 1.0/(_knots.get(_order)-_knots.get(0)));
        _leftCoeff = new double[]{1};
        _rightCoeff = new double[]{1};
        _left = formBasis(_knots, _order-1, basisIndex, 0);
        _rite = formBasis(_knots, _order-1, basisIndex, 1);
        _coeffs = extractCoeff(_left, _rite, knots, basisIndex);
    }

    /***
     * This function extracts the coefficients for the derivative of a NBSplineTypeI.
     * 
     * @param left
     * @param rite
     * @param knots : list containing full knots with duplications
     * @param basisIndex index refers to the starting location of knots in knots
     * @return
     */
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
        NBSplinesTypeIDerivative[] allDerivs = formDerivatives(numBasis, order, knotsWithDuplicates);
        double[][] penaltyMat = new double[numBasis][numBasis];
        for (int i=0; i < numBasis; i++) {
            for (int j = i; j < numBasis; j++) {
                penaltyMat[i][j] = formDerivateProduct(i, j, order, knotsWithDuplicates);
                penaltyMat[j][i] = penaltyMat[i][j];
            }
        }
        // 
        return penaltyMat;
    }
    
    public static NBSplinesTypeIDerivative[] formDerivatives(int numBasis, int order, List<Double> fullKnots) {
        NBSplinesTypeIDerivative[] allDerivs = new NBSplinesTypeIDerivative[numBasis];
        for (int index=0; index<numBasis; index++)
            allDerivs[index] = new NBSplinesTypeIDerivative(index, order, fullKnots);
        return allDerivs;
    }
    
    public static double formDerivateProduct(int firstIndex, int secondIndex, int order, List<Double> fullKnots) {
        NBSplinesTypeIDerivative firstDeriv = new NBSplinesTypeIDerivative(firstIndex, order, fullKnots);
        NBSplinesTypeIDerivative secondDeriv = new NBSplinesTypeIDerivative(secondIndex, order, fullKnots);
        return 0;
    }
    
}
