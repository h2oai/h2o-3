package hex.gam.GamSplines;

import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class NBSplinesUtils {
    /**
     * Given two polynomial coefficients represented as double arrays, multiply and combine them into one array
     * 
     * @param coeff1
     * @param coeff2
     * @return
     */
    public static double[] polynomialProduct(double[] coeff1, double[] coeff2) {
        int firstLen = coeff1.length;
        int secondLen = coeff2.length;
        int combinedLen = firstLen*secondLen;
        int[] firstOrder = IntStream.rangeClosed(0, firstLen-1).toArray();
        int[] secondOrder = IntStream.rangeClosed(0, secondLen-1).toArray();
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
    
    public static double integratePolynomial(List<Double> knotsWithDuplicates, double[][] coeffProduct) {
        double sumValue = 0;
        int numBasis = coeffProduct.length;

        for (int index=0; index < numBasis; index++) {
            if (coeffProduct[index] != null) {
                int orderSize = coeffProduct[index].length;
                double firstKnot = knotsWithDuplicates.get(index);
                double secondKnot = knotsWithDuplicates.get(index+1);
                double[] coeffs = coeffProduct[index];
                double tempSum = 0;
                for (int orderIndex = 0; orderIndex < orderSize; orderIndex++) {
                    tempSum += coeffs[orderIndex]/(orderIndex+1)*(Math.pow(secondKnot, orderIndex+1)-
                            Math.pow(firstKnot, orderIndex+1));
                }
                sumValue += tempSum;
            }
        }
        return sumValue;
    }
}
