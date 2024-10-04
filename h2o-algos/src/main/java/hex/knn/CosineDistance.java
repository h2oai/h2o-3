package hex.knn;

public class CosineDistance extends KNNDistance{
    
    public CosineDistance(){
        super.valuesLength = 3;
    }
    
    @Override
    public double nom(double[] v1, double[] v2) {
        assert v1.length == v2.length;
        int size = v1.length;
        double vSum = 0;
        for (int i = 0; i < size; i++) {
            vSum += v1[i] * v2[i];
        }
        return vSum;
    }

    @Override
    public double denom(double[] v1, double[] v2) {
        assert v1.length == v2.length;
        int size = v1.length;
        double v1Sum = 0;
        double v2Sum = 0;
        for (int i = 0; i < size; i++) {
            v1Sum += (v1[i]*v1[i]);
            v2Sum += (v2[i]*v2[i]);
        }
        return Math.sqrt(v1Sum) * Math.sqrt(v2Sum);
    }

    @Override
    public double value(double[] v1, double[] v2) {
        return nom(v1, v2) / denom(v1, v2);
    }
    
    @Override
    public double denom(double v1, double v2) {
        return Math.sqrt(v1);
    }

    @Override
    public double value(double nom, double denom) {
        return nom/denom;
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
        return (values[0]/ Math.pow(values[1], 2)) * (values[0]/ Math.pow(values[2], 2));
    }
}
