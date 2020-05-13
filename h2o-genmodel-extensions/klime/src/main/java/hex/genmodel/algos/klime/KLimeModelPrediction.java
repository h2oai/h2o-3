package hex.genmodel.algos.klime;

import hex.genmodel.easy.prediction.RegressionModelPrediction;

public class KLimeModelPrediction extends RegressionModelPrediction {
    /**
     * Chosen cluster for this data point.
     */
    public int cluster;

    /**
     * Array of reason code. Each element of the array corresponds to a feature used in model training.
     * Order of the codes is given by the order of columns in the model.
     */
    public double[] reasonCodes;
}