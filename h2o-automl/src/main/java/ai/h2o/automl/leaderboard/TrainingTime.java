package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;
import water.util.Log;

public class TrainingTime extends Iced<TrainingTime> implements LeaderboardExtension<Long, TrainingTime> {

    public static final String NAME = "training_time_secs";

    private final Key<Model> _modelId;

    private Long _trainingTimeSecs;

    public TrainingTime(Model model) {
        _modelId = model._key;
        _trainingTimeSecs = model._output._run_time;
    }

    public TrainingTime(Key<Model> modelId) {
        _modelId = modelId;
    }

    @Override
    public Key<Model> getModelId() {
        return _modelId;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getColumnType() {
        return "int";
    }

    @Override
    public String getColumnFormat() {
        return "%s";
    }

    @Override
    public Long getValue() {
        return _trainingTimeSecs;
    }

    @Override
    public void setValue(Long value) {
        _trainingTimeSecs = value;
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
