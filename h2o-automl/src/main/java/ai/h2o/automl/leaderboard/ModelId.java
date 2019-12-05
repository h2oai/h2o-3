package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;

public class ModelId extends Iced<ModelId> implements LeaderboardColumn<String, ModelId> {

    public static final LeaderboardColumnDescriptor DESC = new LeaderboardColumnDescriptor("model_id", "string", "%s");

    private final Key<Model> _modelId;

    public ModelId(Key<Model> modelId) {
        _modelId = modelId;
    }

    @Override
    public LeaderboardColumnDescriptor getDescriptor() {
        return DESC;
    }

    @Override
    public Key<Model> getModelId() {
        return _modelId;
    }


    @Override
    public String getValue() {
        return _modelId.toString();
    }

    @Override
    public void setValue(String value) { throw new UnsupportedOperationException(); }

}
