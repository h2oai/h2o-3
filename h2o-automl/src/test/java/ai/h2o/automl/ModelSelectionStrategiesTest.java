package ai.h2o.automl;

import ai.h2o.automl.ModelSelectionStrategies.KeepBestConstantSize;
import ai.h2o.automl.ModelSelectionStrategies.KeepBestN;
import ai.h2o.automl.ModelSelectionStrategies.KeepBestNFromSubgroup;
import ai.h2o.automl.ModelSelectionStrategies.LeaderboardHolder;
import ai.h2o.automl.dummy.DummyModel;
import ai.h2o.automl.events.EventLog;
import ai.h2o.automl.events.EventLogEntry;
import hex.Model;
import hex.leaderboard.Leaderboard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.*;
import static water.TestUtil.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ModelSelectionStrategiesTest {

    private final List<Keyed> toDelete = new ArrayList<>();
    private Frame fr;
    private double[] perfectPreds;
    private Model[] oldModels;
    private Model[] newModels;
    private Supplier<LeaderboardHolder> leaderboardSupplier;
    private static final Key<AutoML> dummy = Key.make();
    static class DummyScoreModel extends DummyModel {

        private final double[] _perfectPreds;
        private final int _goodPredsCount;

        public DummyScoreModel(String key, double[] perfectPreds, int goodPredsCount) {
            super(key);
            _perfectPreds = perfectPreds;
            _goodPredsCount = goodPredsCount;
        }

        @Override
        protected double[] score0(double[] data, double[] preds) {
            int rowIdx = (int) data[0];
            boolean ok = rowIdx < _goodPredsCount;
            double pred = ok ? _perfectPreds[rowIdx - 1] : 1 - _perfectPreds[rowIdx - 1];
            return ard(pred, pred, 1 - pred);
        }
    }

    @Before
    public void prepareModels() {
        fr = new Frame(
                Key.make("dummy_fr"),
                new String[]{"A", "B", "target"},
                new Vec[]{
                        ivec(1, 2, 3, 4, 5),
                        ivec(1, 2, 3, 4, 5),
                        cvec("foo", "foo", "foo", "bar", "bar"),
                }
        );
        perfectPreds = ard(1., 1., 1., 0., 0.);
        DKV.put(fr); toDelete.add(fr);

        leaderboardSupplier = () -> {
            String name = "selection_lb";
            EventLog el = EventLog.getOrMake(Key.make(name)); //toDelete.add(el);
            Leaderboard lb = Leaderboard.getOrMake(name, el.asLogger(EventLogEntry.Stage.ModelTraining),  fr, "logloss"); //toDelete.add(lb);
            return new LeaderboardHolder() {
                @Override
                public Leaderboard get() {
                    return lb;
                }

                @Override
                public void cleanup() {
                    lb.remove();
                    el.remove();
                }
            };
        };

        Model[] models = Arrays.asList(1., 2., 3., 4., 1.1, 2.2, 5.5).stream().map(score -> {
            int goodPredsCount = (int) Math.floor(score);
            DummyModel m = new DummyScoreModel("dummy_" + score, perfectPreds, goodPredsCount);
            m._parms._moreParams.put(goodPredsCount % 2 == 0 ? "even" : "odd", String.valueOf(goodPredsCount));
            m._output._names = fr.names();
            m._output._domains = ar(null, null, fr.vec("target").domain());
            DKV.put(m); toDelete.add(m);
            return m;
        }).toArray(Model[]::new);

        oldModels = ArrayUtils.subarray(models, 0, 4);
        newModels = ArrayUtils.subarray(models, 4, 3);
    }

    @After
    public void cleanup() {
        toDelete.forEach(Keyed::remove);
    }

     @Test public void testKeepBestN() {
        try {
            Scope.enter();
            ModelSelectionStrategy<DummyModel> strategy = new KeepBestN<>(2, leaderboardSupplier);

            ModelSelectionStrategy.Selection<DummyModel> selection = strategy.select(
                    Arrays.stream(oldModels).map(m -> m._key).toArray(Key[]::new),
                    Arrays.stream(newModels).map(m -> m._key).toArray(Key[]::new)
            );

            assertNotNull(selection);
            assertEquals(1, selection._add.length);
            assertArrayEquals(ar("dummy_5.5"), Arrays.stream(selection._add).map(Object::toString).toArray(String[]::new));
            assertEquals(3, selection._remove.length);
            assertArrayEquals(ar("dummy_1.0", "dummy_2.0", "dummy_3.0"), Arrays.stream(selection._remove).map(Object::toString).toArray(String[]::new));
        } finally {
            Scope.exit();
            EventLog.getOrMake(dummy).remove(true);
        }
    }

    @Test public void testKeepBestN_NoAdd() {
        try {
            Scope.enter();
            ModelSelectionStrategy<DummyModel> strategy = new KeepBestN<>(1, leaderboardSupplier);

            // swapping old and new for the sake of this test
            ModelSelectionStrategy.Selection<DummyModel> selection = strategy.select(
                    Arrays.stream(newModels).map(m -> m._key).toArray(Key[]::new),
                    Arrays.stream(oldModels).map(m -> m._key).toArray(Key[]::new)
            );

            assertNotNull(selection);
            assertEquals(0, selection._add.length);
            assertEquals(2, selection._remove.length);
            assertArrayEquals(ar("dummy_1.1", "dummy_2.2"), Arrays.stream(selection._remove).map(Object::toString).toArray(String[]::new));
        } finally {
            Scope.exit();
            EventLog.getOrMake(dummy).remove(true);
        }
    }

    @Test public void testKeepBestN_NoRemove() {
        try {
            Scope.enter();
            ModelSelectionStrategy<DummyModel> strategy = new KeepBestN<>(10, leaderboardSupplier);

            // swapping old and new for the sake of this test
            ModelSelectionStrategy.Selection<DummyModel> selection = strategy.select(
                    Arrays.stream(oldModels).map(m -> m._key).toArray(Key[]::new),
                    Arrays.stream(newModels).map(m -> m._key).toArray(Key[]::new)
            );

            assertNotNull(selection);
            assertEquals(3, selection._add.length);
            assertArrayEquals(ar("dummy_5.5", "dummy_2.2", "dummy_1.1"), Arrays.stream(selection._add).map(Object::toString).toArray(String[]::new));
            assertEquals(0, selection._remove.length);
        } finally {
            Scope.exit();
            EventLog.getOrMake(dummy).remove(true);
        }
    }

    @Test public void testKeepBestConstantSize() {
        try {
            Scope.enter();
            ModelSelectionStrategy<DummyModel> strategy = new KeepBestConstantSize<>(leaderboardSupplier);

            ModelSelectionStrategy.Selection<DummyModel> selection = strategy.select(
                    Arrays.stream(oldModels).map(m -> m._key).toArray(Key[]::new),
                    Arrays.stream(newModels).map(m -> m._key).toArray(Key[]::new)
            );

            assertNotNull(selection);
            assertEquals(1, selection._add.length);
            assertArrayEquals(ar("dummy_5.5"), Arrays.stream(selection._add).map(Object::toString).toArray(String[]::new));
            assertEquals(1, selection._remove.length);
            assertArrayEquals(ar("dummy_1.0"), Arrays.stream(selection._remove).map(Object::toString).toArray(String[]::new));
        } finally {
            Scope.exit();
            EventLog.getOrMake(dummy).remove(true);
        }
    }

    @Test public void testKeepBestNFromSubgroup() {
        try {
            Scope.enter();
            ModelSelectionStrategy<DummyModel> strategy = new KeepBestNFromSubgroup<>(
                    1,
                    k -> {
                        DummyModel m = k.get();
                        return m._parms._moreParams.containsKey("odd");
                    },
                    leaderboardSupplier
            );

            ModelSelectionStrategy.Selection<DummyModel> selection = strategy.select(
                    Arrays.stream(oldModels).map(m -> m._key).toArray(Key[]::new),
                    Arrays.stream(newModels).map(m -> m._key).toArray(Key[]::new)
            );

            assertNotNull(selection);
            assertEquals(1, selection._add.length);
            assertArrayEquals(ar("dummy_5.5"), Arrays.stream(selection._add).map(Object::toString).toArray(String[]::new));
            assertEquals(2, selection._remove.length);
            assertArrayEquals(ar("dummy_1.0", "dummy_3.0"), Arrays.stream(selection._remove).map(Object::toString).toArray(String[]::new));
        } finally {
            Scope.exit();
            EventLog.getOrMake(dummy).remove(true);
        }
    }
}
