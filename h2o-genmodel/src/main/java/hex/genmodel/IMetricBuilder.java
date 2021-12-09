package hex.genmodel;

public interface IMetricBuilder<T extends IMetricBuilder<T>> {
    double[] perRow(double[] ds, double[] yact);
    
    double[] perRow(double[] ds, double[] yact, double weight, double offset);

    void reduce(Object mb);
    
    Object makeModelMetrics();
}
