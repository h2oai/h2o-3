package ai.h2o.automl.leaderboard;

import ai.h2o.automl.ModelingStep;
import hex.Model;
import water.Iced;
import water.Key;

public class ModelProvider extends Iced<ModelProvider> implements LeaderboardCell<String, ModelProvider> {
    public static final LeaderboardColumn COLUMN = new LeaderboardColumn("provider", "string", "%s", true);
    final Key<Model> _modelId;
    final String _provider;

    public ModelProvider(Model model, ModelingStep step) {
        _modelId = model._key;
        _provider = step == null ? "" : step.getProvider();
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
        return _provider;
    }

    @Override
    public void setValue(String value) {
        throw new UnsupportedOperationException();
    }

}
