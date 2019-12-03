package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;

public class ModelSize extends Iced<ModelSize> implements LeaderboardExtension<Long, ModelSize> {

    public static final String NAME = "model_size_bytes";

    private final Key<Model> _modelId;

    private Long _model_size;

    public ModelSize(Key<Model> modelId) {
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
        return "long";
    }

    @Override
    public String getColumnFormat() {
        return "%s";
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
                // delete save model
            } catch (Exception e) {
                setValue(-1L);
            }
        }
        return getValue();
    }
}
