package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Freezable;
import water.Key;

public interface LeaderboardExtension<V, SELF extends LeaderboardExtension> extends Freezable<SELF> {

    Key<Model> getModelId();
    String getName();
    String getColumnType();
    String getColumnFormat();
    V getValue();
    void setValue(V value);

    default boolean isNA() { return getValue() == null; }
    default V fetch() { return getValue(); }
}
