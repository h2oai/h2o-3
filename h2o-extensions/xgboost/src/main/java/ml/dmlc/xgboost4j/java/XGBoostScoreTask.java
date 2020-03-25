package ml.dmlc.xgboost4j.java;

import hex.*;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import hex.tree.xgboost.predict.XGBoostBigScorePredict;
import hex.tree.xgboost.predict.XGBoostPredict;
import water.MRTask;
import water.MemoryManager;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;

import static water.fvec.Vec.T_CAT;
import static water.fvec.Vec.T_NUM;

public class XGBoostScoreTask extends MRTask<XGBoostScoreTask> {

    public static ModelMetrics computeMetrics(
        XGBoostOutput output,
        Frame data,
        Frame originalData,
        boolean isTrain,
        XGBoostModel m
    ) {
        Scope.enter();
        try {
            Frame preds = Scope.track(scoreModel(output, data, isTrain, m));
            ModelMetrics.MetricBuilder metricBuilder = createMetricsBuilder(output.nclasses(), output.classNames());
            if (output.nclasses() == 1) {
                Vec pred = preds.vec(0);
                return metricBuilder.makeModelMetrics(m, originalData, data, new Frame(pred));
            } else if (output.nclasses() == 2) {
                Vec p1 = preds.vec(2);
                return metricBuilder.makeModelMetrics(m, originalData, data, new Frame(p1));
            } else {
                Frame pp = new Frame(preds);
                pp.remove(0);
                return metricBuilder.makeModelMetrics(m, originalData, data, pp);
            }
        } finally {
            Scope.exit();
        }
    }

    public static Frame scoreModel(
        XGBoostOutput output,
        Frame data,
        boolean isTrain,
        XGBoostModel m
    ) {
        XGBoostScoreTask task = new XGBoostScoreTask(output, data.find(m._parms._weights_column), isTrain, m)
            .doAll(outputTypes(output), data);
        final String[] names = Model.makeScoringNames(output);
        return task.outputFrame(null, names, makeDomains(output, names));
    }


    private static byte[] outputTypes(XGBoostOutput output) {
        // Last output is the response, which eventually will be removed before returning the preds Frame but is needed to build metrics
        if(output.nclasses() == 1) {
            return new byte[]{T_NUM};
        } else if(output.nclasses() == 2) {
            return new byte[]{T_CAT, T_NUM, T_NUM};
        } else{
            byte[] types = new byte[output.nclasses() + 1];
            Arrays.fill(types, T_NUM);
            return types;
        }
    }

    private static String[][] makeDomains(XGBoostOutput output, String[] names) {
        if(output.nclasses() == 1) {
            return null;
        } else {
            String[][] domains = new String[names.length][];
            domains[0] = output.classNames();
            return domains;
        }
    }

    private final XGBoostOutput _output;
    private final int _weightsChunkId;
    private final XGBoostModel _model;
    private final boolean _isTrain;
    private final double _threshold;

    private transient XGBoostBigScorePredict _predict;
    private transient ModelMetrics.MetricBuilder _metricBuilder;

    private XGBoostScoreTask(
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
    private static ModelMetrics.MetricBuilder createMetricsBuilder(final int responseClassesNum, final String[] responseDomain) {
        switch (responseClassesNum) {
            case 1:
                return new ModelMetricsRegression.MetricBuilderRegression();
            case 2:
                return new ModelMetricsBinomial.MetricBuilderBinomial(responseDomain);
            default:
                return new ModelMetricsMultinomial.MetricBuilderMultinomial(responseClassesNum, responseDomain);
        }
    }

    @Override
    protected void setupLocal() {
        _metricBuilder = createMetricsBuilder(_output.nclasses(), _output.classNames());
        _predict = _model.setupBigScorePredict(_isTrain);
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
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
