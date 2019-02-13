package hex.mojo;

import hex.*;
import hex.genmodel.MojoModel;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.H2O;
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
    public MojoDelegatingModel(Key<MojoDelegatingModel> selfKey, MojoDelegatingModelParameters parms, MojoDelegatingModelOutput output, MojoModel mojoModel) {
        // TODO: We might want to add reference for training and validation frame to re-construct metrics if we do not choose to get them from JSON inside MOJO
        super(selfKey, parms, output);
        _mojoModel = mojoModel;
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        // TODO : incomplete. We've got to support all of them ! Maybe automate this ?
        switch(_output.getModelCategory()) {
            case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
            case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(),domain);
            case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
            case AnomalyDetection: return new ModelMetricsAnomaly.MetricBuilderAnomaly();
            default: throw H2O.unimpl();
        }
    }
    
    @Override
    protected double[] score0(double[] data, double[] preds) {
        return _mojoModel.score0(data,preds);
    }
}
