package hex.genmodel.easy.prediction;

public class KLimeModelPrediction extends RegressionModelPrediction {
    /**
     * Chosen cluster for this data point.
     */
    public int cluster;

    /**
     * Array of reason codes. Each element of the array corresponds to a feature used in model training.
     * Order of the codes is given by the order of columns in the model.
     */
    public double[] reasonCodes;
}
