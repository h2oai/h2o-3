package ml.dmlc.xgboost4j.java;

import com.google.common.collect.ObjectArrays;
import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.xgboost.*;
import hex.tree.xgboost.XGBoost;
import water.*;
import water.fvec.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static water.fvec.Vec.T_CAT;
import static water.fvec.Vec.T_NUM;

public class XGBoostScoreTask extends MRTask<XGBoostScoreTask> {

    private final XGBoostModelInfo _sharedmodel;
    private final XGBoostOutput _output;
    private final XGBoostModel.XGBoostParameters _parms;
    private final BoosterParms _boosterParms;


    private byte[] rawBooster;

    public static class XGBoostScoreTaskResult {
        public Frame preds;
        public ModelMetrics mm;
    }

    public static XGBoostScoreTaskResult runScoreTask(XGBoostModelInfo sharedmodel,
                                               XGBoostOutput output,
                                               XGBoostModel.XGBoostParameters parms,
                                               Booster booster,
                                               Key<Frame> destinationKey,
                                               Frame data,
                                               boolean computeMetrics){
        BoosterParms boosterParms = XGBoostModel.createParams(parms, output.nclasses());
        XGBoostScoreTask task = new XGBoostScoreTask(sharedmodel,
                output,
                parms,
                booster,
                boosterParms).doAll(outputTypes(output), data);
        String[] names = ObjectArrays.concat(Model.makeScoringNames(output), new String[] {"label"}, String.class);
        Frame preds = task.outputFrame(destinationKey, names, makeDomains(output, names));

        XGBoostScoreTaskResult res = new XGBoostScoreTaskResult();

        Vec resp = preds.lastVec();
        preds.remove(preds.vecs().length - 1);
        if (output.nclasses() == 1) {
            Vec pred = preds.vec(0);
            if (computeMetrics) {
                res.mm = ModelMetricsRegression.make(pred, resp, DistributionFamily.gaussian);
            }
        } else if (output.nclasses() == 2) {
            Vec p1 = preds.vec(2);
            if (computeMetrics) {
                resp.setDomain(output.classNames());
                res.mm = ModelMetricsBinomial.make(p1, resp);
            }
        } else {
            if (computeMetrics) {
                resp.setDomain(output.classNames());
                Frame pp = new Frame(preds);
                pp.remove(0);
                Scope.enter();
                res.mm = ModelMetricsMultinomial.make(pp, resp, resp.toCategoricalVec().domain());
                Scope.exit();
            }
        }

        res.preds = preds;

        if (resp != null) {
            resp.remove();
        }

        if (computeMetrics) {
            assert res.mm != null;
        }
        assert "predict".equals(preds.name(0));

        return res;
    }

    private static byte[] outputTypes(XGBoostOutput output) {
        // Last output is the response, which eventually will be removed before returning the preds Frame but is needed to build metrics
        if(output.nclasses() == 1) {
            return new byte[]{T_NUM, T_NUM};
        } else if(output.nclasses() == 2) {
            return new byte[]{T_CAT, T_NUM, T_NUM, T_NUM};
        } else{
            byte[] types = new byte[output.nclasses() + 2];
            Arrays.fill(types, T_NUM);
            return types;
        }
    }

    private static String[][] makeDomains(XGBoostOutput output, String[] names) {
        if(output.nclasses() == 1) {
            return null;
        } else if(output.nclasses() == 2) {
            String[][] domains = new String[4][];
            domains[0] = new String[]{"N", "Y"};
            domains[3] = new String[]{"N", "Y"};
            return domains;
        } else{
            String[][] domains = new String[names.length][];
            domains[0] = output.classNames();
            return domains;
        }
    }

    private XGBoostScoreTask(XGBoostModelInfo sharedmodel,
                             XGBoostOutput output,
                             XGBoostModel.XGBoostParameters parms,
                             Booster booster, BoosterParms boosterParms) {
        _sharedmodel = sharedmodel;
        _output = output;
        _parms = parms;
        _boosterParms = boosterParms;
        this.rawBooster = XGBoost.getRawArray(booster);
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
        DMatrix data = null;
        Booster booster = null;
        try {
            Map<String, String> rabitEnv = new HashMap<>();
            // Rabit has to be initialized as parts of booster.predict() are using Rabit
            // This might be fixed in future versions of XGBoost
            Rabit.init(rabitEnv);

            data = XGBoostUtils.convertChunksToDMatrix(
                    _sharedmodel._dataInfoKey,
                    cs,
                    _fr.find(_parms._response_column),
                    -1, // not used for preds
                    _fr.find(_parms._fold_column),
                    _output._sparse);

            // No local chunks for this frame
            if (data.rowNum() == 0) {
                return;
            }

            try {
                booster = Booster.loadModel(new ByteArrayInputStream(rawBooster));
                booster.setParams(_boosterParms.get());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load the booster.", e);
            }
            final float[][] preds = booster.predict(data);

            float[] labels = data.getLabel();

            float[] weights = data.getWeight();

            if (_output.nclasses() == 1) {
                double[] dpreds = new double[preds.length];
                for (int j = 0; j < dpreds.length; ++j)
                    dpreds[j] = preds[j][0];
                for (int i = 0; i < cs[0]._len; ++i) {
                    ncs[0].addNum(dpreds[i]);
                    ncs[1].addNum(labels[i]);
                }
            } else if (_output.nclasses() == 2) {
                double[] dpreds = new double[preds.length];

                for (int j = 0; j < dpreds.length; ++j)
                    dpreds[j] = preds[j][0];

                if (weights.length > 0)
                    for (int j = 0; j < dpreds.length; ++j)
                        assert weights[j] == 1.0;

                for (int i = 0; i < cs[0]._len; ++i) {
                    double p = dpreds[i];
                    ncs[1].addNum(1.0d - p);
                    ncs[2].addNum(p);
                    double[] row = new double[]{0, 1 - p, p};
                    double predLab = hex.genmodel.GenModel.getPrediction(row, _output._priorClassDist, null, Model.defaultThreshold(_output));
                    ncs[0].addNum(predLab);

                    ncs[3].addNum(labels[i]);
                }
            } else {
                for (int i = 0; i < cs[0]._len; ++i) {
                    double[] row = new double[ncs.length - 1];
                    for (int j = 1; j < row.length; ++j) {
                        double val = preds[i][j - 1];
                        ncs[j].addNum(val);
                        row[j] = val;
                    }
                    ncs[0].addNum(hex.genmodel.GenModel.getPrediction(row, _output._priorClassDist, null, Model.defaultThreshold(_output)));
                    ncs[ncs.length - 1].addNum(labels[i]);
                }
            }
        } catch (XGBoostError xgBoostError) {
            throw new IllegalStateException("Failed to score with XGBoost.", xgBoostError);
        } finally {
            BoosterHelper.dispose(booster, data);
            try {
                Rabit.shutdown();
            } catch (XGBoostError xgBoostError) {
                throw new IllegalStateException("Failed Rabit shutdown. A hanging RabitTracker task might be present on the driver node.", xgBoostError);
            }
        }
    }

}
