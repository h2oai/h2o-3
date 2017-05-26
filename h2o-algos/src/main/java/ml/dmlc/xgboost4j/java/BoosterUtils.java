package ml.dmlc.xgboost4j.java;

public class BoosterUtils {

    public static void saveRabitCheckpoint(Booster booster) throws XGBoostError {
        booster.saveRabitCheckpoint();
    }

    public static int loadRabitCheckpoint(Booster booster) throws XGBoostError {
        return booster.loadRabitCheckpoint();
    }
}
