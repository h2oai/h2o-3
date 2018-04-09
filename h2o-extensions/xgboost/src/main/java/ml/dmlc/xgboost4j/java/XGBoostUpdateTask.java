package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.XGBoostExtension;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import hex.tree.xgboost.XGBoostUtils;
import water.ExtensionManager;
import water.H2O;
import water.MRTask;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.io.*;
import java.util.*;

public class XGBoostUpdateTask extends MRTask<XGBoostUpdateTask> {

    private final XGBoostModelInfo _sharedModel;
    private final XGBoostOutput _output;
    private transient Booster _booster;
    private byte[] _rawBooster;

    private final XGBoostModel.XGBoostParameters _parms;
    private final int _tid;

    private IcedHashMapGeneric.IcedHashMapStringString rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString();

    public XGBoostUpdateTask(Booster booster,
                      XGBoostModelInfo inputModel,
                      XGBoostOutput _output,
                      XGBoostModel.XGBoostParameters _parms,
                      int tid,
                      Map<String, String> workerEnvs) {
        this._sharedModel = inputModel;
        this._output = _output;
        this._parms = _parms;
        this._tid = tid;
        this._rawBooster = hex.tree.xgboost.XGBoost.getRawArray(booster);
        rabitEnv.putAll(workerEnvs);
    }

    @Override
    protected void setupLocal() {
        if(H2O.ARGS.client) {
            return;
        }

        // We need to verify that the xgboost is available on the remote node
        if (!ExtensionManager.getInstance().isCoreExtensionEnabled(XGBoostExtension.NAME)) {
            throw new IllegalStateException("XGBoost is not available on the node " + H2O.SELF);
        }
        try {
            update();
        } catch (XGBoostError xgBoostError) {
            try {
                Rabit.shutdown();
            } catch (XGBoostError xgBoostError1) {
                xgBoostError1.printStackTrace();
            }
            xgBoostError.printStackTrace();
            throw new IllegalStateException("Failed XGBoost training.", xgBoostError);
        }
    }

    private void update() throws XGBoostError {
        HashMap<String, Object> params = XGBoostModel.createParams(_parms, _output);

        rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));

        DMatrix trainMat = null;
        try {
            trainMat = XGBoostUtils.convertFrameToDMatrix(
                    _sharedModel._dataInfoKey,
                    _fr,
                    true,
                    _parms._response_column,
                    _parms._weights_column,
                    _parms._fold_column,
                    _output._sparse);

            if (null == trainMat) {
                return;
            }

            // DON'T put this before createParams, createPrams calls train() which isn't supposed to be distributed
            // just to check if we have GPU on the machine
            Rabit.init(rabitEnv);

            if (_rawBooster == null) {
                HashMap<String, DMatrix> watches = new HashMap<>();
                _booster = ml.dmlc.xgboost4j.java.XGBoost.train(trainMat,
                        params,
                        0,
                        watches,
                        null,
                        null);
            } else {
                try {
                    _booster = Booster.loadModel(new ByteArrayInputStream(_rawBooster));
                    Log.debug("Booster created from bytes, raw size = " + _rawBooster.length);
                    // Set the parameters, some seem to get lost on save/load
                    _booster.setParams(params);
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new IllegalStateException("Failed to load the booster.", e);
                }

                _booster.update(trainMat, _tid);
            }
            _rawBooster = _booster.toByteArray();
        } finally {
            if (trainMat != null) {
                trainMat.dispose();
                // Rabit was not started if the matrix was not properly initialized
                try {
                    Rabit.shutdown();
                } catch (XGBoostError xgBoostError) {
                    Log.debug("Rabit shutdown during update failed", xgBoostError);
                }
            }
        }
    }

    @Override
    public void reduce(XGBoostUpdateTask mrt) {
        if(null == _rawBooster) {
            _rawBooster = mrt._rawBooster;
        }
    }

    // This is called from driver
    public Booster getBooster() {
        if (null == _booster) {
            try {
                _booster = Booster.loadModel(new ByteArrayInputStream(_rawBooster));
                Log.debug("Booster created from bytes, raw size = " + _rawBooster.length);
            } catch (XGBoostError | IOException xgBoostError) {
                throw new IllegalStateException("Failed to load the booster.", xgBoostError);
            }
        }
        return _booster;
    }
}
