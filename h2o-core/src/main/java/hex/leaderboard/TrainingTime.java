package hex.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;
import water.util.Log;

/**
 * A cell providing the time needed to train the model.
 */
public class TrainingTime extends Iced<TrainingTime> implements LeaderboardCell<Long, TrainingTime> {

    public static final LeaderboardColumn COLUMN = new LeaderboardColumn("training_time_ms", "long", "%s");

    private final Key<Model> _modelId;

    private Long _trainingTimeMillis;

    public TrainingTime(Model model) {
        _modelId = model._key;
        _trainingTimeMillis = model._output._run_time;
    }

    public TrainingTime(Key<Model> modelId) {
        _modelId = modelId;
    }

    @Override
    public LeaderboardColumn getColumn() {
        return COLUMN;
    }

    @Override
    public Key<Model> getModelId() {
        return _modelId;
    }

    @Override
    public Long getValue() {
        return _trainingTimeMillis;
    }

    @Override
    public void setValue(Long value) {
        _trainingTimeMillis = value;
    }

    @Override
    public boolean isNA() {
        return getValue() == null || getValue() < 0;
    }

    @Override
    public Long fetch() {
        if (getValue() == null) {
            try {
                setValue(_modelId.get()._output._run_time);
            } catch (Exception e) {
                Log.err("Could not retrieve training time for model "+_modelId, e);
                setValue(-1L);
            }
        }
        return getValue();
    }
}
