package hex.gam.GamSplines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class NBSplinesUtils {
    
    public static List<Double> fillKnots(double[] knots, int m, int N) {
        List<Double> knotsNew = new ArrayList<>();
        int upperBound = m-1;
        for (int index=0; index < upperBound; index++)    // m lower knots, all equal value
            knotsNew.add(knots[0]);
        upperBound = knots.length;
        for (int index=0; index < upperBound; index++) // N-2 interior knots
            knotsNew.add(knots[index]);
        double upperVal = knots[knots.length-1];
        for (int index=0; index < upperBound; index++)
            knotsNew.add(upperVal);
        assert knotsNew.size()==N+2*m-2:"knots length is incorrect";
        return knotsNew;
    }

    /**
     * This method is used to extract the knots over which a basis function is supposed to be non-zero.
     */
    public static List<Double> extractKnots(int index, int order, List<Double> knots) {
        List<Double> newKnots = new ArrayList<Double>();
        int[] validIndices = IntStream.rangeClosed(index, index+order).toArray();
        for (int ind : validIndices)
            newKnots.add(knots.get(ind));
        return newKnots;
    }

    static double[] formNumerator(int index, int order, List<Double> knots) {
        double[] numerator = new double[2];
        if (order == 1) {
            numerator[0] = 1;
            numerator[1] = 1;
        } else {
            numerator[0] = knots.get(index);
            numerator[1] = knots.get(index+order);
        }
        return numerator;
    }
    
    static double[] formDenominator(int index, int order, List<Double> knots) {
        double[] oneOverDenominator = new double[2];
        if (order == 1) {
            oneOverDenominator[0] = 1;
            oneOverDenominator[1] = 1;
        } else {
            oneOverDenominator[0] = 1.0/(knots.get(index+order-1)-knots.get(index));
            oneOverDenominator[1] = 1.0/(knots.get(index+order)-knots.get(index+1));
        }
        return oneOverDenominator;
    }
}
