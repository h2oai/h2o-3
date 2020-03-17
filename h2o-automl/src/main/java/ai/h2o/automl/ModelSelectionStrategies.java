package ai.h2o.automl;

import ai.h2o.automl.leaderboard.Leaderboard;
import hex.Model;
import water.Key;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ModelSelectionStrategies {

    public static abstract class LeaderboardBasedSelectionStrategy<M extends Model> implements ModelSelectionStrategy<M> {

        final Supplier<Leaderboard> _leaderboardSupplier;

        public LeaderboardBasedSelectionStrategy(Supplier<Leaderboard> leaderboardSupplier) {
            _leaderboardSupplier = leaderboardSupplier;
        }

        Leaderboard makeSelectionLeaderboard() {
            return _leaderboardSupplier.get();
        }
    }

    public static class KeepBestN<M extends Model> extends LeaderboardBasedSelectionStrategy<M>{

        private final int _N;

        public KeepBestN(int N, Supplier<Leaderboard> leaderboardSupplier) {
            super(leaderboardSupplier);
            _N = N;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Selection<M> select(Key<M>[] originalModels, Key<M>[] newModels) {
            Leaderboard tmpLeaderboard = makeSelectionLeaderboard();
            tmpLeaderboard.addModels((Key<Model>[]) originalModels);
            tmpLeaderboard.addModels((Key<Model>[]) newModels);
            Key<Model>[] sortedKeys = tmpLeaderboard.getModelKeys();
            Key<Model>[] bestN = ArrayUtils.subarray(sortedKeys, 0, Math.min(sortedKeys.length, _N));
            Key<M>[] toAdd = Arrays.stream(bestN).filter(k -> !ArrayUtils.contains(originalModels, k)).toArray(Key[]::new);
            Key<M>[] toRemove = Arrays.stream(originalModels).filter(k -> !ArrayUtils.contains(bestN, k)).toArray(Key[]::new);
            return new Selection<>(toAdd, toRemove);
        }
    }

    public static class KeepBestConstantSize<M extends Model> extends LeaderboardBasedSelectionStrategy<M> {

        public KeepBestConstantSize(Supplier<Leaderboard> leaderboardSupplier) {
            super(leaderboardSupplier);
        }

        @Override
        public Selection<M> select(Key<M>[] originalModels, Key<M>[] newModels) {
            return new KeepBestN<M>(originalModels.length, _leaderboardSupplier).select(originalModels, newModels);
        }
    }

    public static class KeepBestNFromSubgroup<M extends Model> extends LeaderboardBasedSelectionStrategy<M> {

        private final Predicate<Key<M>> _criterion;
        private final int _N;

        public KeepBestNFromSubgroup(int N, Predicate<Key<M>> criterion, Supplier<Leaderboard> leaderboardSupplier) {
            super(leaderboardSupplier);
            _criterion = criterion;
            _N = N;
        }

        @Override
        public Selection<M> select(Key<M>[] originalModels, Key<M>[] newModels) {
            Key<M>[] originalModelsSubgroup = Arrays.stream(originalModels).filter(_criterion).toArray(Key[]::new);
            Key<M>[] newModelsSubGroup = Arrays.stream(newModels).filter(_criterion).toArray(Key[]::new);
            return new KeepBestN<M>(_N, _leaderboardSupplier).select(originalModelsSubgroup, newModelsSubGroup);
        }
    }

}
