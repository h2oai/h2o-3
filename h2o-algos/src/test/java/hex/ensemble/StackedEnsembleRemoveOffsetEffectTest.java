package hex.ensemble;

import hex.Model;
import hex.ensemble.StackedEnsembleModel.StackedEnsembleParameters;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;
import static water.TestUtil.*;

/**
 * GH-16851 flag-ON test for StackedEnsemble.
 * <p>
 * The base models score offset-free, but the ensemble used to re-apply the offset anyway: SE inherits
 * {@code _offset_column} from base model 0, {@code addNonPredictorsToLevelOneFrame} puts that column into the
 * level-one frame, and {@code Metalearner.setCommonParams} hands it to the metalearner (a GLM with the flag
 * off), which adds it back on the link scale. A user stacking two compliant base models therefore shipped an
 * ensemble that applied the exact variable they had removed — silently, with no warning.
 * <p>
 * The fix keeps the offset out of the level-one frame entirely (rather than flagging the metalearner, which
 * would collide with GLM's "remove_offset_effects is not supported with cross-validation" rule whenever
 * {@code metalearner_nfolds > 1}).
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class StackedEnsembleRemoveOffsetEffectTest {

  private Frame makeTrain() {
    Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
    Vec offset = Scope.track(train.anyVec().makeCon(0));
    // strong, signal-carrying offset so "the ensemble applied it" is unmistakable
    for (long i = 0; i < offset.length(); i++) offset.set(i, Math.cos(i) * 3);
    train.add("offset", offset);
    DKV.put(train);
    return train;
  }

  private Frame zeroOffset(Frame train) {
    Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
    zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
    DKV.put(zeroed);
    return zeroed;
  }

  private GBMModel baseGbm(Frame train, boolean removeOffset, long seed) {
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._train = train._key;
    parms._response_column = "AGE";
    parms._offset_column = "offset";
    parms._remove_offset_effects = removeOffset;
    parms._ignored_columns = new String[]{"ID"};
    parms._distribution = DistributionFamily.gaussian;
    parms._ntrees = 10;
    parms._max_depth = 3;
    parms._nfolds = 3;
    parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
    parms._keep_cross_validation_predictions = true;
    parms._seed = seed;
    return (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());
  }

  private StackedEnsembleModel stack(Frame train, GBMModel... base) {
    StackedEnsembleParameters seParms = new StackedEnsembleParameters();
    seParms._train = train._key;
    seParms._response_column = "AGE";
    seParms._ignored_columns = new String[]{"ID"};
    Key[] keys = new Key[base.length];
    for (int i = 0; i < base.length; i++) keys[i] = base[i]._key;
    seParms._base_models = keys;
    seParms._seed = 42;
    return (StackedEnsembleModel) Scope.track_generic(new StackedEnsemble(seParms).trainModel().get());
  }

  private double maxOffsetSensitivity(Model m, Frame train) {
    Frame withOffset = Scope.track(m.score(train));
    Frame zeroed = Scope.track(m.score(zeroOffset(train)));
    double d = 0;
    for (long r = 0; r < train.numRows(); r++)
      d = Math.max(d, Math.abs(withOffset.vec(0).at(r) - zeroed.vec(0).at(r)));
    return d;
  }

  /**
   * The regression this pins: base models remove the offset, so the ensemble built on them must too.
   * Before the fix the ensemble's predictions moved ~1:1 with the offset column.
   */
  @Test
  public void ensembleOfRemoveOffsetBaseModelsIsOffsetFree() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      GBMModel b1 = baseGbm(train, true, 1);
      GBMModel b2 = baseGbm(train, true, 2);

      assertEquals("base model 1 must be offset-free", 0.0, maxOffsetSensitivity(b1, train), 1e-9);
      assertEquals("base model 2 must be offset-free", 0.0, maxOffsetSensitivity(b2, train), 1e-9);

      StackedEnsembleModel se = stack(train, b1, b2);
      assertTrue("SE must inherit remove_offset_effects from its base models",
              se._parms._remove_offset_effects);
      assertEquals("the ensemble must not re-apply the offset its base models removed",
              0.0, maxOffsetSensitivity(se, train), 1e-9);
    } finally {
      Scope.exit();
    }
  }

  /** Control: with plain (offset-applying) base models the ensemble still uses the offset, as before. */
  @Test
  public void ensembleOfPlainBaseModelsStillAppliesTheOffset() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      StackedEnsembleModel se = stack(train, baseGbm(train, false, 1), baseGbm(train, false, 2));
      assertFalse(se._parms._remove_offset_effects);
      assertTrue("plain ensemble must still apply the offset", maxOffsetSensitivity(se, train) > 1e-6);
    } finally {
      Scope.exit();
    }
  }

  /** A remove_offset ensemble ignores the offset, so scoring frames may omit the column entirely. */
  @Test
  public void offsetColumnMayBeAbsentAtScoreTime() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      StackedEnsembleModel se = stack(train, baseGbm(train, true, 1), baseGbm(train, true, 2));
      Frame noOffset = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
      noOffset.remove("offset");
      DKV.put(noOffset);
      Frame preds = Scope.track(se.score(noOffset));
      Frame withOffset = Scope.track(se.score(train));
      assertFrameEquals(withOffset, preds, 1e-9);
    } finally {
      Scope.exit();
    }
  }

  /** Mixing a remove_offset base model with a plain one is ambiguous and must be rejected, not guessed. */
  @Test
  public void mixedBaseModelsAreRejected() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      GBMModel ro = baseGbm(train, true, 1);
      GBMModel plain = baseGbm(train, false, 2);
      try {
        stack(train, ro, plain);
        fail("mixing remove_offset and plain base models must be rejected");
      } catch (Exception e) {
        assertTrue("expected a remove_offset_effects mismatch error, got: " + e.getMessage(),
                e.getMessage() != null && e.getMessage().contains("remove_offset_effects"));
      }
    } finally {
      Scope.exit();
    }
  }
}
