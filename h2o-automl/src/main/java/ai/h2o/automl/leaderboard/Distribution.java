package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;

public class Distribution extends Iced<Distribution> implements LeaderboardCell<String, Distribution> {
    public static final LeaderboardColumn COLUMN = new LeaderboardColumn("distribution", "string", "%s");
    final Key<Model> _modelId;
    private String _distribution;

    public Distribution(Model model) {
        this._modelId = model._key;
        this._distribution = model._parms.getDistributionFamily().name();
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
        return _distribution;
    }

    @Override
    public void setValue(String value) {
        throw new UnsupportedOperationException();
    }

}
