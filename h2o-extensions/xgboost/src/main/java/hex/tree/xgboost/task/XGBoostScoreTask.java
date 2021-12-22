package hex.tree.xgboost.task;

import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import hex.ModelMetricsMultinomial;
import hex.ModelMetricsRegression;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import hex.tree.xgboost.predict.XGBoostBigScorePredict;
import hex.tree.xgboost.predict.XGBoostPredict;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.NewChunk;

public class XGBoostScoreTask extends MRTask<XGBoostScoreTask> {

    private final XGBoostOutput _output;
    private final int _weightsChunkId;
    private final XGBoostModel _model;
    private final boolean _isTrain;
    private final double _threshold;

    public ModelMetrics.MetricBuilder _metricBuilder;

    private transient XGBoostBigScorePredict _predict;

    public XGBoostScoreTask(
        final XGBoostOutput output,
        final int weightsChunkId,
        final boolean isTrain,
        final XGBoostModel model
    ) {
        _output = output;
        _weightsChunkId = weightsChunkId;
        _model = model;
        _isTrain = isTrain;
        _threshold = model.defaultThreshold();
    }

    /**
     * Constructs a MetricBuilder for this XGBoostScoreTask based on parameters of response variable
     *
     * @param responseClassesNum Number of classes found in response variable
     * @param responseDomain     Specific domains in response variable
     * @return An instance of {@link hex.ModelMetrics.MetricBuilder} corresponding to given response variable type
     */
    private ModelMetrics.MetricBuilder createMetricsBuilder(final int responseClassesNum, final String[] responseDomain) {
        switch (responseClassesNum) {
            case 1:
                return new ModelMetricsRegression.MetricBuilderRegression();
            case 2:
                return new ModelMetricsBinomial.MetricBuilderBinomial(responseDomain);
            default:
                return new ModelMetricsMultinomial.MetricBuilderMultinomial(responseClassesNum, responseDomain, this._model._parms._auc_type);
        }
    }

    @Override
    protected void setupLocal() {
        _predict = _model.setupBigScorePredict(_isTrain);
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
        _metricBuilder = createMetricsBuilder(_output.nclasses(), _output.classNames());
        final XGBoostPredict predictor = _predict.initMap(_fr, cs);
        final float[][] preds = predictor.predict(cs);
        if (preds.length == 0) return;
        assert preds.length == cs[0]._len;
        
        final Chunk responseChunk = cs[_output.responseIdx()];

        if (_output.nclasses() == 1) {
            double[] currentPred = new double[1];
            float[] yact = new float[1];
            for (int j = 0; j < preds.length; ++j) {
                currentPred[0] = preds[j][0];
                yact[0] = (float) responseChunk.atd(j);
                double weight = _weightsChunkId != -1 ? cs[_weightsChunkId].atd(j) : 1; // If there is no chunk with weights, the weight is considered to be 1
                _metricBuilder.perRow(currentPred, yact, weight, 0, _model);
            }
            for (int i = 0; i < cs[0]._len; ++i) {
                ncs[0].addNum(preds[i][0]);
            }
        } else if (_output.nclasses() == 2) {
            double[] row = new double[3];
            float[] yact = new float[1];
            for (int i = 0; i < cs[0]._len; ++i) {
                final double p = preds[i][0];
                row[1] = 1 - p;
                row[2] = p;
                row[0] = hex.genmodel.GenModel.getPrediction(row, _output._priorClassDist, null, _threshold);

                ncs[0].addNum(row[0]);
                ncs[1].addNum(row[1]);
                ncs[2].addNum(row[2]);

                double weight = _weightsChunkId != -1 ? cs[_weightsChunkId].atd(i) : 1; // If there is no chunk with weights, the weight is considered to be 1
                yact[0] = (float) responseChunk.atd(i);
                _metricBuilder.perRow(row, yact, weight, 0, _model);
            }
        } else {
            float[] yact = new float[1];
            double[] row = MemoryManager.malloc8d(ncs.length);
            for (int i = 0; i < cs[0]._len; ++i) {
                for (int j = 1; j < row.length; ++j) {
                    double val = preds[i][j - 1];
                    ncs[j].addNum(val);
                    row[j] = val;
                }
                row[0] = hex.genmodel.GenModel.getPrediction(row, _output._priorClassDist, null, _threshold);
                ncs[0].addNum(row[0]);
                yact[0] = (float) responseChunk.atd(i);
                double weight = _weightsChunkId != -1 ? cs[_weightsChunkId].atd(i) : 1; // If there is no chunk with weights, the weight is considered to be 1
                _metricBuilder.perRow(row, yact, weight, 0, _model);
            }
        }
    }

    @Override
    public void reduce(XGBoostScoreTask mrt) {
        super.reduce(mrt);
        _metricBuilder.reduce(mrt._metricBuilder);
    }

}
