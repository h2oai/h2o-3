package hex.knn;

import water.Iced;

public abstract class KNNDistance extends Iced<KNNDistance> {
    
    public int valuesLength = 1;

    public abstract double nom(double v1, double v2);
    
    public double[] initializeValues(){
        return  new double[valuesLength];
    }
    
    public abstract double[] calculateValues(double v1, double v2, double[] values);
    
    public abstract double result(double[] values);
    
}
