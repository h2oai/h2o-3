package water.util;

import java.util.ArrayList;
import java.util.Arrays;

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
    
    public static class AccumulatedComparisonResult {
        private ArrayList<Difference> _differences = null;
        
        public void addDifference(Difference difference) {
            if (_differences == null) _differences = new ArrayList<>();
            _differences.add(difference);
        }
        
        public AccumulatedComparisonResult merge(AccumulatedComparisonResult other) {
            if (_differences == null) _differences = other._differences;
            if (other._differences != null) _differences.addAll(other._differences);
            return this;
        }
        
        public void compareValuesUpToTolerance(String description, double first, double second, double proportionalTolerance) {
            if (!ComparisonUtils.compareValuesUpToTolerance(first, second, proportionalTolerance)) {
                Difference difference = new Difference(description, first, second, proportionalTolerance);
                addDifference(difference);
            }
        }

        public void compareValuesUpToTolerance(String description, double[] first, double[] second, double proportionalTolerance) {
            if (!ComparisonUtils.compareValuesUpToTolerance(first, second, proportionalTolerance)) {
                String firstString = Arrays.toString(first);
                String secondString = Arrays.toString(second);
                Difference difference = new Difference(description, firstString, secondString, proportionalTolerance);
                addDifference(difference);
            }
        }

        public void compareValuesUpToTolerance(String description, float[] first, float[] second, double proportionalTolerance) {
            if (!ComparisonUtils.compareValuesUpToTolerance(first, second, proportionalTolerance)) {
                String firstString = Arrays.toString(first);
                String secondString = Arrays.toString(second);
                Difference difference = new Difference(description, firstString, secondString, proportionalTolerance);
                addDifference(difference);
            }
        }
        
        public void compare(String description, long first, long second) {
            if (first != second) {
                Difference difference = new Difference(description, first, second, 0.0);
                addDifference(difference);
            }
        }

        public void compare(String description, long[] first, long[] second) {
            if (!Arrays.equals(first, second)) {
                String firstString = Arrays.toString(first);
                String secondString = Arrays.toString(second);
                Difference difference = new Difference(description, firstString, secondString, 0.0);
                addDifference(difference);
            }
        }
        
        public boolean isEqual() { return _differences == null; }

        @Override
        public String toString() {
            if (_differences == null) {
                return "Equal";
            } else {
                StringBuilder builder = new StringBuilder();
                builder.append("Differences [");
                boolean first = true;
                for (Difference difference : _differences) {
                    if (first) {
                        first = false;
                    } else {
                        builder.append(", ");
                    }
                    builder.append(difference.toString());
                }
                builder.append("]");
                return builder.toString();
            }
        }
    }
    
    public static class Difference {
        private final String _description;
        private final Object _first;
        private final Object _second;
        private final double _proportionalTolerance;
        
        Difference(String description, Object first, Object second, double proportionalTolerance) {
            _description = description;
            _first = first;
            _second = second;
            _proportionalTolerance = proportionalTolerance;
        }
        
        public String getDescription() {
            return _description;
        }
        
        public Object getFirst() {
            return _first;
        }
        
        public Object getSecond() {
            return _second;
        }
        
        public double getProportionalTolerance() {
            return _proportionalTolerance;
        }

        @Override
        public String toString() {
            return String.format("%s (%s vs. %s)", _description, _first.toString(), _second.toString());
        }
    }
}
