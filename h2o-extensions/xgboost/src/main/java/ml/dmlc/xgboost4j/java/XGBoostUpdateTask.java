package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostExtension;
import water.ExtensionManager;
import water.H2O;
import water.MRTask;
import static water.util.IcedHashMapGeneric.IcedHashMapStringObject;
import static water.util.IcedHashMapGeneric.IcedHashMapStringString;
import water.util.Log;

import java.io.*;
import java.util.*;

public class XGBoostUpdateTask extends MRTask<XGBoostUpdateTask> {

    private final IcedHashMapStringObject _nodeToMatrixWrapper;
    private transient Booster _booster;
    private byte[] _rawBooster;

    private final BoosterParms _boosterParms;
    private final int _tid;

    private IcedHashMapStringString rabitEnv = new IcedHashMapStringString();

    public XGBoostUpdateTask(
                      XGBoostSetupTask setupTask,
                      Booster booster,
                      BoosterParms boosterParms,
                      int tid,
                      Map<String, String> workerEnvs) {
        _nodeToMatrixWrapper = setupTask._nodeToMatrixWrapper;
        _boosterParms = boosterParms;
        _tid = tid;
        _rawBooster = hex.tree.xgboost.XGBoost.getRawArray(booster);
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
            PersistentDMatrix.Wrapper wrapper = (PersistentDMatrix.Wrapper) _nodeToMatrixWrapper.get(H2O.SELF.toString());
            if (wrapper == null)
                return;
            DMatrix matrix = wrapper.get();
            update(matrix);
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

    private void update(DMatrix trainMat) throws XGBoostError {
        rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));

        try {
            // DON'T put this before createParams, createPrams calls train() which isn't supposed to be distributed
            // just to check if we have GPU on the machine
            Rabit.init(rabitEnv);

            if (_rawBooster == null) {
                HashMap<String, DMatrix> watches = new HashMap<>();
                _booster = ml.dmlc.xgboost4j.java.XGBoost.train(trainMat,
                        _boosterParms.get(),
                        0,
                        watches,
                        null,
                        null);
            } else {
                try {
                    _booster = Booster.loadModel(new ByteArrayInputStream(_rawBooster));
                    Log.debug("Booster created from bytes, raw size = " + _rawBooster.length);
                    // Set the parameters, some seem to get lost on save/load
                    _booster.setParams(_boosterParms.get());
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new IllegalStateException("Failed to load the booster.", e);
                }

                _booster.update(trainMat, _tid);
            }
            _rawBooster = _booster.toByteArray();
        } finally {
            try {
                Rabit.shutdown();
            } catch (XGBoostError xgBoostError) {
                Log.debug("Rabit shutdown during update failed", xgBoostError);
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
