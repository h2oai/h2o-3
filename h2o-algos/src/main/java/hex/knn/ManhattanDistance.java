package hex.knn;

public class ManhattanDistance extends KNNDistance {

    @Override
    public double nom(double v1, double v2) {
        return Math.abs(v1-v2);
    }
    
    @Override
    public void calculateValues(double v1, double v2) {
        this.values[0] += nom(v1, v2);
    }
    
    @Override
    public double result() {
        return this.values[0];
    }

}
