package hex.genmodel.algos.gam;

import hex.genmodel.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

import static hex.genmodel.utils.ArrayUtils.arrayInitRange;

public class GamUtilsISplines {
    public static double[] fillKnots(double[] knots, int m) {
        int numKnotsDup = knots.length+2*m-2;
        double[] knotsNew = new double[numKnotsDup];
        int upperBound = m > 0?m-1:0;
        for (int index=0; index < upperBound; index++)    // m lower knots, all equal value
            knotsNew[index]=knots[0];
        int knotLen = knots.length;
        for (int index=0; index < knotLen; index++) // N-2 interior knots
            knotsNew[index+upperBound]=knots[index];
        double upperVal = knots[knots.length-1];
        for (int index=knotLen+upperBound; index < numKnotsDup; index++)
            knotsNew[index]=upperVal;
        return knotsNew;
    }

    /**
     * This method is used to extract the knots over which a basis function is supposed to be non-zero.
     */
    public static double[] extractKnots(int index, int order, double[] knots) {
        double[] newKnots = new double[order+1];
        int upperIndex = Math.min(index+order, knots.length-1);
        int startIndex = 0;
        for (int counter = index; counter <= upperIndex; counter++)
            newKnots[startIndex++]=knots[counter];
        return newKnots;
    }

    static double[] formDenominatorNSpline(int order, double[] knots) {
        double[] oneOverDenominator = new double[2];
        if (order == 1) {
            oneOverDenominator[0] = 1;
            oneOverDenominator[1] = 0;
        } else {
            double tempDenom = knots[order-1]-knots[0];
            oneOverDenominator[0] = tempDenom==0
                    ? 0 : 1.0/tempDenom;
            tempDenom = knots[order]-knots[1];
            oneOverDenominator[1] = tempDenom==0
                    ? 0 : 1.0/tempDenom;
        }
        return oneOverDenominator;
    }

    static double[] formNumerator(int order, double[] knots) {
        double[] numerator = new double[2];
        if (order == 1) {
            numerator[0] = 1;
            numerator[1] = 0;
        } else {
            numerator[0] = knots[0];
            numerator[1] = knots[order];
        }
        return numerator;
    }

    static double[] formDenominatorMSpline(int order, double[] knots) {
        double[] oneOverDenominator = new double[2];
        if (order == 1) {
            oneOverDenominator[0] = 1;
            oneOverDenominator[1] = 0;
        } else {
            double tempDenom = knots[order]-knots[0];
            oneOverDenominator[0] = tempDenom==0
                    ? 0 : 1.0/tempDenom;
            tempDenom = knots[order]-knots[0];
            oneOverDenominator[1] = tempDenom==0
                    ? 0 : 1.0/tempDenom;
        }
        return oneOverDenominator;
    }
    
    /**
     * This method performs the multiplication of two polynomials where the polynomials are given as a double
     * array.  This will result in another array which contains the multiplication of the two polynomials.
     */
    public static double[] polynomialProduct(double[] coeff1, double[] coeff2) {
        int firstLen = coeff1.length;
        int secondLen = coeff2.length;
        int combinedLen = firstLen*secondLen;
        int[] firstOrder = arrayInitRange(firstLen, 0);
        int[] secondOrder = arrayInitRange(secondLen, 0);
        int highestOrder = firstLen+secondLen-2;
        double[] combinedCoefficients = new double[highestOrder+1]; // start with order 0
        List<Double> combinedC = new ArrayList<>();
        List<Integer> combinedOrder = new ArrayList<>();
        for (int firstIndex=0; firstIndex < firstLen; firstIndex++) {
            for (int secondIndex=0; secondIndex < secondLen; secondIndex++) {
                double tempValue = coeff1[firstIndex]*coeff2[secondIndex];
                combinedC.add(tempValue);
                int tempOrder = firstOrder[firstIndex]+secondOrder[secondIndex];
                combinedOrder.add(tempOrder);
            }
        }
        for (int index = 0; index < combinedLen; index++) {
            combinedCoefficients[combinedOrder.get(index)] += combinedC.get(index);

        }
        return combinedCoefficients;
    }
    
    /**
     * Extract coefficients for a node as in equation 5, 11 or 16 by combining the constants, additional
     * polynomials with polynomials from nodes of lower orders.
     */
    public static void combineParentCoef(double[] parentCoeff, double parentConst, double[][] currCoeff) {
        int numBasis = currCoeff.length;
        double[] copyParentCoef = parentCoeff.clone();
        ArrayUtils.mult(copyParentCoef, parentConst);
        for (int index = 0; index < numBasis; index++) {
            if (currCoeff[index] != null) {
                currCoeff[index] = polynomialProduct(copyParentCoef, currCoeff[index]);
            }
        }
    }

    /**
     * Perform sum of two polynomials resulting in a double[] representing the result of the summation.
     */
    public static void sumCoeffs(double[][] leftCoeffs, double[][] riteCoeffs, double[][] currCoeffs) {
        int knotInt = leftCoeffs.length;
        for (int index=0; index < knotInt; index++) {
            double[] leftCoef1 = leftCoeffs[index];
            double[] riteCoef1 = riteCoeffs[index];
            if (leftCoef1 != null || riteCoef1 != null) {
                if (leftCoef1 != null && riteCoef1 != null) {
                    currCoeffs[index] = addCoeffs(leftCoef1, riteCoef1);
                } else if (leftCoef1 != null) {
                    currCoeffs[index] = leftCoef1.clone();
                } else { // only riteCoef1 is not null
                    currCoeffs[index] = riteCoef1.clone();
                }
            }
        }
    }

    /***
     * Perform summation of coefficients from the left and rite splines with lower order.
     */
    public static double[] addCoeffs(double[] leftCoef, double[] riteCoef) {
        int leftLen = leftCoef.length;
        int riteLen = riteCoef.length;
        int coeffLen = Math.max(leftLen, riteLen);
        double[] sumCoeffs = new double[coeffLen];
        for (int index=0; index<coeffLen; index++) {
            double val = 0;
            if (index < leftLen)
                val += leftCoef[index];
            if (index < riteLen)
                val += riteCoef[index];
            sumCoeffs[index] = val;
        }
        return sumCoeffs;
    }
}
