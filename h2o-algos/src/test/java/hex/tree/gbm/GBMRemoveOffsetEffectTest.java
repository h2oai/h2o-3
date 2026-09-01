package hex.tree.gbm;

import hex.genmodel.utils.DistributionFamily;
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

import static water.TestUtil.*;

/**
 * Phase 1 for GH-16851: exercises the generic core scoring seam (Model.BigScore reads
 * _parms._remove_offset_effects) on a non-GLM algo. GLM has its own scoring path; GBM scores through the
 * shared BigScore, so this proves the one-line guard removes the offset for every BigScore-path algo.
 *
 * The parameter only affects scoring/metrics, not the fit, so a remove_offset model and a plain model
 * trained identically have the SAME trees. Therefore:
 *   (1) a remove_offset model's predictions are invariant to the offset column values, and
 *   (2) remove_offset.score(frameWithOffset) == plainModel.score(frameWithZeroedOffset)  (exactly).
 */
@RunWith(Parameterized.class)
public class GBMRemoveOffsetEffectTest extends TestUtil {

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> params() {
    return Arrays.asList(new Object[][]{
        {DistributionFamily.gaussian, "AGE"},
        {DistributionFamily.poisson,  "AGE"},
        {DistributionFamily.tweedie,  "AGE"},
        {DistributionFamily.bernoulli,"CAPSULE"},
    });
  }

  @Parameterized.Parameter        public DistributionFamily family;
  @Parameterized.Parameter(1)     public String response;

  private GBMModel train(Frame train, boolean removeOffset) {
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._train = train._key;
    parms._response_column = response;
    parms._offset_column = "offset";
    parms._remove_offset_effects = removeOffset;
    parms._distribution = family;
    if (family == DistributionFamily.tweedie) parms._tweedie_power = 1.5;
    parms._ntrees = 20;
    parms._max_depth = 4;
    parms._seed = 42;
    GBMModel m = new GBM(parms).trainModel().get();
    Scope.track_generic(m);
    return m;
  }

  @Test
  public void removeOffsetEffectsScoresAsIfOffsetZero() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      if (family == DistributionFamily.bernoulli)
        train.replace(train.find(response), train.vec(response).toCategoricalVec()).remove();
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      Frame zeroed = Scope.track(new Frame(Key.<Frame>make(), train.names(), train.vecs().clone()));
      zeroed.replace(zeroed.find("offset"), Scope.track(train.anyVec().makeCon(0)));
      DKV.put(zeroed);

      GBMModel ro = train(train, true);
      GBMModel plain = train(train, false);

      Frame roOnOffset = Scope.track(ro.score(train));
      Frame roOnZeroed = Scope.track(ro.score(zeroed));
      Frame plainOnZeroed = Scope.track(plain.score(zeroed));
      Frame plainOnOffset = Scope.track(plain.score(train));

