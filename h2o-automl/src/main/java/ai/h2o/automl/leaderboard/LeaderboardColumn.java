package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Freezable;
import water.Key;

public interface LeaderboardColumn<V, SELF extends LeaderboardColumn> extends Freezable<SELF> {
    LeaderboardColumnDescriptor getDescriptor();

    Key<Model> getModelId();
    V getValue();
    void setValue(V value);

    default boolean isNA() { return getValue() == null; }
    default V fetch() { return getValue(); }
}
