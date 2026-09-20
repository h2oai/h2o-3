package hex.hglm;

import hex.hglm.HGLMModel.HGLMParameters;
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
 * GH-16851 flag-ON test for HGLM.
 * <p>
 * HGLM adds the offset straight onto the linear predictor
 * ({@code HGLMScore.scoreRow}: {@code preds[0] = x.beta + z.ubeta + r.offset}) and hands the same
 * {@code r.offset} to {@code MetricBuilderHGLM.perRow}, so zeroing {@code r.offset} once in
 * {@code HGLMScore.map} removes it from predictions and metrics together — the same seam GAM uses.
 * <p>
 * remove_offset_effects does not change the fit, so for the same model
 * {@code plain(offset) - removeOffset(row) == offset} exactly, row by row.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class HGLMRemoveOffsetEffectTest {

  private Frame makeTrain() {
    Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
    // RACE is the level-2 grouping unit; it must be categorical
    train.replace(train.find("RACE"), train.vec("RACE").toCategoricalVec()).remove();
    Vec offset = Scope.track(train.anyVec().makeCon(0));
    for (long i = 0; i < offset.length(); i++) offset.set(i, 0.25 * (i % 9) - 1.0);
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

  private HGLMModel train(Frame train, boolean removeOffset) {
    HGLMParameters parms = new HGLMParameters();
    parms._train = train._key;
    parms._response_column = "AGE";
    parms._group_column = "RACE";
    parms._random_columns = new String[]{"PSA"};
    parms._offset_column = "offset";
    parms._remove_offset_effects = removeOffset;
    parms._ignored_columns = new String[]{"ID"};
    parms._max_iterations = 10;
    parms._seed = 42;
    return (HGLMModel) Scope.track_generic(new HGLM(parms).trainModel().get());
  }

  @Test
  public void removeOffsetPredictionsIgnoreTheOffsetColumn() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      HGLMModel ro = train(train, true);
      assertFrameEquals(Scope.track(ro.score(train)), Scope.track(ro.score(zeroOffset(train))), 0.0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void removeOffsetPredictionEqualsPlainPredictionMinusTheOffset() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      HGLMModel ro = train(train, true);
      HGLMModel plain = train(train, false);

      Frame roPreds = Scope.track(ro.score(train));
      Frame plainPreds = Scope.track(plain.score(train));
      Vec offset = train.vec("offset");

      double maxErr = 0;
      for (long r = 0; r < train.numRows(); r++)
        maxErr = Math.max(maxErr, Math.abs((plainPreds.vec(0).at(r) - roPreds.vec(0).at(r)) - offset.at(r)));
      assertEquals("plain(offset) - removeOffset(row) must equal the offset exactly", 0.0, maxErr, 1e-8);
    } finally {
      Scope.exit();
    }
  }

  /** Metrics must follow the predictions: the restricted view is computed with the offset zeroed too. */
  @Test
  public void metricsFollowTheRestrictedView() {
    Scope.enter();
    try {
      Frame train = makeTrain();
      HGLMModel ro = train(train, true);
      HGLMModel plain = train(train, false);
      Frame zeroed = zeroOffset(train); // one frame — the metric is keyed on it
      Scope.track(plain.score(zeroed));

      assertEquals("restricted training MSE must match the identically-fit plain model scored at offset 0",
              hex.ModelMetrics.getFromDKV(plain, zeroed).mse(),
              ro._output._training_metrics.mse(), 1e-6);
    } finally {
      Scope.exit();
    }
  }
}
