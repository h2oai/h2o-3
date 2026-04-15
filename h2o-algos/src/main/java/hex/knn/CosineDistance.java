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
    public void calculateValues(double v1, double v2) {
        this.values[0] += nom(v1, v2);
        this.values[1] += nom(v1, v1);
        this.values[2] += nom(v2, v2);
    }

    @Override
    public double result() {
        return 1 - (this.values[0] / (Math.sqrt(this.values[1]) * Math.sqrt(this.values[2])));
    }
}
