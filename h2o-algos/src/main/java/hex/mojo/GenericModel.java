package hex.mojo;

import hex.*;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.kmeans.KMeansMojoModel;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.H2O;
import water.Key;
import water.fvec.ByteVec;

public class GenericModel extends Model<GenericModel, GenericModelParameters, GenericModelOutput> {
    
    private final MojoModel _mojoModel;
    private final ByteVec _mojoBytes;
    
    /**
     * Full constructor
     *
     * @param selfKey
     * @param parms
     * @param output
     */
    public GenericModel(Key<GenericModel> selfKey, GenericModelParameters parms, GenericModelOutput output, MojoModel mojoModel, ByteVec mojoBytes) {
        super(selfKey, parms, output);
        _mojoModel = mojoModel;
        _mojoBytes = mojoBytes;
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        switch(_output.getModelCategory()) {
            case Unknown:
                throw new IllegalStateException("Model category is unknown");
            case Binomial:
                return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
            case Multinomial:
                return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain);
            case Ordinal:
                return new ModelMetricsOrdinal.MetricBuilderOrdinal(_output.nclasses(), domain);
            case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
            case Clustering:
                assert _mojoModel instanceof KMeansMojoModel;
                KMeansMojoModel kMeansMojoModel = (KMeansMojoModel) _mojoModel;
                return new ModelMetricsClustering.MetricBuilderClustering(_output.nfeatures(), kMeansMojoModel.getNumClusters());
            case AutoEncoder:
                return new ModelMetricsAutoEncoder.MetricBuilderAutoEncoder(_output.nfeatures());
            case DimReduction:
                throw new UnsupportedOperationException("DimReduction is not supported.");
            case WordEmbedding:
                throw new UnsupportedOperationException("WordEmbedding is not supported.");
            case CoxPH:
                throw new UnsupportedOperationException("CoxPH is not supported.");
            case AnomalyDetection: return new ModelMetricsAnomaly.MetricBuilderAnomaly("");

            default: throw H2O.unimpl();
        }
    }
    
    @Override
    protected double[] score0(double[] data, double[] preds) {
        return _mojoModel.score0(data,preds);
    }

    @Override
    public ModelMojoWriter getMojo() {
        return new GenericModelMojoWriter(_mojoBytes);
    }
}
