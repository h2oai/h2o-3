package hex.knn;

public class EuclideanDistance extends KNNDistance {
    
    @Override
    public double nom(double v1, double v2) {
        return (v1-v2)*(v1-v2);
    }

    @Override
    public double[] calculateValues(double v1, double v2, double[] values) {
        values[0] += nom(v1, v2);
        return values;
    }

    @Override
    public double result(double[] values) {
        assert values.length == 1;
        return Math.sqrt(values[0]);
    }


}
