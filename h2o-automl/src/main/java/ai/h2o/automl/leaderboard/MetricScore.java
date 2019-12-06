package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;

import java.util.HashMap;
import java.util.Map;

public class MetricScore extends Iced<MetricScore> implements LeaderboardCell<Double, MetricScore> {

    private static final Map<String, LeaderboardColumn> DESCRIPTORS = new HashMap<>();

    public static LeaderboardColumn getDescriptor(String metric) {
        if (!DESCRIPTORS.containsKey(metric)) {
            DESCRIPTORS.put(metric, new LeaderboardColumn(metric, "double", "%.6f"));
        }
        return DESCRIPTORS.get(metric);
    }

    private final Key<Model> _modelId;
    private final String _metric;

    private Double _score;

    public MetricScore(Key<Model> modelId, String metric, Double score) {
        _modelId = modelId;
        _metric = metric;
        _score = score;
    }

    @Override
    public LeaderboardColumn getColumn() {
        return MetricScore.getDescriptor(_metric);
    }

    @Override
    public Key<Model> getModelId() {
        return _modelId;
    }

    @Override
    public Double getValue() {
        return _score;
    }

    @Override
    public void setValue(Double value) {
        _score = value;
    }
}
