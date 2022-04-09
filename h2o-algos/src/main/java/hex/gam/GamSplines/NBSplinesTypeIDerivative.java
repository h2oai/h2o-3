package hex.gam.GamSplines;

import water.util.ArrayUtils;

import static hex.gam.GamSplines.NBSplinesTypeI.formBasis;
import static hex.gam.GamSplines.NBSplinesTypeI.extractCoeffs;
import static hex.gam.GamSplines.NBSplinesUtils.*;
import static hex.genmodel.algos.gam.GamUtilsISplines.extractKnots;
import static hex.genmodel.algos.gam.GamUtilsISplines.fillKnots;

public class NBSplinesTypeIDerivative {
    /***
     * This class implements the derivative of NBSpline Type I (derivative of Mi,k(t)) in order to generate the 
     * penalty function described in Section VI.I equation 16 of doc in JIRA: https://h2oai.atlassian.net/browse/PUBDEV-8398.  
     */
    private final int _order; // order k as in derivative of Mi,k(t)
    private final int _basisIndex; // index i
    private final double[] _knots;  // knots sequence with duplication
    private final double _commonConst;  // k/(ti+k-ti)
    public double[][] _coeffs; // store coefficients for derivate of Mi,k(t) for knot intervals where spline is non-zero
    private NBSplinesTypeI _left;   // point to spline Mi,k-1(t)
    private NBSplinesTypeI _right;   // point to spline Mi+1,k-1(t)
    
    public NBSplinesTypeIDerivative(int basisIndex, int order, double[] fullKnots) {
        _order = order;
        _basisIndex = basisIndex;
        _knots = extractKnots(_basisIndex, order, fullKnots);   // extract knot sequence over which spline is non-zero
        _commonConst = _order*((_knots[_order] == _knots[0]) ? 0 : 1.0/(_knots[_order]-_knots[0]));
        _left = formBasis(_knots, _order-1, basisIndex, 0, fullKnots.length-1);
        _right = formBasis(_knots, _order-1, basisIndex, 1, fullKnots.length-1);
        _coeffs = extractDerivativeCoeff(_left, _right, fullKnots, basisIndex, _commonConst);
    }

    /***
     * This function extracts the coefficients for the derivative of a NBSplineTypeI (Mi,k(t)) as described in Section
     * VI of doc.
     */
    public static double[][] extractDerivativeCoeff(NBSplinesTypeI left, NBSplinesTypeI rite, double[] knots, 
                                                    int basisIndex, double parentConst) {
        double[][] coeffsLeft = extractCoeffs(left, basisIndex, parentConst);
        double[][] coeffsRite = extractCoeffs(rite, basisIndex+1, parentConst);
        ArrayUtils.mult(coeffsRite, -1.0); // -1 for second lower order spline as in equation 16
        double[][] combinedCoeffs = new double[knots.length-1][];
        sumCoeffs(coeffsLeft, coeffsRite, combinedCoeffs);
        return combinedCoeffs;
    }
    
    
    /***
     * Generate penalty matrix as described in Section VI of doc.
     * 
     * @param knots : containing all knots without duplication
     * @param order : order of original I-splines
     * @return double[][] array of size number of basis function by number of total numbers
     */
    public static double[][] genPenaltyMatrix(double[] knots, int order) {
        int numBasis = knots.length+order-2;
        if (order <= 1)
            return new double[numBasis][numBasis];  // derivative of order 1 NBSpline will generate all 0
        
        double[] knotsWithDuplicates = fillKnots(knots, order); // knot sequence over which to perform integration
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
     * Method to generate an array of derivatives of NBSplineTypeI.  See Section VI.I of doc.
     * 
     * @param numBasis: integer representing number of basis functions for knot sequence
     * @param order: order of NBSplineTypeI to generate
     * @param fullKnots: complete knot sequence with duplicate knots at both ends
     */
    public static NBSplinesTypeIDerivative[] formDerivatives(int numBasis, int order, double[] fullKnots) {
        NBSplinesTypeIDerivative[] allDerivs = new NBSplinesTypeIDerivative[numBasis];
        for (int index=0; index<numBasis; index++)
            allDerivs[index] = new NBSplinesTypeIDerivative(index, order, fullKnots); // dMi,k(t)/dt
        return allDerivs;
    }

    /***
     * Form product of derivative basis function for index firstIndex, secondIndex like M'i,k(t)*M'j,k(t). See Section
     * VI.I of doc.
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
