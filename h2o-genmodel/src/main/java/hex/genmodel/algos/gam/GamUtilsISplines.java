package hex.genmodel.algos.gam;

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

    static double[] formNumerator(int order, double[] knots) {
        double[] numerator = new double[2];
        if (order == 1) {
            numerator[0] = 1;
            numerator[1] = 1;
        } else {
            numerator[0] = knots[0];
            numerator[1] = knots[order];
        }
        return numerator;
    }

    static double[] formDenominator(int order, double[] knots) {
        double[] oneOverDenominator = new double[2];
        if (order == 1) {
            oneOverDenominator[0] = 1;
            oneOverDenominator[1] = 1;
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
}