      // (1) remove_offset predictions ignore the offset column value entirely
      assertFrameEquals(roOnOffset, roOnZeroed, 0.0);
      // (2) remove_offset scoring == the identically-fit plain model scored with a zero offset.
      // For bernoulli compare the probability columns only: the two models derive their default label
      // threshold from different training-metric views (restricted vs offset-applied), so the label
      // column may legitimately differ at threshold-boundary rows.
      if (family == DistributionFamily.bernoulli)
        assertFrameEquals(Scope.track(roOnOffset.subframe(1, roOnOffset.numCols())),
                Scope.track(plainOnZeroed.subframe(1, plainOnZeroed.numCols())), 0.0);
      else
        assertFrameEquals(roOnOffset, plainOnZeroed, 0.0);
      // sanity: the plain model DID use the offset, so it differs from the remove_offset model
      double maxDiff = 0;
      for (long r = 0; r < train.numRows(); r++)
        maxDiff = Math.max(maxDiff, Math.abs(plainOnOffset.vec(0).at(r) - roOnOffset.vec(0).at(r)));
      org.junit.Assert.assertTrue("offset should matter for the plain model", maxDiff > 1e-6);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void dualViewUnrestrictedMetricsPopulated() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      if (family == DistributionFamily.bernoulli)
        train.replace(train.find(response), train.vec(response).toCategoricalVec()).remove();
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      GBMModel ro = train(train, true);
      GBMModel plain = train(train, false);
      // primary (restricted / offset-removed) metrics are present, and the parallel unrestricted
      // (offset-applied) metrics were computed generically by ModelBuilder.scoreUnrestrictedOffsetMetrics()
      org.junit.Assert.assertNotNull(ro._output._training_metrics);
      org.junit.Assert.assertNotNull("unrestricted training metrics should be populated",
              ro._output._training_metrics_unrestricted_model);
      // Semantic oracles (NOT a bare not-equals, which could pass on FP path noise between the in-training
      // Score task and BigScore):
      // (1) the model's PRIMARY training metric is offset-REMOVED: it matches a fresh restricted BigScore
      //     pass of the same model (tolerance covers the two paths' float accumulation differences)
      Scope.track(ro.score(train));
      double roFreshRestricted = hex.ModelMetrics.getFromDKV(ro, train).mse();
      org.junit.Assert.assertEquals("primary training metric should be offset-removed",
              roFreshRestricted, ro._output._training_metrics.mse(), 1e-4);
      // (2) the unrestricted view matches the identically-fit plain model's offset-applied metric
      Scope.track(plain.score(train));
      double plainOffsetApplied = hex.ModelMetrics.getFromDKV(plain, train).mse();
      org.junit.Assert.assertEquals("unrestricted metric should equal the offset-applied plain model's metric",
              plainOffsetApplied, ro._output._training_metrics_unrestricted_model.mse(), 1e-6);
      // (3) the two views genuinely differ (offset carries signal in this setup)
      org.junit.Assert.assertNotEquals("restricted vs unrestricted metrics should differ (offset matters)",
              ro._output._training_metrics_unrestricted_model.mse(), ro._output._training_metrics.mse(), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  /**
   * GH-16851: the validation-frame half of the dual view. Also pins that every ModelMetrics key registered on
   * the model resolves in the DKV - the unrestricted pass scores a clone that SHARES _output, so a key it
   * registers and then removes would otherwise be left dangling.
   */
  @Test
  public void dualViewOnValidationFrameAndNoDanglingMetricsKeys() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      if (family == DistributionFamily.bernoulli)
        train.replace(train.find(response), train.vec(response).toCategoricalVec()).remove();
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);
      // A genuinely separate validation frame. NOTE: `new Frame(key, names, train.vecs().clone())` would NOT
      // work here - that clones the ARRAY, not the Vecs, giving a row-for-row alias of train and making the
      // _nobs assertion below compare a frame to itself. deepSlice takes real rows.
      long[] validRows = new long[(int) (train.numRows() / 3)];
      for (int i = 0; i < validRows.length; i++) validRows[i] = i * 3L; // every third row
      Frame valid = train.deepSlice(validRows, null);
      valid._key = Key.make();   // must be set BEFORE tracking, or Scope tracks the wrong key and it leaks
      DKV.put(valid);
      Scope.track(valid);

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = train._key;
      parms._valid = valid._key;
      parms._response_column = response;
      parms._offset_column = "offset";
      parms._remove_offset_effects = true;
      parms._distribution = family;
      if (family == DistributionFamily.tweedie) parms._tweedie_power = 1.5;
      parms._ntrees = 20;
      parms._max_depth = 4;
      parms._seed = 42;
      GBMModel ro = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());

      org.junit.Assert.assertNotNull("validation metrics should be present", ro._output._validation_metrics);
      org.junit.Assert.assertNotNull("unrestricted VALIDATION metrics should be populated",
              ro._output._validation_metrics_unrestricted_model);
      org.junit.Assert.assertNotEquals("restricted vs unrestricted validation metrics should differ",
              ro._output._validation_metrics_unrestricted_model.mse(), ro._output._validation_metrics.mse(), 1e-5);
      // both views must be computed over the same rows, otherwise comparing them is meaningless
      org.junit.Assert.assertEquals("both validation views must cover the same rows",
              ro._output._validation_metrics._nobs, ro._output._validation_metrics_unrestricted_model._nobs);
      org.junit.Assert.assertEquals("both training views must cover the same rows",
              ro._output._training_metrics._nobs, ro._output._training_metrics_unrestricted_model._nobs);
      // the two views must be distinguishable when printed
      org.junit.Assert.assertTrue("unrestricted view should say so in its description",
              ro._output._training_metrics_unrestricted_model._description != null
                      && ro._output._training_metrics_unrestricted_model._description.contains("Offset applied"));

      for (Key<hex.ModelMetrics> k : ro._output.getModelMetrics())
        org.junit.Assert.assertNotNull("dangling ModelMetrics key left on the model: " + k, DKV.get(k));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void scoringFrameWithoutOffsetColumnWorks() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      if (family == DistributionFamily.bernoulli)
        train.replace(train.find(response), train.vec(response).toCategoricalVec()).remove();
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      GBMModel ro = train(train, true);

      // a remove_offset model ignores the offset at scoring time, so a frame WITHOUT the offset column
      // must score (zero column substituted) and produce the same predictions as any offset value (GH-16851 P1-4)
      Frame noOffset = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      if (family == DistributionFamily.bernoulli)
        noOffset.replace(noOffset.find(response), noOffset.vec(response).toCategoricalVec()).remove();
      DKV.put(noOffset);

      Frame onNoOffset = Scope.track(ro.score(noOffset));
      Frame onOffset = Scope.track(ro.score(train));
      assertFrameEquals(onOffset, onNoOffset, 0.0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void removeOffsetEffectsRequiresOffsetColumn() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      if (family == DistributionFamily.bernoulli)
        train.replace(train.find(response), train.vec(response).toCategoricalVec()).remove();
      DKV.put(train);

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = train._key;
      parms._response_column = response;
      parms._remove_offset_effects = true;             // but NO offset_column
      parms._distribution = family;
      if (family == DistributionFamily.tweedie) parms._tweedie_power = 1.5;
      parms._ntrees = 5;
      GBM gbm = new GBM(parms);
      try {
        gbm.trainModel().get();
        org.junit.Assert.fail("expected validation error: remove_offset_effects without offset_column");
      } catch (Exception expected) {
        // the generic ModelBuilder.init() guard rejects this — make sure it failed for the right reason
        org.junit.Assert.assertTrue("unexpected failure: " + expected.getMessage(),
                expected.getMessage() != null && expected.getMessage().contains("offset_column"));
      }
    } finally {
      Scope.exit();
    }
  }

  /**
   * balance_classes replaces the training frame with a stratified resample. SharedTree must re-point
   * _offset at the resampled frame alongside _response and _weights, otherwise getInitialValue() combines
   * vectors of different lengths and training dies with "Unexpected incompatible espc". This is
   * independent of remove_offset_effects (which only affects scoring) — both are covered here.
   */
  @Test
  public void balanceClassesKeepsOffsetAligned() {
    org.junit.Assume.assumeTrue("balance_classes only applies to classification",
            family == DistributionFamily.bernoulli);
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      train.replace(train.find(response), train.vec(response).toCategoricalVec()).remove();
      Vec offset = Scope.track(train.anyVec().makeCon(0));
      for (long i = 0; i < offset.length(); i++) offset.set(i, 0.1 * (i % 7) - 0.3);
      train.add("offset", offset);
      DKV.put(train);

      for (boolean removeOffset : new boolean[]{false, true}) {
        GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
        parms._train = train._key;
        parms._response_column = response;
        parms._offset_column = "offset";
        parms._remove_offset_effects = removeOffset;
        parms._balance_classes = true;
        parms._distribution = family;
        parms._ntrees = 5;
        parms._seed = 42;
        GBMModel m = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());
        org.junit.Assert.assertNotNull("training metrics (remove_offset_effects=" + removeOffset + ")",
                m._output._training_metrics);
        Scope.track(m.score(train)).remove();
      }
    } finally {
      Scope.exit();
    }
  }
}
