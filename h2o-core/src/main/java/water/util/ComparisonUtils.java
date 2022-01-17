package water.util;

public class ComparisonUtils {
    public static boolean compareValuesUpToTolerance(float[] first, float[] second, double proportionalTolerance) {
        if (first == null) {
            return second == null;
        }
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            if(!compareValuesUpToTolerance(first[i], second[i], proportionalTolerance)) return false;
        }
        return true;
    }

    public static boolean compareValuesUpToTolerance(double[] first, double[] second, double proportionalTolerance) {
        if (first == null) {
            return second == null;
        }
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            if(!compareValuesUpToTolerance(first[i], second[i], proportionalTolerance)) return false;
        }
        return true;
    }
    
    public static boolean compareValuesUpToTolerance(double first, double second, double proportionalTolerance) {
        if (Double.isNaN(first)) {
            return Double.isNaN(second);
        }

        if (first == Double.POSITIVE_INFINITY) {
            return second == Double.POSITIVE_INFINITY;
        }

        if (first == Double.NEGATIVE_INFINITY) {
            return second == Double.NEGATIVE_INFINITY;
        }

        // covers scenario when both are 0.0.
        if (first == second) {
            return true;
        }

        double ratio = first / second;
        if (ratio < 0) {
            return false;
        }

        double difference = Math.abs(ratio - 1);
        
        return difference <= proportionalTolerance;
    }
}
