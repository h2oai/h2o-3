package hex.gam.GamSplines;

import hex.genmodel.algos.gam.NBSplinesTypeI;

import static hex.gam.GamSplines.NBSplinesUtils.integratePolynomial;
import static hex.genmodel.algos.gam.GamUtilsISplines.*;
import static hex.genmodel.algos.gam.NBSplinesTypeI.*;

public class NBSplinesTypeIDerivative {
    /***
     * This class implements the first or second derivative of NBSpline Type I (derivative of Mi,k(t)) in order to 
     * generate the penalty function described in Section VI.I equation 16 of doc in 
     * the GitHub issue: https://github.com/h2oai/h2o-3/issues/7261.
     * Doc 2 is the doc for M-spline implementation and can be found here: 
     * https://github.com/h2oai/h2o-3/issues/6926
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
        _commonConst = _order * ((_knots[_order] == _knots[0]) ? 0 : 1.0 / (_knots[_order] - _knots[0]));
        _left = formBasisDeriv(fullKnots, _order - 1, basisIndex, fullKnots.length - 1);
        _right = formBasisDeriv(fullKnots, _order - 1, basisIndex + 1, fullKnots.length - 1);
        _coeffs = extractDerivativeCoeff(_left, _right, fullKnots, basisIndex, _commonConst);

    }

    /***
     * This function extracts the coefficients for the derivative of a NBSplineTypeI (Mi,k(t)) as described in Section
     * VI of doc.
     */
    public static double[][] extractDerivativeCoeff(NBSplinesTypeI left, NBSplinesTypeI rite, double[] knots,
                                                    int basisIndex, double parentConst) {
        double[][] coeffsLeft = extractCoeffs(left, basisIndex, parentConst);
        double[][] coeffsRite = extractCoeffs(rite, basisIndex + 1, -parentConst);
        double[][] combinedCoeffs = new double[knots.length - 1][];
        sumCoeffs(coeffsLeft, coeffsRite, combinedCoeffs);
        return combinedCoeffs;
    }


    /***
     * Generate penalty matrix for I-spline as described in Section VI of doc.
     *
     * @param knots : containing all knots without duplication
     * @param order : order of original I-splines
     * @return double[][] array of size number of basis function by number of total numbers
     */
    public static double[][] genISPenaltyMatrix(double[] knots, int order) {
        int numBasis = knots.length + order - 2;
        if (order <= 1)
            return new double[numBasis][numBasis];  // derivative of order 1 NBSpline will generate all 0

        double[] knotsWithDuplicates = fillKnots(knots, order); // knot sequence over which to perform integration
        NBSplinesTypeIDerivative[] allDerivatives = form1stOrderDerivatives(numBasis, order, knotsWithDuplicates);
        double[][] penaltyMat = new double[numBasis][numBasis];
        for (int i = 0; i < numBasis; i++) {
            for (int j = i; j < numBasis; j++) {
                double[][] coeffProduct = formDerivateProduct(allDerivatives[i]._coeffs, allDerivatives[j]._coeffs);
                penaltyMat[i][j] = integratePolynomial(knotsWithDuplicates, coeffProduct);
                penaltyMat[j][i] = penaltyMat[i][j];
            }
        }
        return penaltyMat;
    }

    /***
     * Generate penalty matrix for M-spline as described in Section III of doc 2.
     *
     * @param knots : containing all knots without duplication
     * @param order : order of original I-splines
     * @return double[][] array of size number of basis function by number of total numbers
     */
    public static double[][] genMSPenaltyMatrix(double[] knots, int order) {
        int numBasis = knots.length + order - 2;
        if (order <= 2)
            return new double[numBasis][numBasis];  // derivative of order 2 NBSpline will generate all 0

        double[] knotsWithDuplicates = fillKnots(knots, order); // knot sequence over which to perform integration
        double[][][] allDerivCoeffs = form2ndDerivCoeffs(numBasis, order, knotsWithDuplicates);
        
        double[][] penaltyMat = new double[numBasis][numBasis];
        for (int i = 0; i < numBasis; i++) {
            for (int j = i; j < numBasis; j++) {
                double[][] coeffProduct = formDerivateProduct(allDerivCoeffs[i], allDerivCoeffs[j]);
                penaltyMat[i][j] = integratePolynomial(knotsWithDuplicates, coeffProduct);
                penaltyMat[j][i] = penaltyMat[i][j];
            }
        }
        return penaltyMat;
    }

    public static double[][][] form2ndDerivCoeffs(int numBasis, int order, double[] fullKnots) {
        double[][][] derivCoeffs = new double[numBasis][][];
        NBSplinesTypeI[] msBasis = new NBSplinesTypeI[numBasis];
        int numKnotInt = fullKnots.length-1;

        for (int index = 0; index < numBasis; index++) {
            msBasis[index] = formBasisDeriv(fullKnots, order, index, numKnotInt);
            // extract coefficients of spline
            extractNBSplineCoeffs(msBasis[index], order, new double[]{1}, 1, index);
            // take 2nd derivative of Mspline by dealing with the coefficients
            derivCoeffs[index] = derivativeCoeffs(msBasis[index]._nodeCoeffs);
        }
        return derivCoeffs;
    }

    public static double[][] derivativeCoeffs(double[][] origCoeffs) {
        int numCoeffs = origCoeffs.length;
        double[][] derivCoeffs = new double[numCoeffs][];
        for (int index=0; index<numCoeffs; index++) {
            double[] currCoeffs = origCoeffs[index];
            if (currCoeffs != null && currCoeffs.length > 2) {
                int count = 0;
                int currCoeffLen = currCoeffs.length;
                derivCoeffs[index] = new double[currCoeffLen-2];
                for (int index2=2; index2<currCoeffLen; index2++)
                    derivCoeffs[index][count++] = currCoeffs[index2]*index2*(index2-1);
           }
        }
        return derivCoeffs;
    }

    /***
     * Method to generate an array of derivatives of NBSplineTypeI.  See Section VI.I of doc.
     * 
     * @param numBasis: integer representing number of basis functions for knot sequence
     * @param order: order of NBSplineTypeI to generate
     * @param fullKnots: complete knot sequence with duplicate knots at both ends
     */
    public static NBSplinesTypeIDerivative[] form1stOrderDerivatives(int numBasis, int order, double[] fullKnots) {
        NBSplinesTypeIDerivative[] allDerivs = new NBSplinesTypeIDerivative[numBasis];
        for (int index=0; index<numBasis; index++)
            allDerivs[index] = new NBSplinesTypeIDerivative(index, order, fullKnots); // dMi,k(t)/dt
        return allDerivs;
    }

    /***
     * Form product of derivative basis function for index firstIndex, secondIndex like M'i,k(t)*M'j,k(t). See Section
     * VI.I of doc.
     */
    public static double[][] formDerivateProduct(double[][] firstCoeff, double[][] secondCoeff) {
        int numBasis = firstCoeff.length;
        double[][] polyProduct = new double[numBasis][];
        for (int index=0; index<numBasis; index++) {
            if (firstCoeff[index] != null && secondCoeff[index] != null)
                polyProduct[index] = polynomialProduct(firstCoeff[index], secondCoeff[index]);
        }
        return polyProduct;
    }
}
