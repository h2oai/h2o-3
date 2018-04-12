package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.XGBoostExtension;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import water.*;
import water.util.IcedBoolean;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.io.*;
import java.util.*;

public class XGBoostUpdateTask extends MRTask<XGBoostUpdateTask> {

    private final IcedHashMapGeneric.IcedHashMapStringObject _nodeToMatrixWrapper;
    private final XGBoostOutput _output;
    private IcedBooster _boosterWrapper;

    private final XGBoostModel.XGBoostParameters _parms;
    private final int _tid;

    private IcedHashMapGeneric.IcedHashMapStringString rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString();

    public XGBoostUpdateTask(
                      XGBoostSetupTask setupTask,
                      Booster booster,
                      XGBoostOutput output,
                      XGBoostModel.XGBoostParameters parms,
                      int tid,
                      Map<String, String> workerEnvs) {
        _nodeToMatrixWrapper = setupTask._nodeToMatrixWrapper;
        _boosterWrapper = IcedBooster.wrap(booster);
        _output = output;
        _parms = parms;
        _tid = tid;
        rabitEnv.putAll(workerEnvs);
    }

    @Override
    protected void setupLocal() {
        if(H2O.ARGS.client) {
            _boosterWrapper = null; // to be sure that old booster will not be returned on the client
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
        HashMap<String, Object> params = XGBoostModel.createParams(_parms, _output);

        rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));

        try {
            // DON'T put this before createParams, createPrams calls train() which isn't supposed to be distributed
            // just to check if we have GPU on the machine
            Rabit.init(rabitEnv);

            if (_boosterWrapper == null) {
                HashMap<String, DMatrix> watches = new HashMap<>();
                _boosterWrapper = IcedBooster.wrap(ml.dmlc.xgboost4j.java.XGBoost.train(trainMat,
                        params,
                        0,
                        watches,
                        null,
                        null));
                _boosterWrapper.setParams(params);
            } else {
                // Refresh the parameters - for the cases when booster was freshly deserialized
                _boosterWrapper.setParams(params);
                // Do one iteration
                _boosterWrapper._booster.update(trainMat, _tid);
            }
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
        // always take the "other" booster - client doesn't have one
        _boosterWrapper = mrt._boosterWrapper;
    }

    // This is called from driver
    public Booster getBooster() {
        return IcedBooster.unwrap(_boosterWrapper);
    }

    private static class IcedBooster extends Iced<IcedBooster> {

        private transient boolean _initialized;
        private transient Booster _booster;
        private transient Map<String, Object> _params;

        public IcedBooster() {}

        private IcedBooster(Booster booster) {
            assert booster != null;
            _booster = booster;
            _initialized = true; // assume it is already initialized
            _params = null;
        }

        public final AutoBuffer write_impl(AutoBuffer ab) {
            byte[] rawBooster = hex.tree.xgboost.XGBoost.getRawArray(_booster);
            ab.putA1(rawBooster);
            ab.put(toIcedMap(_params));
            return ab;
        }

        public final IcedBooster read_impl(AutoBuffer ab) {
            try {
                byte[] rawBooster = ab.getA1();
                assert rawBooster != null;
                _initialized = false; // We will need to re-initialize the booster, some of the parameters seem to get lost on save/load
                _booster = Booster.loadModel(new ByteArrayInputStream(rawBooster));
                _params = fromIcedMap(ab.get(IcedHashMapGeneric.IcedHashMapStringObject.class));
                Log.debug("Booster created from bytes, raw size = " + rawBooster.length);
                return this;
            } catch (XGBoostError | IOException xgBoostError) {
                throw new IllegalStateException("Failed to load the booster.", xgBoostError);
            }
        }

        private IcedHashMapGeneric.IcedHashMapStringObject toIcedMap(Map<String, Object> m) {
            if (m == null)
                return null;
            IcedHashMapGeneric.IcedHashMapStringObject result = new IcedHashMapGeneric.IcedHashMapStringObject();
            for (Map.Entry<String, Object> entry : m.entrySet()) {
                if (entry.getValue() instanceof Boolean)
                    result.put(entry.getKey(), new IcedBoolean((boolean) entry.getValue()));
                else
                    result.put(entry.getKey(), entry.getValue());
            }
            return result;
        }

        private Map<String, Object> fromIcedMap(IcedHashMapGeneric.IcedHashMapStringObject m) {
            if (m == null)
                return null;
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : m.entrySet()) {
                if (entry.getValue() instanceof IcedBoolean)
                    result.put(entry.getKey(), ((IcedBoolean) entry.getValue())._val);
                else
                    result.put(entry.getKey(), entry.getValue());
            }
            return result;
        }

        private void setParams(Map<String, Object> params) {
            _params = params;
        }

        private Booster get() {
            if ((! _initialized) && (_params != null)) {
                try {
                    _booster.setParams(_params);
                    _initialized = true;
                } catch (XGBoostError xgBoostError) {
                    throw new IllegalStateException("Failed to initialize the booster.", xgBoostError);
                }
            }
            return _booster;
        }

        static Booster unwrap(IcedBooster icedBooster) {
            return icedBooster != null ? icedBooster.get() : null;
        }

        static IcedBooster wrap(Booster booster) {
            if (booster == null)
                return null;
            return new IcedBooster(booster);
        }

    }

}
