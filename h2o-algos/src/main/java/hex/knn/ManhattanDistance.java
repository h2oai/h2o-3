package hex.knn;

public class ManhattanDistance extends KNNDistance{
    
    @Override
    public double nom(double[] v1, double[] v2) {
        assert v1.length == v2.length;
        int size = v1.length;
        double residualSum = 0;
        for (int i = 0; i < size; i++) {
            residualSum += Math.abs(v1[i]-v2[i]);
        }
        return residualSum;
    }

    @Override
    public double denom(double[] v1, double[] v2) {
        return 1;
    }

    @Override
    public double value(double[] v1, double[] v2) {
        return Math.sqrt(nom(v1, v2));
    }

    @Override
    public double denom(double v1, double v2) {
        return 0;
    }

    @Override
    public double value(double nom, double denom) {
        return Math.sqrt(nom);
    }


    

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
        return Math.sqrt(values[0]);
    }

}
