package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;

public class ModelSize extends Iced<ModelSize> implements LeaderboardColumn<Long, ModelSize> {

    public static final LeaderboardColumnDescriptor DESC = new LeaderboardColumnDescriptor("model_size_bytes", "long", "%s");

    private final Key<Model> _modelId;

    private Long _model_size;

    public ModelSize(Key<Model> modelId) {
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
