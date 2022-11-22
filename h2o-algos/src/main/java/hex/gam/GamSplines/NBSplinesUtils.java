package hex.gam.GamSplines;

import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class NBSplinesUtils {




    /***
     * Perform integration of polynomials as described in Section VI.IV, equation 17 of doc I.
     */
    public static double integratePolynomial(double[] knotsWithDuplicates, double[][] coeffProduct) {
        double sumValue = 0;
        int numBasis = coeffProduct.length;

        for (int index=0; index < numBasis; index++) {
            if (coeffProduct[index] != null) {
                int orderSize = coeffProduct[index].length;
                double firstKnot = knotsWithDuplicates[index];
                double secondKnot = knotsWithDuplicates[index+1];
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
