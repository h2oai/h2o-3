package hex.knn;

public class CosineDistance extends KNNDistance {
    
    public CosineDistance(){
        super.valuesLength = 3;
    }
    
    @Override
    public double nom(double v1, double v2) {
        return v1*v2;
    }

    @Override
    public double[] calculateValues(double v1, double v2, double[] values) {
        assert values.length == valuesLength;
        values[0] += nom(v1, v2);
        values[1] += nom(v1, v1);
        values[2] += nom(v2, v2);
        return values;
    }

    @Override
    public double result(double[] values) {
        assert values.length == valuesLength;
        return 1 - (values[0] / (Math.sqrt(values[1] * values[2])));
    }
}
