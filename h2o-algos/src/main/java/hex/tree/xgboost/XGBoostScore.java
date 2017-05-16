package hex.tree.xgboost;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.Key;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

public class XGBoostScore extends MRTask<XGBoostScore> {

    private final XGBoostModelInfo _sharedmodel;
    private final XGBoostOutput _output;
    private final XGBoostModel.XGBoostParameters _parms;

    private final Booster booster;
    private final Key<Frame> destinationKey;
    ModelMetrics mm;
    Frame predFrame;

    public XGBoostScore(XGBoostModelInfo sharedmodel,
                        XGBoostOutput output,
                        XGBoostModel.XGBoostParameters parms,
                        Booster booster,
                        Key<Frame> destinationKey) {
        _sharedmodel = sharedmodel;
        _output = output;
        _parms = parms;

        this.booster = booster;
        this.destinationKey = destinationKey;
    }

    @Override
    protected void setupLocal() {
        try {
            DMatrix data = XGBoost.convertFrametoDMatrix(
                    _sharedmodel._dataInfoKey,
                    _fr,
                    true,
                    _parms._response_column,
                    _parms._weights_column,
                    _parms._fold_column,
                    null,
                    _output._sparse);

            final float[][] preds = booster.predict(data);
            Vec resp = Vec.makeVec(data.getLabel(), Vec.newKey());
            float[] weights = data.getWeight();
            if (_output.nclasses() == 1) {
                double[] dpreds = new double[preds.length];
                for (int j = 0; j < dpreds.length; ++j)
                    dpreds[j] = preds[j][0];
//      if (weights.length>0)
//        for (int j = 0; j < dpreds.length; ++j)
//          assert weights[j] == 1.0;
                Vec pred = Vec.makeVec(dpreds, Vec.newKey());
                mm = ModelMetricsRegression.make(pred, resp, DistributionFamily.gaussian);
                predFrame = new Frame(destinationKey, new Vec[]{pred}, true);
            } else if (_output.nclasses() == 2) {
                double[] dpreds = new double[preds.length];
                for (int j = 0; j < dpreds.length; ++j)
                    dpreds[j] = preds[j][0];
                if (weights.length > 0)
                    for (int j = 0; j < dpreds.length; ++j)
                        assert weights[j] == 1.0;
                Vec p1 = Vec.makeVec(dpreds, Vec.newKey());
                Vec p0 = p1.makeCon(0);
                Vec label = p1.makeCon(0., Vec.T_CAT);
                new MRTask() {
                    public void map(Chunk l, Chunk p0, Chunk p1) {
                        for (int i = 0; i < l._len; ++i) {
                            double p = p1.atd(i);
                            p0.set(i, 1. - p);
                            double[] row = new double[]{0, 1 - p, p};
                            l.set(i, hex.genmodel.GenModel.getPrediction(row, _output._priorClassDist, null, Model.defaultThreshold(_output)));
                        }
                    }
                }.doAll(label, p0, p1);
                mm = ModelMetricsBinomial.make(p1, resp);
                label.setDomain(new String[]{"N", "Y"}); // ignored
                predFrame = new Frame(destinationKey, new Vec[]{label, p0, p1}, true);
            } else {
                String[] names = Model.makeScoringNames(_output);
                String[][] domains = new String[names.length][];
                domains[0] = _output.classNames();
                Frame input = new Frame(resp); //has the right size
                predFrame = new MRTask() {
                    public void map(Chunk[] chk, NewChunk[] nc) {
                        for (int i = 0; i < chk[0]._len; ++i) {
                            double[] row = new double[nc.length];
                            for (int j = 1; j < row.length; ++j) {
                                double val = preds[i][j - 1];
                                nc[j].addNum(val);
                                row[j] = val;
                            }
                            nc[0].addNum(hex.genmodel.GenModel.getPrediction(row, _output._priorClassDist, null, Model.defaultThreshold(_output)));
                        }
                    }
                }.doAll(_output.nclasses() + 1, Vec.T_NUM, input).outputFrame(destinationKey, names, domains);

                Frame pp = new Frame(predFrame);
                pp.remove(0);
                Scope.enter();
                mm = ModelMetricsMultinomial.make(pp, resp, resp.toCategoricalVec().domain());
                Scope.exit();
            }
            resp.remove();
        } catch (XGBoostError xgBoostError) {
            xgBoostError.printStackTrace();
        }
    }

    @Override
    public void reduce(XGBoostScore other) {
        // TODO ????
    }

}
