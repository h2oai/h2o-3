package hex.gam.GamSplines;

import water.util.ArrayUtils;

import java.util.List;

import static hex.gam.GamSplines.NBSplinesTypeI.formBasis;
import static hex.gam.GamSplines.NBSplinesTypeI.extractCoeffs;
import static hex.gam.GamSplines.NBSplinesUtils.*;

public class NBSplinesTypeIDerivative {
    /***
     * This class implements the derivative of NBSpline Type I.  
     */
    private final int _order;
    private final int _basisIndex;
    private final List<Double> _knots;
    private final double _commonConst;
    private double[] _leftCoeff;
    private double[] _rightCoeff;
    public double[][] _coeffs; // store coefficients for basis function of _index for all knot intervals
    private NBSplinesTypeI _left;
    private NBSplinesTypeI _rite;

    /**
     * 
     * @param basisIndex
     * @param order
     * @param fullKnots : list containing full list of knots including duplicate
     */
    public NBSplinesTypeIDerivative(int basisIndex, int order, List<Double> fullKnots) {
        _order = order;
        _basisIndex = basisIndex;
        _knots = extractKnots(_basisIndex, order, fullKnots);;
        _commonConst = _order*((_knots.get(_order) == _knots.get(0)) ? 0 : 1.0/(_knots.get(_order)-_knots.get(0)));
        _leftCoeff = new double[]{1};
        _rightCoeff = new double[]{1};
        _left = formBasis(_knots, _order-1, basisIndex, 0, fullKnots.size()-1);
        _rite = formBasis(_knots, _order-1, basisIndex, 1, fullKnots.size()-1);
        _coeffs = extractDerivativeCoeff(_left, _rite, fullKnots, basisIndex, _commonConst);
    }

    /***
     * This function extracts the coefficients for the derivative of a NBSplineTypeI (Mi,k(t))
     * 
     * @param left
     * @param rite
     * @param knots : list containing full knots with duplications
     * @param basisIndex index refers to the starting location of knots in knots
     * @return
     */
    public static double[][] extractDerivativeCoeff(NBSplinesTypeI left, NBSplinesTypeI rite, List<Double> knots, 
                                                    int basisIndex, double parentConst) {
        double[][] coeffsLeft = extractCoeffs(left, basisIndex, parentConst);
        double[][] coeffsRite = extractCoeffs(rite, basisIndex+1, parentConst);
        ArrayUtils.mult(coeffsRite, -1.0);
        double[][] combinedCoeffs = new double[knots.size()-1][];
        sumCoeffs(coeffsLeft, coeffsRite, combinedCoeffs);
        return combinedCoeffs;
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
        
        List<Double> knotsWithDuplicates = fillKnots(knots, order); // knot sequence over which to perform integration
        NBSplinesTypeIDerivative[] allDerivatives = formDerivatives(numBasis, order, knotsWithDuplicates);
        double[][] penaltyMat = new double[numBasis][numBasis];
        for (int i=0; i < numBasis; i++) {
            for (int j = i; j < numBasis; j++) {
                double[][] coeffProduct = formDerivateProduct(i, j, allDerivatives);
                penaltyMat[i][j] = integratePolynomial(knotsWithDuplicates, coeffProduct);
                penaltyMat[j][i] = penaltyMat[i][j];
            }
        }
        return penaltyMat;
    }

    /***
     * Method to generate an array of derivatives of NBSplineTypeI.  
     * 
     * @param numBasis: integer representing number of basis functions for knot sequence
     * @param order: order of NBSplineTypeI to generate
     * @param fullKnots: complete knot sequence with duplicate knots at both ends
     * @return
     */
    public static NBSplinesTypeIDerivative[] formDerivatives(int numBasis, int order, List<Double> fullKnots) {
        NBSplinesTypeIDerivative[] allDerivs = new NBSplinesTypeIDerivative[numBasis];
        for (int index=0; index<numBasis; index++)
            allDerivs[index] = new NBSplinesTypeIDerivative(index, order, fullKnots); // dMi,k(t)/dt
        return allDerivs;
    }

    /***
     * Form product of derivative basis function for index firstIndex, secondIndex
     * @param firstIndex
     * @param secondIndex
     * @param allDeriv
     * @return
     */
    public static double[][] formDerivateProduct(int firstIndex, int secondIndex, NBSplinesTypeIDerivative[] allDeriv) {
        double[][] firstCoeff = allDeriv[firstIndex]._coeffs;
        double[][] secondCoeff = allDeriv[secondIndex]._coeffs;
        int numBasis = firstCoeff.length;
        double[][] polyProduct = new double[numBasis][];
        for (int index=0; index<numBasis; index++) {
            if (firstCoeff[index] != null && secondCoeff[index] != null)
                polyProduct[index] = polynomialProduct(firstCoeff[index], secondCoeff[index]);
        }
        return polyProduct;
    }
}
