package hex.deepwater.keras;

import hex.Model;
import hex.ModelMetrics;
import hex.deepwater.DeepWaterModelOutput;
import hex.deepwater.DeepWaterParameters;
import water.Key;

/**
 * Created by fmilo on 8/12/16.
 *
public class KerasScriptModel extends Model<KerasScriptModel,DeepWaterParameters,DeepWaterModelOutput> {

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        return null;
    }

    public KerasScriptModel(Key selfKey, DeepWaterParameters parms, DeepWaterModelOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    protected double[] score0(double[] data, double[] preds) {
        return new double[0];
    }

}
*/