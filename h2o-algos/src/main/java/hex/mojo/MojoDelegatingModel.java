package hex.mojo;

import hex.Model;
import hex.ModelMetrics;
import hex.genmodel.MojoModel;
import water.Key;

public class MojoDelegatingModel extends Model<MojoDelegatingModel, MojoDelegatingModelParameters, MojoDelegatingModelOutput> {
    
    MojoModel _mojoModel;
    
    /**
     * Full constructor
     *
     * @param selfKey
     * @param parms
     * @param output
     */
    public MojoDelegatingModel(Key<MojoDelegatingModel> selfKey, MojoDelegatingModelParameters parms, MojoDelegatingModelOutput output) {
        // TODO: We might want to add reference for training and validation frame to re-construct metrics if we do not choose to get them from JSON inside MOJO
        super(selfKey, parms, output);
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        return null;
    }
    
    

    @Override
    protected double[] score0(double[] data, double[] preds) {
        return _mojoModel.score0(data,preds);
    }
}
