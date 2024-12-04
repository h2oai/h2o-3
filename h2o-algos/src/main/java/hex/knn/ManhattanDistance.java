package hex.knn;

public class ManhattanDistance extends KNNDistance {

    @Override
    public double nom(double v1, double v2) {
        return Math.abs(v1-v2);
    }
    
    @Override
    public double[] calculateValues(double v1, double v2, double[] values) {
        values[0] += nom(v1, v2);
        return values;
    }
    
    @Override
    public double result(double[] values) {
        assert values.length == 1;
        return values[0];
    }

}
