package hex.leaderboard;

import hex.Model;
import water.Freezable;
import water.Key;

public interface LeaderboardCell<V, SELF extends LeaderboardCell> extends Freezable<SELF> {
    /**
     * @return the column to which this cell belongs.
     */
    LeaderboardColumn getColumn();

    /**
     * @return the row index of this cell.
     */
    Key<Model> getModelId();

    /**
     * gets the current value of the cell.
     * If the value is not immediately available, this should return null, so that the client code can decide to call {@link #fetch()}.
     * This is an accessor, it is safe to call {@link #getValue()} multiple times without triggering any side effect.
     * @return the current cell value.
     */
    V getValue();

    /**
     * sets the cell value.
     * This can be useful for optimization, when the value is expensive to compute and available at some point during the automl run.
     * @param value
     */
    void setValue(V value);

    /**
     * @return true id the value is not available
     */
    default boolean isNA() { return getValue() == null; }

    /**
     * Fetch the value if necessary: this may be a long running task.
     * @return
     */
    default V fetch() { return getValue(); }
}
