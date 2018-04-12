package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostExtension;
import water.ExtensionManager;
import water.H2O;
import water.MRTask;
import static water.util.IcedHashMapGeneric.IcedHashMapStringString;
import water.*;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.io.*;
import java.util.*;

public class XGBoostUpdateTask extends MRTask<XGBoostUpdateTask> {

    private final IcedHashMapGeneric.IcedHashMapStringObject _nodeToMatrixWrapper;
    private final BoosterHolder _boosterHolder;

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
        _boosterHolder = wrap(booster);
        _tid = tid;
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

            final Booster booster;
            if (! _boosterHolder.hasBooster()) {
                HashMap<String, DMatrix> watches = new HashMap<>();
                booster = ml.dmlc.xgboost4j.java.XGBoost.train(trainMat,
                        _boosterParms.get(),
                        0,
                        watches,
                        null,
                        null);
            } else {
                // Do one iteration
                booster = _boosterHolder.get(_boosterParms);
                booster.update(trainMat, _tid);
            }
            assert booster != null;
            _boosterHolder.update(booster);
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
        _boosterHolder.update(mrt._boosterHolder);
    }

    // This is called from driver
    public Booster getBooster() {
        return unwrap(_boosterHolder);
    }

    private static BoosterHolder wrap(Booster booster) {
        if (H2O.ARGS.client || H2O.CLOUD.size() > 1)
            return new SerializedBoosterHolder(booster);
        else
            return new LiveBoosterHolder(booster);
    }

    private static Booster unwrap(BoosterHolder holder) {
        return holder.hasBooster() ? holder.getRaw() : null;
    }

    private interface BoosterHolder<T extends Iced>  extends Freezable<T> {
        boolean hasBooster();
        Booster getRaw();
        Booster get(BoosterParms parms) throws XGBoostError;
        void update(Booster booster) throws XGBoostError;
        void update(BoosterHolder holder);
    }

    private static class LiveBoosterHolder extends Iced<LiveBoosterHolder> implements BoosterHolder<LiveBoosterHolder> {
        private boolean _hasBooster;
        private transient Booster _booster;

        public LiveBoosterHolder() {}

        private LiveBoosterHolder(Booster booster) {
            _hasBooster = booster != null;
            _booster = booster;
        }

        @Override
        public boolean hasBooster() {
            return _hasBooster;
        }

        @Override
        public Booster getRaw() {
            if (! hasBooster()) {
                throw new IllegalStateException("Inconsistent state: booster not available");
            }
            return _booster;
        }

        @Override
        public Booster get(BoosterParms parms) throws XGBoostError {
            return getRaw(); // Live booster is already initialized
        }

        @Override
        public void update(Booster booster) {
            if (hasBooster() && (booster == null)) {
                throw new IllegalStateException("Inconsistent state: attept to delete an existing booster");
            }
            _hasBooster = true;
            _booster = booster;
        }

        @Override
        public void update(BoosterHolder holder) {
            update(((LiveBoosterHolder) holder)._booster);
        }
    }

    private static class SerializedBoosterHolder extends Iced<SerializedBoosterHolder> implements BoosterHolder<SerializedBoosterHolder> {
        private byte[] _boosterBytes;

        public SerializedBoosterHolder() {}

        private SerializedBoosterHolder(Booster booster) {
            if (booster != null) {
                _boosterBytes = hex.tree.xgboost.XGBoost.getRawArray(booster);
            }
        }

        @Override
        public boolean hasBooster() {
            return _boosterBytes != null;
        }

        @Override
        public Booster getRaw() {
            try {
                Booster booster = Booster.loadModel(new ByteArrayInputStream(_boosterBytes));
                Log.debug("Booster created from bytes, raw size = " + _boosterBytes.length);
                return booster;
            } catch (XGBoostError | IOException xgBoostError) {
                throw new IllegalStateException("Failed to load the booster.", xgBoostError);
            }
        }

        @Override
        public Booster get(BoosterParms parms) throws XGBoostError {
            Booster booster = getRaw();
            booster.setParams(parms.get());
            return booster;
        }

        @Override
        public void update(Booster booster) throws XGBoostError {
            _boosterBytes = booster.toByteArray();
        }

        @Override
        public void update(BoosterHolder holder) {
            _boosterBytes = ((SerializedBoosterHolder) holder)._boosterBytes;
        }
    }

}
