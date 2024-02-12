package hex.generic;

import hex.*;
import hex.genmodel.*;
import hex.genmodel.algos.glm.GlmMojoModelBase;
import hex.genmodel.algos.kmeans.KMeansMojoModel;
import hex.genmodel.descriptor.ModelDescriptor;
import hex.genmodel.descriptor.ModelDescriptorBuilder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.glm.GLMModel;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.*;
import water.fvec.*;
import water.udf.CFuncRef;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.RowDataUtils;

import java.io.IOException;
import java.util.*;

public class GenericModel extends Model<GenericModel, GenericModelParameters, GenericModelOutput> 
        implements Model.Contributions {

    /**
     * Captures model-specific behaviors
     */
    private static final Map<String, ModelBehavior[]> DEFAULT_MODEL_BEHAVIORS;
    static{
        final Map<String, ModelBehavior[]> behaviors = new HashMap<>();
        behaviors.put(
                "gam", new ModelBehavior[]{
                        ModelBehavior.USE_MOJO_PREDICT // GAM score0 cannot be used directly because it introduces special column in RawData conversion phase
                }
        );

        DEFAULT_MODEL_BEHAVIORS = Collections.unmodifiableMap(behaviors);
    }

    /**
     * name of the algo for MOJO, "pojo" for POJO models
     */
    private final String _algoName; 
    private final GenModelSource<?> _genModelSource;
    private GLMModel.GLMParameters _glmParameters;

    /**
     * Full constructor
     *
     */
    public GenericModel(Key<GenericModel> selfKey, GenericModelParameters parms, GenericModelOutput output,
                        MojoModel mojoModel, Key<Frame> mojoSource) {
        super(selfKey, parms, output);
        _algoName = mojoModel._algoName;
        _genModelSource = new MojoModelSource(mojoSource, mojoModel, defaultModelBehaviors(_algoName));
        _output = new GenericModelOutput(mojoModel._modelDescriptor, mojoModel._modelAttributes, mojoModel._reproducibilityInformation);
        if (mojoModel._modelAttributes != null && mojoModel._modelAttributes.getModelParameters() != null) {
            _parms._modelParameters = GenericModelParameters.convertParameters(mojoModel._modelAttributes.getModelParameters());
        }
        _glmParameters = null;
        if(_algoName.toLowerCase().contains("glm")) {
            GlmMojoModelBase glmModel = (GlmMojoModelBase) mojoModel;
            // create GLM parameters instance
            _glmParameters = new GLMModel.GLMParameters(
                    GLMModel.GLMParameters.Family.valueOf(getParamByName("family").toString()),
                    GLMModel.GLMParameters.Link.valueOf(getParamByName("link").toString()),
                    Arrays.stream(getParamByName("lambda").toString().trim().replaceAll("\\[", "")
                                    .replaceAll("\\]", "").split(",\\s*"))
                            .mapToDouble(Double::parseDouble).toArray(),
                    Arrays.stream(getParamByName("alpha").toString().trim().replaceAll("\\[", "")
                                    .replaceAll("\\]", "").split(",\\s*"))
                            .mapToDouble(Double::parseDouble).toArray(),
                    Double.parseDouble(getParamByName("tweedie_variance_power").toString()),
                    Double.parseDouble(getParamByName("tweedie_link_power").toString()),
                    null,
                    Double.parseDouble(getParamByName("theta").toString()), 
                    glmModel.getDispersionEstimated()
            );
        }
    }

    public GenericModel(Key<GenericModel> selfKey, GenericModelParameters parms, GenericModelOutput output,
                        GenModel pojoModel, Key<Frame> pojoSource) {
        super(selfKey, parms, output);
        _algoName = "pojo";
        _genModelSource = new PojoModelSource(selfKey.toString(), pojoSource, pojoModel);
        _output = new GenericModelOutput(ModelDescriptorBuilder.makeDescriptor(pojoModel));
    }

    static ModelBehavior[] defaultModelBehaviors(String algoName) {
        return DEFAULT_MODEL_BEHAVIORS.get(algoName);
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
                if (genModel() instanceof KMeansMojoModel) {
                    KMeansMojoModel kMeansMojoModel = (KMeansMojoModel) genModel();
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
            case BinomialUplift:
                return new ModelMetricsBinomialUplift.MetricBuilderBinomialUplift(domain, null);
            default:
                throw H2O.unimpl();
        }
    }

    @Override
    protected Frame adaptFrameForScore(Frame fr, boolean computeMetrics) {
        if (hasBehavior(ModelBehavior.USE_MOJO_PREDICT)) {
            // We do not need to adapt the frame in any way, MOJO will handle it itself
            return fr;
        } else
            return super.adaptFrameForScore(fr, computeMetrics);
    }

    @Override
    protected PredictScoreResult predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j, 
                                                  boolean computeMetrics, CFuncRef customMetricFunc) {
        if (hasBehavior(ModelBehavior.USE_MOJO_PREDICT)) {
            return predictScoreMojoImpl(fr, destination_key, j, computeMetrics);
        } else
            return super.predictScoreImpl(fr, adaptFrm, destination_key, j, computeMetrics, customMetricFunc);
    }

    private Iced getParamByName(String name) {
        return Arrays.stream(this._parms._modelParameters)
                .filter(p -> Objects.equals(p.name, name)).findAny().get().actual_value;
    }

    @Override
    public double aic(double likelihood) {
        // calculate negative loglikelihood specifically  for GLM
        if (!_algoName.equals("glm")) {
            return Double.NaN;
        } else {
            long betasCount = Arrays.stream(((GlmMojoModelBase) this.genModel()).getBeta()).filter(b -> b != 0).count();
            return -2 * likelihood + 2 * betasCount;
        }
    }

    @Override
    public double likelihood(double w, double y, double[] f) {
        // calculate negative loglikelihood specifically for GLM
        if(!_algoName.equals("glm")) {
            return Double.NaN;
        } else if (w == 0) {
            return 0;
        } else {
            // time-consuming calculation for the final scoring for GLM model
            return _glmParameters.likelihood(w, y, f);
        }
    }

    PredictScoreResult predictScoreMojoImpl(Frame fr, String destination_key, Job<?> j, boolean computeMetrics) {
        GenModel model = genModel();
        String[] names = model.getOutputNames();
        String[][] domains = model.getOutputDomains();
        byte[] type = new byte[domains.length];
        for (int i = 0; i < type.length; i++) {
            type[i] = domains[i] != null ? Vec.T_CAT : Vec.T_NUM; 
        }

        PredictScoreMojoTask bs = new PredictScoreMojoTask(computeMetrics, j);
        Frame predictFr = bs.doAll(type, fr).outputFrame(Key.make(destination_key), names, domains);
        return new PredictScoreResult(bs._mb, predictFr, predictFr);
    }

    private class PredictScoreMojoTask extends MRTask<PredictScoreMojoTask> {
        private final boolean _computeMetrics;
        private final Job<?> _j;

        /** Output parameter: Metric builder */
        private ModelMetrics.MetricBuilder<?> _mb;

        public PredictScoreMojoTask(boolean computeMetrics, Job<?> j) {
            _computeMetrics = computeMetrics;
            _j = j;
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            if (isCancelled() || (_j != null && _j.stop_requested()))
                return;

            EasyPredictModelWrapper wrapper = makeWrapper();
            GenModel model = wrapper.getModel();
            String[] responseDomain = model.isSupervised() ? model.getDomainValues(model.getResponseName()) : null;
            AdaptFrameParameters adaptFrameParameters = makeAdaptFrameParameters(
                    Parameters.CategoricalEncodingScheme.AUTO); // encoding will actually be handled by the MOJO itself
            _mb = _computeMetrics ? GenericModel.this.makeMetricBuilder(responseDomain) : null;
            try {
                predict(wrapper, adaptFrameParameters, responseDomain, cs, ncs);
            } catch (PredictException e) {
                throw new RuntimeException(e);
            }
        }

        private void predict(EasyPredictModelWrapper wrapper, AdaptFrameParameters adaptFrameParameters, String[] responseDomain,
                             Chunk[] cs, NewChunk[] ncs) throws PredictException {
            final byte[] types = _fr.types();
            final String offsetColumn = adaptFrameParameters.getOffsetColumn();
            final String weightsColumn = adaptFrameParameters.getWeightsColumn();
            final String responseColumn = adaptFrameParameters.getResponseColumn();
            final String treatmentColumn = adaptFrameParameters.getTreatmentColumn();
            final boolean isClassifier = wrapper.getModel().isClassifier();
            final boolean isUplift = treatmentColumn != null;
            final float[] yact;
            if (isUplift) {
                yact = new float[2];
            } else {
                yact = new float[1];
            }
            for (int row = 0; row < cs[0]._len; row++) {
                RowData rowData = new RowData();
                RowDataUtils.extractChunkRow(cs, _fr._names, types, row, rowData);
                double offset = offsetColumn != null && rowData.containsKey(offsetColumn) ? 
                        (double) rowData.get(offsetColumn) : 0.0;
                double[] result = wrapper.predictRaw(rowData, offset);
                for (int i = 0; i < ncs.length; i++) {
                    ncs[i].addNum(result[i]);
                }
                if (_mb != null) {
                    Object response = responseColumn != null && rowData.containsKey(responseColumn) ?
                            rowData.get(responseColumn) : null;
                    if (response == null)
                        continue;
                    double weight = weightsColumn != null && rowData.containsKey(weightsColumn) ? 
                            (double) rowData.get(weightsColumn) : 1.0;
                    if (isClassifier) {
                        int idx = ArrayUtils.find(responseDomain, String.valueOf(response));
                        if (idx < 0)
                            continue;
                        yact[0] = (float) idx;
                    } else 
                        yact[0] = ((Number) response).floatValue();
                    if (isUplift){
                        yact[1] = (float) rowData.get(treatmentColumn);
                    }
                    _mb.perRow(result, yact, weight, offset, GenericModel.this);
                }
            }
        }

        @Override
        public void reduce(PredictScoreMojoTask bs) {
            super.reduce(bs);
            if (_mb != null) {
                _mb.reduce(bs._mb);
            }
        }

        EasyPredictModelWrapper makeWrapper() {
            final EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
                    .setModel(genModel().internal_threadSafeInstance())
                    .setConvertUnknownCategoricalLevelsToNa(true);
            return new EasyPredictModelWrapper(config);
        }
    }

    private ModelMetrics.MetricBuilder<?> unsupportedMetricsBuilder() {
        if (_parms._disable_algo_check) {
            Log.warn("Model category `" + _output._modelCategory + "` currently doesn't support calculating model metrics. " +
                    "Model metrics will not be available.");
            return new MetricBuilderGeneric(genModel().getPredsSize(_output._modelCategory));
        } else {
            throw new UnsupportedOperationException(_output._modelCategory + " is not supported.");
        }
    }
    
    @Override
    protected double[] score0(double[] data, double[] preds) {
        return genModel().score0(data, preds);
    }

    @Override
    protected double[] score0(double[] data, double[] preds, double offset) {
        if (offset == 0) // MOJO doesn't like when score0 is called with 0 offset for problems that were trained without offset 
            return score0(data, preds);
        else
            return genModel().score0(data, offset, preds);
    }

    @Override
    protected AdaptFrameParameters makeAdaptFrameParameters() {
        CategoricalEncoding encoding = genModel().getCategoricalEncoding();
        if (encoding.isParametrized()) {
            throw new UnsupportedOperationException(
                    "Models with categorical encoding '" + encoding + "' are not currently supported for predicting and/or calculating metrics.");
        }
        return makeAdaptFrameParameters(Parameters.CategoricalEncodingScheme.fromGenModel(encoding));
    }

    protected AdaptFrameParameters makeAdaptFrameParameters(final Parameters.CategoricalEncodingScheme encodingScheme) {
        final GenModel genModel = genModel();
        final ModelDescriptor descriptor = getModelDescriptor();
        return new AdaptFrameParameters() {
            @Override
            public Parameters.CategoricalEncodingScheme getCategoricalEncoding() {
                return encodingScheme;
            }
            @Override
            public String getWeightsColumn() {
                return descriptor != null ? descriptor.weightsColumn() : null;
            }
            @Override
            public String getOffsetColumn() {
                return descriptor != null ? descriptor.offsetColumn() : null;
            }
            @Override
            public String getFoldColumn() {
                return descriptor != null ? descriptor.foldColumn() : null;
            }
            @Override
            public String getResponseColumn() {
                return genModel.isSupervised() ? genModel.getResponseName() : null; 
            }
            @Override
            public String getTreatmentColumn() {return descriptor != null ? descriptor.treatmentColumn() : null;}
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

    private ModelDescriptor getModelDescriptor() {
        final GenModel genModel = genModel();
        return genModel instanceof MojoModel ? ((MojoModel) genModel)._modelDescriptor : null;
    }

    @Override
    protected String[] makeScoringNames() {
        return genModel().getOutputNames();
    }

    @Override
    protected boolean needsPostProcess() {
        return false; // MOJO scoring includes post-processing 
    }

    @Override
    public GenericModelMojoWriter getMojo() {
        if (_genModelSource instanceof MojoModelSource) {
            return new GenericModelMojoWriter(_genModelSource.backingByteVec());
        }
        throw new IllegalStateException("Cannot create a MOJO from a POJO");
    }

    private GenModel genModel() {
        GenericModel self = DKV.getGet(_key); // trick - always use instance cached in DKV to avoid model-reloading
        return self._genModelSource.get();
    }

    @Override
    protected BigScorePredict setupBigScorePredict(Model<GenericModel, GenericModelParameters, GenericModelOutput>.BigScore bs) {
        GenModel genmodel = genModel();
        assert genmodel != null;
        return super.setupBigScorePredict(bs);
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
            _genModelSource.remove(fs, cascade);
        }
        return super.remove_impl(fs, cascade);
    }

    private static abstract class GenModelSource<T extends Iced<T>> extends Iced<T> {
        private final Key<Frame> _source;
        private transient volatile GenModel _genModel;

        GenModelSource(Key<Frame> source, GenModel genModel) {
            _source = source;
            _genModel = genModel;
        }

        GenModel get() {
            if (_genModel == null) {
                synchronized (this) {
                    if (_genModel == null) {
                        _genModel = reconstructGenModel(backingByteVec());
                    }
                }
            }
            assert _genModel != null;
            return _genModel;
        }

        void remove(Futures fs, boolean cascade) {
            Frame mojoFrame = _source.get();
            if (mojoFrame != null) {
                mojoFrame.remove(fs, cascade);
            }
        }

        abstract GenModel reconstructGenModel(ByteVec bv);

        ByteVec backingByteVec() {
            return (ByteVec) _source.get().anyVec();
        }

        Key<Frame> getSourceKey() {
            return _source;
        }

        ModelBehavior[] getModelBehaviors() {
            return null;
        }
    }
    
    private static class MojoModelSource extends GenModelSource<MojoModelSource> {
        private final ModelBehavior[] _modelBehaviors;

        MojoModelSource(Key<Frame> mojoSource, MojoModel mojoModel, ModelBehavior[] defaultModelBehaviors) {
            super(mojoSource, mojoModel);
            _modelBehaviors = mojoModeBehaviors(mojoModel, defaultModelBehaviors);
        }

        @Override
        GenModel reconstructGenModel(ByteVec bv) {
            return reconstructMojo(bv);
        }

        @Override
        ModelBehavior[] getModelBehaviors() {
            return _modelBehaviors;
        }

        static ModelBehavior[] mojoModeBehaviors(MojoModel mojoModel, ModelBehavior[] defaultModelBehaviors) {
            boolean useMojoPredict = mojoModel.getCategoricalEncoding().isParametrized();
            return useMojoPredict ?
                    ArrayUtils.append(defaultModelBehaviors, ModelBehavior.USE_MOJO_PREDICT)
                    :
                    defaultModelBehaviors;
        }
    }

    private static class PojoModelSource extends GenModelSource<PojoModelSource> {
        final String _model_id; 
        PojoModelSource(String modelId, Key<Frame> pojoSource, GenModel pojoModel) {
            super(pojoSource, pojoModel);
            _model_id = modelId;
        }

        @Override
        GenModel reconstructGenModel(ByteVec bv) {
            Key<Frame> pojoKey = getSourceKey();
            try {
                return PojoLoader.loadPojoFromSourceCode(bv, pojoKey, _model_id);
            } catch (IOException e) {
                throw new RuntimeException("Unable to load POJO source code from Vec " + pojoKey);
            }
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
                    .setModel(genModel())
                    .setConvertUnknownCategoricalLevelsToNa(true)
                    .setEnableContributions(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new EasyPredictModelWrapper(config);
    }

    @Override
    protected String toJavaModelClassName() {
        return ModelBuilder.make(_output._original_model_identifier, null, null).getClass()
                .getSimpleName() + "Model";
    }

    @Override
    protected String toJavaAlgo() {
        return _output._original_model_identifier;
    }

    @Override
    protected String toJavaUUID() {
        return genModel().getUUID();
    }

    @Override
    protected PojoWriter makePojoWriter() {
        GenModel genModel = genModel();
        if (!havePojo()) {
            throw new UnsupportedOperationException("Only MOJO models can be converted to POJO.");
        }
        MojoModel mojoModel = (MojoModel) genModel;
        ModelBuilder<?, ?, ?> builder = ModelBuilder.make(mojoModel._algoName, null, null);
        return builder.makePojoWriter(this, mojoModel);
    }
    
    @Override
    public boolean havePojo() {
        GenModel genModel = genModel();
        return genModel instanceof MojoModel;
    }

    boolean hasBehavior(ModelBehavior b) {
        ModelBehavior[] modelBehaviors = _genModelSource.getModelBehaviors();
        if (modelBehaviors == null)
            return false;
        return ArrayUtils.find(modelBehaviors, b) >= 0;
    }

    enum ModelBehavior {
        USE_MOJO_PREDICT
    }

}
