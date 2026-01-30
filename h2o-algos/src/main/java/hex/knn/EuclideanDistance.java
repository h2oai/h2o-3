package hex.knn;

public class EuclideanDistance extends KNNDistance {
    
    @Override
    public double nom(double v1, double v2) {
        return (v1-v2)*(v1-v2);
    }

    @Override
    public void calculateValues(double v1, double v2) {
        this.values[0] += nom(v1, v2);
    }

    @Override
    public double result() {
        return Math.sqrt(this.values[0]);
    }


}
