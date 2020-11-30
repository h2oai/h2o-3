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
        DMatrix train
    ) throws XGBoostError {
        if (checkpointBoosterBytes != null) {
            booster = BoosterHelper.loadModel(checkpointBoosterBytes);
            booster.setParams(params);
        } else {
            booster = Booster.newBooster(params, new DMatrix[]{train});
            booster.loadRabitCheckpoint();
        }
        booster.saveRabitCheckpoint();
    }

    public void update(DMatrix dtrain, int iter) throws XGBoostError {
        booster.update(dtrain, iter);
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
