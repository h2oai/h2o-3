package ai.h2o.automl.leaderboard;

import hex.Model;
import hex.leaderboard.LeaderboardCell;
import hex.leaderboard.LeaderboardColumn;
import water.Iced;
import water.Key;

/**
 * A cell computing lazily the size of a model.
 */
public class ModelSize extends Iced<ModelSize> implements LeaderboardCell<Long, ModelSize> {

    public static final LeaderboardColumn COLUMN = new LeaderboardColumn("model_size_bytes", "long", "%s");

    private final Key<Model> _modelId;

    private Long _model_size;

    public ModelSize(Key<Model> modelId) {
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
        return _model_size;
    }

    @Override
    public void setValue(Long value) {
        _model_size = value;
    }

    @Override
    public boolean isNA() {
        return getValue() == null || getValue() < 0;
    }

    @Override
    public Long fetch() {
        if (getValue() == null) {
            try {
                // PUBDEV-7124:
//                Model model = _modelId.get();
                // export binary model to temp folder
                // read size
                // delete saved model
            } catch (Exception e) {
                setValue(-1L);
            }
        }
        return getValue();
    }
}
