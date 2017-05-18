package hex.tree.xgboost;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.Rabit;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.*;
import water.fvec.*;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class XGBoostScore extends MRTask<XGBoostScore> {

    private final XGBoostModelInfo _sharedmodel;
    private final XGBoostOutput _output;
    private final XGBoostModel.XGBoostParameters _parms;

    private byte[] rawBooster;
    private final Key<Frame> destinationKey;
    private Frame subPredsFrame;
    private Frame subResponsesFrame;
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
        this.rawBooster = XGBoost.getRawArray(booster);
        this.destinationKey = destinationKey;
    }

    @Override
    protected void setupLocal() {
        try {
            HashMap<String, Object> params = XGBoostModel.createParams(_parms, _output);
            Map<String, String> rabitEnv = new HashMap<>();
            rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));
            Rabit.init(rabitEnv);

            DMatrix data = XGBoost.convertFrametoDMatrix(
                    _sharedmodel._dataInfoKey,
                    _fr,
                    true,
                    _parms._response_column,
                    _parms._weights_column,
                    _parms._fold_column,
                    null,
                    _output._sparse);

            Booster booster = null;
            try {
                booster = Booster.loadModel(new ByteArrayInputStream(rawBooster));
                booster.setParams(params);
            } catch (IOException e) {
                e.printStackTrace();
            }
            final float[][] preds = booster.predict(data);
            Vec resp = Vec.makeVec(data.getLabel(), Vec.newKey());
            subResponsesFrame = new Frame(Key.<Frame>make(), new Vec[] {resp}, true);
            DKV.put(subResponsesFrame);

            float[] weights = data.getWeight();

            if (_output.nclasses() == 1) {
                double[] dpreds = new double[preds.length];
                for (int j = 0; j < dpreds.length; ++j)
                    dpreds[j] = preds[j][0];
//      if (weights.length>0)
//        for (int j = 0; j < dpreds.length; ++j)
//          assert weights[j] == 1.0;
                subPredsFrame = new Frame(Key.<Frame>make(), new Vec[] {Vec.makeVec(dpreds, Vec.newKey())}, true);

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

                label.setDomain(new String[]{"N", "Y"}); // ignored

                subPredsFrame = new Frame(Key.<Frame>make(), new Vec[] { label, p0, p1 }, true);
            } else {
                String[] names = Model.makeScoringNames(_output);
                String[][] domains = new String[names.length][];
                domains[0] = _output.classNames();
                Frame input = new Frame(resp); //has the right size
                subPredsFrame = new MRTask() {
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
            }
            DKV.put(subPredsFrame);

        } catch (XGBoostError xgBoostError) {
            xgBoostError.printStackTrace();
        } finally {
            try {
                Rabit.shutdown();
            } catch (XGBoostError xgBoostError) {
                xgBoostError.printStackTrace();
            }
        }
    }

    @Override
    public void reduce(XGBoostScore other) {
        // todo for some reason the domains in the other vector are going AWOL?!
        for(int i = 0; i < subPredsFrame.numCols(); i++) {
            Vec vec = subPredsFrame.vec(i);
            if(vec.domain() != null) {
                other.subPredsFrame.vec(i).setDomain(vec.domain());
            }
        }
        subPredsFrame = rBindAndDelete(subPredsFrame, other.subPredsFrame);
        subResponsesFrame = rBindAndDelete(subResponsesFrame, other.subResponsesFrame);
    }

    private Frame rBindAndDelete(Frame left, Frame right) {
        if(left._key.equals(right._key)) {
            return left;
        }

        String respBind = "(rbind " + left._key + " " + right._key + ")";
        try {
            Val result = Rapids.exec(respBind);
            if (result instanceof ValFrame) {
                return result.getFrame();
            } // TODO handle else case
            return null;
        } finally {
            left.remove();
            right.remove();
        }
    }


    @Override
    protected void postGlobal() {
        Vec resp = subResponsesFrame.vec(0);
        if (_output.nclasses() == 1) {
            Vec pred = subPredsFrame.vec(0);
            mm = ModelMetricsRegression.make(pred, resp, DistributionFamily.gaussian);
            predFrame = new Frame(destinationKey, new Vec[]{pred}, true);
        } else if (_output.nclasses() == 2) {
            Vec label = subPredsFrame.vec(0);
            Vec p0 = subPredsFrame.vec(1);
            Vec p1 = subPredsFrame.vec(2);
            mm = ModelMetricsBinomial.make(p1, resp);
            predFrame = new Frame(destinationKey, new Vec[]{label, p0, p1}, true);
        } else {
            predFrame = subPredsFrame;
            Frame pp = new Frame(predFrame);
            pp.remove(0);
            Scope.enter();
            mm = ModelMetricsMultinomial.make(pp, resp, resp.toCategoricalVec().domain());
            Scope.exit();
        }
        if( _output.nclasses() <= 2 ) {
            // Remove underlying vec references since they are used in predFrame
            subPredsFrame.removeAll();
            // Remove the frame
            subPredsFrame.remove();
        }
        subResponsesFrame.remove();
    }
}
