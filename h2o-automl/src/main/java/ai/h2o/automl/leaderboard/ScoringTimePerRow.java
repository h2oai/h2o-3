package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;
import water.fvec.Frame;

public class ScoringTimePerRow extends Iced<ScoringTimePerRow> implements LeaderboardColumn<Double, ScoringTimePerRow> {

    public static final LeaderboardColumnDescriptor DESC = new LeaderboardColumnDescriptor("predict_time_per_row_millis", "double", "%.6f");

    private final Key<Model> _modelId;
    private final Key<Frame> _leaderboardFrameId;
    private final Key<Frame> _trainingFrameId;

    private Double _scoringTimePerRowMillis;

    public ScoringTimePerRow(Model model, Frame leaderboardFrame, Frame trainingFrame) {
        this(model._key,
             leaderboardFrame == null ? null : leaderboardFrame._key,
             trainingFrame == null ? null : trainingFrame._key
        );
    }

    public ScoringTimePerRow(Key<Model> modelId, Key<Frame> leaderboardFrameId, Key<Frame> trainingFrameId) {
        _modelId = modelId;
        _leaderboardFrameId = leaderboardFrameId;
        _trainingFrameId = trainingFrameId;
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
                    long start = System.nanoTime();
                    model.score(scoringFrame).delete();
                    long stop = System.nanoTime();
                    setValue((stop-start)/nrows/1e6);
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
