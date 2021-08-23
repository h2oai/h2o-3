package ai.h2o.automl.leaderboard;

import ai.h2o.automl.ModelingStep;
import hex.Model;
import water.Iced;
import water.Key;

public class ModelStep extends Iced<ModelStep> implements LeaderboardCell<String, ModelStep> {
    public static final LeaderboardColumn COLUMN = new LeaderboardColumn("step", "string", "%s", true);
    final Key<Model> _modelId;
    final String _stepId;

    public ModelStep(Model model, ModelingStep step) {
        _modelId = model._key;
        _stepId = step == null ? "" : step.getId();
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
    public String getValue() {
        return _stepId;
    }

    @Override
    public void setValue(String value) {
        throw new UnsupportedOperationException();
    }

}
