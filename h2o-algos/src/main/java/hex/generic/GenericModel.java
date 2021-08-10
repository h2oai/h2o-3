package hex.generic;

import hex.*;
import hex.genmodel.*;
import hex.genmodel.algos.kmeans.KMeansMojoModel;
import hex.genmodel.descriptor.ModelDescriptor;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.*;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.RowDataUtils;

import java.io.IOException;

public class GenericModel extends Model<GenericModel, GenericModelParameters, GenericModelOutput> 
        implements Model.Contributions {

    private final MojoModelSource _mojoModelSource;

    /**
     * Full constructor
     *
     */
    public GenericModel(Key<GenericModel> selfKey, GenericModelParameters parms, GenericModelOutput output, MojoModel mojoModel, Key<Frame> mojoSource) {
        super(selfKey, parms, output);
        _mojoModelSource = new MojoModelSource(mojoSource, mojoModel);
        _output = new GenericModelOutput(mojoModel._modelDescriptor, mojoModel._modelAttributes, mojoModel._reproducibilityInformation);
        if(mojoModel._modelAttributes != null && mojoModel._modelAttributes.getModelParameters() != null) {
            _parms._modelParameters = GenericModelParameters.convertParameters(mojoModel._modelAttributes.getModelParameters());
        }

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
                return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain, _parms._auc_type);
            case Ordinal:
                return new ModelMetricsOrdinal.MetricBuilderOrdinal(_output.nclasses(), domain);
            case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
            case Clustering:
                if (mojoModel() instanceof KMeansMojoModel) {
                    KMeansMojoModel kMeansMojoModel = (KMeansMojoModel) mojoModel();
                    return new ModelMetricsClustering.MetricBuilderClustering(_output.nfeatures(), kMeansMojoModel.getNumClusters());
                } else {
                    return unsupportedMetricsBuilder();
                }
            case AutoEncoder:
                return new ModelMetricsAutoEncoder.MetricBuilderAutoEncoder(_output.nfeatures());
            case DimReduction:
                return unsupportedMetricsBuilder();
            case WordEmbedding:
                return unsupportedMetricsBuilder();
            case CoxPH:
                return new ModelMetricsRegressionCoxPH.MetricBuilderRegressionCoxPH("start", "stop", false, new String[0]);
            case AnomalyDetection:
                return new ModelMetricsAnomaly.MetricBuilderAnomaly();
            default:
                throw H2O.unimpl();
        }
    }
    
    private ModelMetrics.MetricBuilder unsupportedMetricsBuilder() {
        if (_parms._disable_algo_check) {
            Log.warn("Model category `" + _output._modelCategory + "` currently doesn't support calculating model metrics. " +
                    "Model metrics will not be available.");
            return new MetricBuilderGeneric(mojoModel().getPredsSize(_output._modelCategory));
        } else {
            throw new UnsupportedOperationException(_output._modelCategory + " is not supported.");
        }
    }
    
    @Override
    protected double[] score0(double[] data, double[] preds) {
        return mojoModel().score0(data, preds);
    }

    @Override
    protected double[] score0(double[] data, double[] preds, double offset) {
        if (offset == 0) // MOJO doesn't like when score0 is called with 0 offset for problems that were trained without offset 
            return score0(data, preds);
        else
            return mojoModel().score0(data, offset, preds);
    }

    @Override
    protected AdaptFrameParameters makeAdaptFrameParameters() {
        final MojoModel mojoModel = mojoModel();
        CategoricalEncoding encoding = mojoModel.getCategoricalEncoding();
        if (encoding.isParametrized()) {
            throw new UnsupportedOperationException(
                    "Models with categorical encoding '" + encoding + "' are not currently supported for predicting and/or calculating metrics.");
        }
        final Parameters.CategoricalEncodingScheme encodingScheme = Parameters.CategoricalEncodingScheme.fromGenModel(encoding);
        final ModelDescriptor descriptor = mojoModel._modelDescriptor;
        return new AdaptFrameParameters() {
            @Override
            public Parameters.CategoricalEncodingScheme getCategoricalEncoding() {
                return encodingScheme;
            }
            @Override
            public String getWeightsColumn() {
                return descriptor.weightsColumn();
            }
            @Override
            public String getOffsetColumn() {
                return descriptor.offsetColumn();
            }
            @Override
            public String getFoldColumn() {
                return descriptor.foldColumn();
            }
            @Override
            public String getResponseColumn() {
                return mojoModel.isSupervised() ? mojoModel.getResponseName() : null; 
            }
            @Override
            public String getTreatmentColumn() {return null;}
            @Override
            public double missingColumnsType() {
                return Double.NaN;
            }
            @Override
            public int getMaxCategoricalLevels() {
                return -1; // returned but won't be used
            }
        };
    }

    @Override
    protected String[] makeScoringNames() {
        return mojoModel().getOutputNames();
    }

    @Override
    protected boolean needsPostProcess() {
        return false; // MOJO scoring includes post-processing 
    }

    @Override
    public GenericModelMojoWriter getMojo() {
        return new GenericModelMojoWriter(_mojoModelSource.mojoByteVec());
    }

    private MojoModel mojoModel() {
        return _mojoModelSource.get();
    }

    private static class MetricBuilderGeneric extends ModelMetrics.MetricBuilder<MetricBuilderGeneric> {
        private MetricBuilderGeneric(int predsSize) {
            _work = new double[predsSize];
        }

        @Override
        public double[] perRow(double[] ds, float[] yact, Model m) {
            return ds;
        }

        @Override
        public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
            return null;
        }
    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        if (_parms._path != null) {
            // user loaded the model by providing a path (not a Frame holding MOJO data) => we need to do the clean-up
            Frame mojoFrame = _mojoModelSource._mojoSource.get();
            if (mojoFrame != null) {
                mojoFrame.remove(fs, cascade);
            }
        }
        return super.remove_impl(fs, cascade);
    }

    private static class MojoModelSource extends Iced<MojoModelSource> {
        private final Key<Frame> _mojoSource;

        private transient MojoModel _mojoModel;

        MojoModelSource(Key<Frame> mojoSource, MojoModel mojoModel) {
            _mojoSource = mojoSource;
            _mojoModel = mojoModel;
        }

        private ByteVec mojoByteVec() {
            return (ByteVec) _mojoSource.get().anyVec();
        }

        MojoModel get() {
            if (_mojoModel == null) {
                synchronized (this) {
                    if (_mojoModel == null) {
                        _mojoModel = reconstructMojo(mojoByteVec());
                    }
                }
            }
            assert _mojoModel != null;
            return _mojoModel;
        }
    }

    @Override
    public Frame scoreContributions(Frame frame, Key<Frame> destination_key) {
        return scoreContributions(frame, destination_key, null);
    }

    @Override
    public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> job) {
        EasyPredictModelWrapper wrapper = makeWrapperWithContributions();

        // keep only columns that the model actually needs
        Frame adaptFrm = new Frame(frame);
        GenModel model = wrapper.getModel();
        String[] columnNames = model.getOrigNames() != null ? model.getOrigNames() : model.getNames();
        adaptFrm.remove(ArrayUtils.difference(frame._names, columnNames));

        String[] outputNames = wrapper.getContributionNames();
        return new GenericScoreContributionsTask(wrapper)
                .withPostMapAction(JobUpdatePostMap.forJob(job))
                .doAll(outputNames.length, Vec.T_NUM, adaptFrm)
                .outputFrame(destination_key, outputNames, null);
    }

    private class GenericScoreContributionsTask extends MRTask<GenericScoreContributionsTask> {
        private transient EasyPredictModelWrapper _wrapper;

        GenericScoreContributionsTask(EasyPredictModelWrapper wrapper) {
            _wrapper = wrapper;
        }

        @Override
        protected void setupLocal() {
            if (_wrapper == null) {
                _wrapper = makeWrapperWithContributions();
            }
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            try {
                predict(cs, ncs);
            } catch (PredictException e) {
                throw new RuntimeException(e);
            }
        }

        private void predict(Chunk[] cs, NewChunk[] ncs) throws PredictException {
            RowData rowData = new RowData();
            byte[] types = _fr.types();
            for (int i = 0; i < cs[0]._len; i++) {
                RowDataUtils.extractChunkRow(cs, _fr._names, types, i, rowData);
                float[] contributions = _wrapper.predictContributions(rowData);
                NewChunk.addNums(ncs, contributions);
            }
        }
    }

    EasyPredictModelWrapper makeWrapperWithContributions() {
        final EasyPredictModelWrapper.Config config;
        try {
            config = new EasyPredictModelWrapper.Config()
                    .setModel(mojoModel())
                    .setConvertUnknownCategoricalLevelsToNa(true)
                    .setEnableContributions(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new EasyPredictModelWrapper(config);
    }

}
