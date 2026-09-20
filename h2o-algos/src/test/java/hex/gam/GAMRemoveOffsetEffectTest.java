package hex.gam;

import hex.glm.GLMModel.GLMParameters.Family;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

/**
 * Flag-ON test for GH-16851 on GAM. GAM scores through its own GAMScore task (not Model.BigScore), so this
 * exercises the applyOffsetAtScoreTime() guard inside GAMScore: a remove_offset GAM's predictions must be
 * invariant to the offset column and equal the identically-fit plain GAM scored with a zeroed offset, and the
 * dual (offset-applied) metric view must match the plain model's metric.
 */
@RunWith(Parameterized.class)
public class GAMRemoveOffsetEffectTest extends TestUtil {

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> params() {
    return Arrays.asList(new Object[][]{
        {Family.gaussian, "AGE"},
        {Family.poisson,  "AGE"},
    });
  }

  @Parameterized.Parameter        public Family family;
  @Parameterized.Parameter(1)     public String response;

  private GAMModel train(Frame train, boolean removeOffset) {
    GAMModel.GAMParameters parms = new GAMModel.GAMParameters();
    parms._train = train._key;
    parms._response_column = response;
    parms._offset_column = "offset";
    parms._remove_offset_effects = removeOffset;
    parms._ignored_columns = new String[]{"ID", "PSA", "CAPSULE"};
    parms._family = family;
    parms._gam_columns = new String[][]{{"PSA"}};
    parms._num_knots = new int[]{5};
    parms._lambda = new double[]{0};
    GAMModel m = new GAM(parms).trainModel().get();
    Scope.track_generic(m);
    return m;
  }

  @Test
  public void removeOffsetEffectsScoresAsIfOffsetZero() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
      zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
      DKV.put(zeroed);

      GAMModel ro = train(train, true);
      GAMModel plain = train(train, false);

      Frame roOnOffset = Scope.track(ro.score(train));
      Frame roOnZeroed = Scope.track(ro.score(zeroed));
      Frame plainOnZeroed = Scope.track(plain.score(zeroed));
      Frame plainOnOffset = Scope.track(plain.score(train));

      // remove_offset predictions ignore the offset column and equal the identically-fit plain model
      // scored with a zero offset
      assertFrameEquals(roOnOffset, roOnZeroed, 0.0);
      assertFrameEquals(roOnOffset, plainOnZeroed, 1e-8);
      double maxDiff = 0;
      for (long r = 0; r < train.numRows(); r++)
        maxDiff = Math.max(maxDiff, Math.abs(plainOnOffset.vec(0).at(r) - roOnOffset.vec(0).at(r)));
      assertTrue("offset should matter for the plain model", maxDiff > 1e-6);

      // dual view: the offset-applied metrics are populated and match the plain model's metric
      assertNotNull("unrestricted training metrics should be populated",
              ro._output._training_metrics_unrestricted_model);
      Scope.track(plain.score(train));
      double plainOffsetApplied = hex.ModelMetrics.getFromDKV(plain, train).mse();
      assertEquals("unrestricted metric should equal the offset-applied plain model's metric",
              plainOffsetApplied, ro._output._training_metrics_unrestricted_model.mse(), 1e-6);
      assertNotEquals("restricted vs unrestricted metrics should differ",
              ro._output._training_metrics_unrestricted_model.mse(), ro._output._training_metrics.mse(), 1e-6);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void removeOffsetEffectsRejectsCrossValidation() {
    // GH-16851: GAM runs CV inside its internal GLM, which does not support remove_offset_effects —
    // GAM must reject the combination itself with a GAM-worded error
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      Vec offset = Scope.track(train.anyVec().makeCon(0.1));
      train.add("offset", offset);
      DKV.put(train);

      GAMModel.GAMParameters parms = new GAMModel.GAMParameters();
      parms._train = train._key;
      parms._response_column = response;
      parms._offset_column = "offset";
      parms._remove_offset_effects = true;
      parms._nfolds = 2;
      parms._ignored_columns = new String[]{"ID", "PSA", "CAPSULE"};
      parms._family = family;
      parms._gam_columns = new String[][]{{"PSA"}};
      parms._num_knots = new int[]{5};
      parms._lambda = new double[]{0};
      try {
        Scope.track_generic(new GAM(parms).trainModel().get());
        fail("GAM + cross-validation + remove_offset_effects should be rejected");
      } catch (Exception e) {
        assertTrue("error should name _remove_offset_effects, got: " + e.getMessage(),
                e.getMessage().contains("_remove_offset_effects"));
      }
    } finally {
      Scope.exit();
    }
  }
}
