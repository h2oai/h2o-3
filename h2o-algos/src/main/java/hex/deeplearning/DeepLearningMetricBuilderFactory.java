package hex.deeplearning;

import com.google.gson.JsonObject;
import hex.ModelMetricsAutoEncoder;
import hex.ModelMetricsSupervised;
import hex.genmodel.IMetricBuilder;
import hex.genmodel.algos.deeplearning.DeeplearningMojoModel;

public class DeepLearningMetricBuilderFactory extends ModelMetricsSupervised.SupervisedMetricBuilderFactory<DeepLearningModel, DeeplearningMojoModel> {
    
    @Override
    public IMetricBuilder createBuilder(DeeplearningMojoModel mojoModel, JsonObject extraInfo) {
        Boolean autoencoder = (Boolean)mojoModel._modelAttributes.getParameterValueByName("autoencoder");
        if (autoencoder) {
            return new ModelMetricsAutoEncoder.IndependentAutoEncoderMetricBuilder(mojoModel);
        } else {
            return super.createBuilder(mojoModel, extraInfo);
        }
    }
}
