package water.util;

import hex.ConfusionMatrix;
import hex.GainsLift;

import java.util.*;

public class ComparisonUtils {
    public static abstract class MetricComparator {
        private ArrayList<Difference> _differences = null;
        
        public void addDifference(Difference difference) {
            if (_differences == null) _differences = new ArrayList<>();
            _differences.add(difference);
        }
        
        public abstract void compareUpToTolerance(String description, double first, double second);

        public abstract void compareUpToTolerance(String description, double[] first, double[] second);

        public abstract void compareUpToTolerance(String description, float[] first, float[] second);

        public abstract void compareUpToTolerance(String description, ConfusionMatrix first, ConfusionMatrix second);

        public abstract void compareUpToTolerance(String description, GainsLift first, GainsLift second);
        
        public abstract void compare(String description, long first, long second);

        public abstract void compare(String description, long[] first, long[] second);
        
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
        
        Difference(String description, Object first, Object second) {
            _description = description;
            _first = first;
            _second = second;
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

        @Override
        public String toString() {
            return String.format("%s (%s vs. %s)", _description, _first.toString(), _second.toString());
        }
    }

    public static class RelativeToleranceMetricComparator extends MetricComparator {
        private final double _relativeTolerance;
        
        private static final Set<String> _ignoredFields = Collections.singleton("lift_top_group");
        
        public RelativeToleranceMetricComparator(double relativeTolerance) {
            _relativeTolerance = relativeTolerance;
        }

        public void compareUpToTolerance(String description, double first, double second) {
            if (_ignoredFields.contains(description)) return;
            if (!compareUpToTolerance(first, second, _relativeTolerance)) {
                Difference difference = new Difference(description, first, second);
                addDifference(difference);
            }
        }

        private static boolean compareUpToTolerance(float[] first, float[] second, double relativeTolerance) {
            if (first == null) {
                return second == null;
            }
            if (first.length != second.length) {
                return false;
            }
            for (int i = 0; i < first.length; i++) {
                if(!compareUpToTolerance(first[i], second[i], relativeTolerance)) return false;
            }
            return true;
        }

        public void compareUpToTolerance(String description, double[] first, double[] second) {
            if (_ignoredFields.contains(description)) return;
            if (!compareUpToTolerance(first, second, _relativeTolerance)) {
                String firstString = Arrays.toString(first);
                String secondString = Arrays.toString(second);
                Difference difference = new Difference(description, firstString, secondString);
                addDifference(difference);
            }
        }

        private static boolean compareUpToTolerance(double[] first, double[] second, double relativeTolerance) {
            if (first == null) {
                return second == null;
            }
            if (first.length != second.length) {
                return false;
            }
            for (int i = 0; i < first.length; i++) {
                if(!compareUpToTolerance(first[i], second[i], relativeTolerance)) return false;
            }
            return true;
        }

        public void compareUpToTolerance(String description, float[] first, float[] second) {
            if (_ignoredFields.contains(description)) return;
            if (!compareUpToTolerance(first, second, _relativeTolerance)) {
                String firstString = Arrays.toString(first);
                String secondString = Arrays.toString(second);
                Difference difference = new Difference(description, firstString, secondString);
                addDifference(difference);
            }
        }

        @Override
        public void compareUpToTolerance(String description, ConfusionMatrix first, ConfusionMatrix second) {
            // Not supported yet
        }

        @Override
        public void compareUpToTolerance(String description, GainsLift first, GainsLift second) {
            // Not supported yet
        }

        private static boolean compareUpToTolerance(double first, double second, double relativeTolerance) {
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

            double difference = Math.abs(first - second) / Math.max(Math.abs(first), Math.abs(second));

            return difference <= relativeTolerance;
        }

        public void compare(String description, long first, long second) {
            if (_ignoredFields.contains(description)) return;
            if (first != second) {
                Difference difference = new Difference(description, first, second);
                addDifference(difference);
            }
        }

        public void compare(String description, long[] first, long[] second) {
            if (_ignoredFields.contains(description)) return;
            if (!Arrays.equals(first, second)) {
                String firstString = Arrays.toString(first);
                String secondString = Arrays.toString(second);
                Difference difference = new Difference(description, firstString, secondString);
                addDifference(difference);
            }
        }

        @Override
        public String toString() {
            String genericInformation = super.toString();
            return String.format("relative tolerance %f - %s", _relativeTolerance, genericInformation);
        }
    }
}
