package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.XGBoostExtension;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import hex.tree.xgboost.XGBoostUtils;
import water.ExtensionManager;
import water.H2O;
import water.MRTask;
import water.util.FileUtils;
import water.util.IcedHashMapGeneric;

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

    private String[] _featureMap;

    public XGBoostUpdateTask(Booster booster,
                      XGBoostModelInfo inputModel,
                      XGBoostOutput _output,
                      XGBoostModel.XGBoostParameters _parms,
                      int tid,
                      Map<String, String> workerEnvs,
                      String[] featureMap) {
        this._sharedModel = inputModel;
        this._output = _output;
        this._parms = _parms;
        this._tid = tid;
        this._featureMap = featureMap;
        this._rawBooster = hex.tree.xgboost.XGBoost.getRawArray(booster);
        rabitEnv.putAll(workerEnvs);
    }

    @Override
    protected void setupLocal() {
        // We need to verify that the xgboost is available on the remove node
        if (!ExtensionManager.getInstance().isCoreExtensionEnabled(XGBoostExtension.NAME)) {
            throw new IllegalStateException("XGBoost is not available on the node " + H2O.SELF);
        }
        try {
            update();
        } catch (XGBoostError xgBoostError) {
            xgBoostError.printStackTrace();
            throw new IllegalStateException("Failed XGBoost training.", xgBoostError);
        }
    }

    private void update() throws XGBoostError {
        HashMap<String, Object> params = XGBoostModel.createParams(_parms, _output);

        rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));

        // DON'T put this before createParams, createPrams calls train() which isn't supposed to be distributed
        // just to check if we have GPU on the machine
        Rabit.init(rabitEnv);
        try {
            DMatrix trainMat = XGBoostUtils.convertFrameToDMatrix(
                _sharedModel._dataInfoKey,
                _fr,
                true,
                _parms._response_column,
                _parms._weights_column,
                _parms._fold_column,
                _featureMap,
                _output._sparse);

            if (null == trainMat) {
                return;
            }

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
            Rabit.shutdown();
        }
    }

    @Override
    public void reduce(XGBoostUpdateTask mrt) {
        if(null == _rawBooster) {
            _rawBooster = mrt._rawBooster;
            _featureMap = mrt._featureMap;
        }
    }

    private void updateFeatureMapFile(File featureMapFile) {
        // For feature importances - write out column info
        OutputStream os = null;
        try {
            os = new FileOutputStream(featureMapFile);
            os.write(_featureMap[0].getBytes());
            os.close();
        } catch (IOException e) {
            H2O.fail("Cannot generate " + featureMapFile, e);
        } finally {
            FileUtils.close(os);
        }
    }

    // This is called from driver
    public Booster getBooster() {
        return getBooster(null);
    }

    public Booster getBooster(File featureMapFile) {
        if (null == _booster) {
            try {
                _booster = Booster.loadModel(new ByteArrayInputStream(_rawBooster));
            } catch (XGBoostError | IOException xgBoostError) {
                throw new IllegalStateException("Failed to load the booster.", xgBoostError);
            }
        }
        if (featureMapFile != null) {
            updateFeatureMapFile(featureMapFile);
        }
        return _booster;
    }
}
