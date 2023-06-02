package hex.leaderboard;

import hex.Model;
import water.Iced;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;

/**
 * A cell computing lazily the average time needed to score a single row with the model.
 * If there is a leaderboard frame available, this average time will be computed by scoring the entire frame.
 * Otherwise, the training frame will be used.
 */
public class ScoringTimePerRow extends Iced<ScoringTimePerRow> implements LeaderboardCell<Double, ScoringTimePerRow> {

    public static final LeaderboardColumn COLUMN = new LeaderboardColumn("predict_time_per_row_ms", "double", "%.6f");

    private final Key<Model> _modelId;
    private final Key<Frame> _leaderboardFrameId;
    private Double _scoringTimePerRowMillis;

    public ScoringTimePerRow(Model model, Frame leaderboardFrame) {
        this(model._key,
             leaderboardFrame == null ? null : leaderboardFrame._key
        );
    }

    public ScoringTimePerRow(Key<Model> modelId, Key<Frame> leaderboardFrameId) {
        _modelId = modelId;
        _leaderboardFrameId = leaderboardFrameId;
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
    public Double getValue() {
        if (_scoringTimePerRowMillis == null && _leaderboardFrameId == null) {
            throw new H2OIllegalArgumentException("predict_time_per_row_ms requires a leaderboard frame!");
        }
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
