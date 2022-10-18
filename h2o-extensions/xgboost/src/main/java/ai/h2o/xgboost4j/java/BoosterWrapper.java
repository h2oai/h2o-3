package ai.h2o.xgboost4j.java;

import hex.tree.xgboost.util.BoosterHelper;

import java.util.Map;

/**
 * Wrapper to expose package private methods
 */
public class BoosterWrapper {

    private final Booster booster;

    public BoosterWrapper(
        byte[] checkpointBoosterBytes,
        Map<String, Object> params,
        DMatrix train,
        DMatrix valid
    ) throws XGBoostError {
        if (checkpointBoosterBytes != null) {
            booster = BoosterHelper.loadModel(checkpointBoosterBytes);
            booster.setParams(params);
        } else {
            DMatrix[] cacheMats = valid == null ? new DMatrix[]{train} : new DMatrix[]{train, valid};
            booster = Booster.newBooster(params, cacheMats);
            booster.loadRabitCheckpoint();
        }
        booster.saveRabitCheckpoint();
    }

    public void update(DMatrix dtrain, int iter) throws XGBoostError {
        booster.update(dtrain, iter);
    }

    public String evalSet(DMatrix dtrain, DMatrix dvalid, int iter) throws XGBoostError {
        if (dvalid == null) {
            return booster.evalSet(new DMatrix[]{dtrain}, new String[]{"train"}, iter);
        } else {
            return booster.evalSet(new DMatrix[]{dtrain, dvalid}, new String[]{"train", "valid"}, iter);
        }
    }

    public void saveRabitCheckpoint() throws XGBoostError {
        booster.saveRabitCheckpoint();
    }

    public byte[] toByteArray() throws XGBoostError {
        return booster.toByteArray();
    }

    public void dispose() {
        booster.dispose();
    }

    public Booster getBooster() {
        return booster;
    }
}
