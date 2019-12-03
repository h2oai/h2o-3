package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;
import water.fvec.Frame;

public class ScoringTimePerRow extends Iced<ScoringTimePerRow> implements LeaderboardExtension<Double, ScoringTimePerRow> {

    public static final String NAME = "prediction_time_per_row_millis";

    private final Key<Model> _modelId;
    private final Key<Frame> _leaderboardFrameId;
    private final Key<Frame> _trainingFrameId;

    private Double _scoringTimePerRowMillis;

    public ScoringTimePerRow(Key<Model> modelId) {
        this(modelId, null, null);
    }

    public ScoringTimePerRow(Key<Model> modelId, Key<Frame> leaderboardFrameId, Key<Frame> trainingFrameId) {
        _modelId = modelId;
        _leaderboardFrameId = leaderboardFrameId;
        _trainingFrameId = trainingFrameId;
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
        return "double";
    }

    @Override
    public String getColumnFormat() {
        return "%.3f";
    }

    @Override
    public Double getValue() {
        return _scoringTimePerRowMillis;
    }

    @Override
    public void setValue(Double value) {
        _scoringTimePerRowMillis = value;
    }

    @Override
    public boolean isNA() {
        return getValue() == null || getValue() < 0;
    }

    @Override
    public Double fetch() {
        if (getValue() == null) {
            try {
                Model model = _modelId.get();
                Frame scoringFrame = _leaderboardFrameId != null ? _leaderboardFrameId.get()
                                : _trainingFrameId != null ? _trainingFrameId.get()
                                : null;
                if (scoringFrame != null) {
                    long nrows = scoringFrame.numRows();
                    long start = System.currentTimeMillis();
                    model.score(scoringFrame).delete();
                    long stop = System.currentTimeMillis();
                    setValue((stop-start)/(double)nrows);
                } else {
                    setValue(-1d);
                }
            } catch (Exception e) {
                setValue(-1d);
            }
        }
        return getValue();
    }
}
