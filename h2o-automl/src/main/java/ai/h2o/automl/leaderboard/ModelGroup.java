package ai.h2o.automl.leaderboard;

import ai.h2o.automl.ModelingStep;
import hex.Model;
import water.Iced;
import water.Key;

public class ModelGroup extends Iced<ModelGroup> implements LeaderboardCell<Integer, ModelGroup> {
    public static final LeaderboardColumn COLUMN = new LeaderboardColumn("group", "int", "%s", true);
    final Key<Model> _modelId;
    final int _priorityGroup;

    public ModelGroup(Model model, ModelingStep step) {
        _modelId = model._key;
        _priorityGroup = step == null ? -1 : step.getPriorityGroup();
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
    public Integer getValue() {
        return _priorityGroup;
    }

    @Override
    public void setValue(Integer value) {
        throw new UnsupportedOperationException();
    }

}
