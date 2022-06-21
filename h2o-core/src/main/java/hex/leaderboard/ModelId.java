package hex.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;

/**
 * A cell for the model id column.
 */
public class ModelId extends Iced<ModelId> implements LeaderboardCell<String, ModelId> {

    public static final LeaderboardColumn COLUMN = new LeaderboardColumn("model_id", "string", "%s");

    private final Key<Model> _modelId;

    public ModelId(Key<Model> modelId) {
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
    public String getValue() {
        return _modelId.toString();
    }

    @Override
    public void setValue(String value) { throw new UnsupportedOperationException(); }

}
