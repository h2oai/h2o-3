package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import hex.tree.xgboost.XGBoostUtils;
import water.H2O;
import water.MRTask;
import water.util.FileUtils;
import water.util.IcedHashMapGeneric;

import java.io.*;
import java.util.*;

public class XGBoostUpdateTask extends MRTask<XGBoostUpdateTask> {

    private final XGBoostModelInfo _sharedmodel;
    private final XGBoostOutput _output;
    private transient Booster booster;
    private byte[] rawBooster;

    private final XGBoostModel.XGBoostParameters _parms;
    private final int tid;

    private IcedHashMapGeneric.IcedHashMapStringString rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString();

    private String[] _featureMap;
    private final File featuresDir;
    private final String featrureMapFile;

    public XGBoostUpdateTask(Booster booster,
                      XGBoostModelInfo inputModel,
                      XGBoostOutput _output,
                      XGBoostModel.XGBoostParameters _parms,
                      int tid,
                      Map<String, String> workerEnvs,
                      String[] featureMap,
                      File featuresDir,
                      String featrureMapFile) {
        this._sharedmodel = inputModel;
        this._output = _output;
        this._parms = _parms;
        this.tid = tid;
        this._featureMap = featureMap;
        this.featuresDir = featuresDir;
        this.featrureMapFile = featrureMapFile;
        this.rawBooster = hex.tree.xgboost.XGBoost.getRawArray(booster);
        rabitEnv.putAll(workerEnvs);
    }

    @Override
    protected void setupLocal() {
        try {
            update();
        } catch (XGBoostError xgBoostError) {
            throw new IllegalStateException("Failed XGBoost training.", xgBoostError);
        }
    }

    private void update() throws XGBoostError {
        HashMap<String, Object> params = XGBoostModel.createParams(_parms, _output);

        rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));

        // DON'T put this before createParams, createPrams calls train() which isn't supposed to be distributed
        // just to check if we have GPU on the machine
        Rabit.init(rabitEnv);

        DMatrix trainMat = XGBoostUtils.convertFrameToDMatrix(
                _sharedmodel._dataInfoKey,
                _fr,
                true,
                _parms._response_column,
                _parms._weights_column,
                _parms._fold_column,
                _featureMap,
                _output._sparse);

        if(null == trainMat) {
            return;
        }

        if(rawBooster == null) {
            // For feature importances - write out column info
            OutputStream os = null;
            try {
                os = new FileOutputStream(new File(featuresDir, featrureMapFile));
                os.write(_featureMap[0].getBytes());
                os.close();
            } catch (IOException e) {
                H2O.fail("Cannot generate " + featrureMapFile, e);
            } finally {
                FileUtils.close(os);
            }

            HashMap<String, DMatrix> watches = new HashMap<>();
            booster = ml.dmlc.xgboost4j.java.XGBoost.train(trainMat, params, 0, watches, null, null);
        } else {
            try {
                booster = Booster.loadModel(new ByteArrayInputStream(rawBooster));
//                 Set the parameters, some seem to get lost on save/load
                booster.setParams(params);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load the booster.", e);
            }

            booster.update(trainMat, tid);
        }

        Rabit.shutdown();
    }

    @Override
    public void reduce(XGBoostUpdateTask mrt) {
        if(null == rawBooster) {
            rawBooster = mrt.rawBooster;
        }
    }

    public Booster getBooster() {
        if(null == booster) {
            try {
                booster = Booster.loadModel(new ByteArrayInputStream(rawBooster));
            } catch (XGBoostError | IOException xgBoostError) {
                throw new IllegalStateException("Failed to load the booster.", xgBoostError);
            }
        }
        return booster;
    }
}