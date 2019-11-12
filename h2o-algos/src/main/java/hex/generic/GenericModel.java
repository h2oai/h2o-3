package hex.generic;

import hex.*;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.algos.kmeans.KMeansMojoModel;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.H2O;
import water.Key;
import water.fvec.ByteVec;

import java.io.IOException;

public class GenericModel extends Model<GenericModel, GenericModelParameters, GenericModelOutput> {

    private transient MojoModel _mojoModel;
    private ByteVec _mojoBytes;
    
    /**
     * Full constructor
     *
     */
    public GenericModel(Key<GenericModel> selfKey, GenericModelParameters parms, GenericModelOutput output, MojoModel mojoModel, ByteVec mojoBytes) {
        super(selfKey, parms, output);
        _mojoBytes = mojoBytes;
        _mojoModel = mojoModel;
        _output = new GenericModelOutput(_mojoModel._modelDescriptor, _mojoModel._modelAttributes);

    }

    private static MojoModel reconstructMojo(ByteVec mojoBytes) {
        try {
            final MojoReaderBackend readerBackend = MojoReaderBackendFactory.createReaderBackend(mojoBytes.openStream(null), MojoReaderBackendFactory.CachingStrategy.MEMORY);
            return ModelMojoReader.readFrom(readerBackend, true);
        } catch (IOException e) {
            throw new IllegalStateException("Unreachable MOJO file: " + mojoBytes._key, e);
        }
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
        if (_mojoModel == null) {
            assert _mojoBytes != null;
            _mojoModel = reconstructMojo(_mojoBytes);
        }
        return _mojoModel.score0(data,preds);
    }

    @Override
    public GenericModelMojoWriter getMojo() {
        return new GenericModelMojoWriter(_mojoBytes);
    }
}
