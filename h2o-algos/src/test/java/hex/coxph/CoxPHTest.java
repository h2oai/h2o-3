package hex.coxph;

import hex.ModelMetricsRegressionCoxPH;
import hex.StringPair;
import hex.genmodel.MojoModel;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.parameters.ModelParameter;
import hex.genmodel.descriptor.ModelDescriptor;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.CoxPHModelPrediction;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.*;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.PojoUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static water.TestUtil.parseAndTrackTestFile;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class CoxPHTest extends Iced<CoxPHTest> {

  @Test
  public void testCoxPHEfron1Var() {
    try {
      Scope.enter();
      final Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv");

      final CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._calc_cumhaz = true;
      parms._train           = fr._key;
      parms._start_column    = "start";
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;
      assertEquals("Surv(start, stop, event) ~ age", parms.toFormula(fr));

      final CoxPH builder = new CoxPH(parms);
      final CoxPHModel model = builder.trainModel().get();
      Scope.track_generic(model);

      assertEquals(model._output._coef[0],        0.0307077486571334,   1e-8);
      assertEquals(model._output._var_coef[0][0], 0.000203471477951459, 1e-8);
      assertEquals(model._output._null_loglik,    -298.121355672984,    1e-8);
      assertEquals(model._output._loglik,         -295.536762216228,    1e-8);
      assertEquals(model._output._score_test,     4.64097294749287,     1e-8);
      assertTrue(model._output._iter >= 1);
      assertEquals(model._output._x_mean_num[0][0],  -2.48402655078554,    1e-8);
      assertEquals(model._output._n,              172);
      assertEquals(model._output._total_event,    75);
      assertEquals(model._output._wald_test,      4.6343882547245,      1e-8);
      assertEquals(model._output._var_cumhaz_2_matrix.rows(), 110);
      assertEquals(model._output._concordance, 0.5806350696073831, 0.0001);

      final ModelMetricsRegressionCoxPH mm = (ModelMetricsRegressionCoxPH) model._output._training_metrics;
      assertEquals(0.5806350696073831, mm.concordance(), 0.00001d);
      assertEquals(2676, mm.discordant());
      assertEquals(10, mm.tiedY());
      assertEquals(model._output._concordance, mm.concordance(), 0.0001);
      
      String expectedSummary = 
              "CoxPH Model (summary):\n" +
              "                         Formula  Likelihood ratio test  Concordance  Number of Observations  Number of Events\n" +
              "  Surv(start, stop, event) ~ age                5.16919      0.58064                     172                75\n";
      assertEquals(expectedSummary, model._output._model_summary.toString());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCoxPHEfron1VarScoring() {
    try {
      Scope.enter();
      final Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv");

      CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._calc_cumhaz = true;
      parms._train           = fr._key;
      parms._start_column    = "start";
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;
      assertEquals("Surv(start, stop, event) ~ age", parms.toFormula(fr));

      final CoxPH builder = new CoxPH(parms);
      final CoxPHModel model = (CoxPHModel) Scope.track_generic(builder.trainModel().get());

      assertNotNull(model);
      final Frame linearPredictors = Scope.track(model.score(fr));
      assertEquals(fr.numRows(), linearPredictors.numRows());

      final ModelMetricsRegressionCoxPH mm = (ModelMetricsRegressionCoxPH) model._output._training_metrics;
      assertEquals(0.5806350696073831, mm.concordance(), 0.00001d);
      assertEquals(2676, mm.discordant());
      assertEquals(10, mm.tiedY());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCoxPHBreslow1Var()  {
    try {
      Scope.enter();
      final Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv");

      final CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._calc_cumhaz = true;
      parms._train           = fr._key;
      parms._start_column    = "start";
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.breslow;
      assertEquals("Surv(start, stop, event) ~ age", parms.toFormula(fr));

      final CoxPH builder = new CoxPH(parms);
      final CoxPHModel model = builder.trainModel().get();
      Scope.track_generic(model);

      assertEquals(model._output._coef[0],        0.0306910411003801,   1e-8);
      assertEquals(model._output._var_coef[0][0], 0.000203592486905101, 1e-8);
      assertEquals(model._output._null_loglik,    -298.325606736463,    1e-8);
      assertEquals(model._output._loglik,         -295.745227177782,    1e-8);
      assertEquals(model._output._score_test,     4.63317821557301,     1e-8);
      assertTrue(model._output._iter >= 1);
      assertEquals(model._output._x_mean_num[0][0],  -2.48402655078554,    1e-8);
      assertEquals(model._output._n,              172);
      assertEquals(model._output._total_event,    75);
      assertEquals(model._output._wald_test,      4.62659510743282,     1e-8);
      assertEquals(model._output._var_cumhaz_2_matrix.rows(), 110);
      assertEquals(0.5806350696073831, ((ModelMetricsRegressionCoxPH)model._output._training_metrics).concordance(), 0.00001d);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCoxPHEfron1VarNoStart() {
    try {
      Scope.enter();
      final Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv");

      final CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._calc_cumhaz = true;
      parms._train           = fr._key;
      parms._start_column    = null;
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant", "start"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;
      assertEquals("Surv(stop, event) ~ age", parms.toFormula(fr));

      CoxPH builder = new CoxPH(parms);
      final CoxPHModel model = builder.trainModel().get();
      Scope.track_generic(model);

      assertEquals(model._output._coef[0],        0.0289468187293998,   1e-8);
      assertEquals(model._output._var_coef[0][0], 0.000210975113029285, 1e-8);
      assertEquals(model._output._null_loglik,    -314.148170059513,    1e-8);
      assertEquals(model._output._loglik,         -311.946958322919,    1e-8);
      assertEquals(model._output._score_test,     3.97716015008595,     1e-8);
      assertTrue(model._output._iter >= 1);
      assertEquals(model._output._x_mean_num[0][0],  -2.48402655078554,    1e-8);
      assertEquals(model._output._n,              172);
      assertEquals(model._output._total_event,    75);
      assertEquals(model._output._wald_test,      3.97164529276219,     1e-8);
      assertEquals(model._output._var_cumhaz_2_matrix.rows(), 110);
      assertEquals(0.5670890188434048, ((ModelMetricsRegressionCoxPH)model._output._training_metrics).concordance(), 0.00001d);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCoxPHBreslow1VarNoStart() {
    try {
      Scope.enter();
      final Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv");

      final CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._calc_cumhaz = true;
      parms._train           = fr._key;
      parms._start_column    = null;
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant", "start"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.breslow;
      assertEquals("Surv(stop, event) ~ age", parms.toFormula(fr));

      final CoxPH builder = new CoxPH(parms);
      final CoxPHModel model = builder.trainModel().get();
      Scope.track_generic(model);

      assertEquals(model._output._coef[0],        0.0289484855901731,   1e-8);
      assertEquals(model._output._var_coef[0][0], 0.000211028794751156, 1e-8);
      assertEquals(model._output._null_loglik,    -314.296493366900,    1e-8);
      assertEquals(model._output._loglik,         -312.095342077591,    1e-8);
      assertEquals(model._output._score_test,     3.97665282498882,     1e-8);
      assertTrue(model._output._iter >= 1);
      assertEquals(model._output._x_mean_num[0][0],  -2.48402655078554,    1e-8);
      assertEquals(model._output._n,              172);
      assertEquals(model._output._total_event,    75);
      assertEquals(model._output._wald_test,      3.97109228128153,     1e-8);
      assertEquals(model._output._var_cumhaz_2_matrix.rows(), 110);
      assertEquals(0.5670890188434048, ((ModelMetricsRegressionCoxPH)model._output._training_metrics).concordance(), 0.00001d);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void showMojoIsAvailableWithCategoricalInteractions() {
    try {
      Scope.enter();
      final Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv")
              .toCategoricalCol("transplant")
              .toCategoricalCol("surgery");

      CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._calc_cumhaz = true;
      parms._train           = fr._key;
      parms._start_column    = "start";
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;

      CoxPHModel.CoxPHParameters parmsNum = (CoxPHModel.CoxPHParameters) parms.clone();
      CoxPHModel.CoxPHParameters parmsCat = (CoxPHModel.CoxPHParameters) parms.clone();

      // numerical interaction
      parmsNum._interaction_pairs = new StringPair[]{new StringPair("age", "year")};
      assertTrue(Scope.track_generic(new CoxPH(parmsNum).trainModel().get()).haveMojo());

      // categorical interaction (num-cat specifically)
      parmsCat._interaction_pairs = new StringPair[]{new StringPair("age", "surgery")};
      assertTrue(Scope.track_generic(new CoxPH(parmsCat).trainModel().get()).haveMojo());
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void testCoxPHEfron1Interaction() throws Exception {
    try {
      Scope.enter();
      final Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv");

      // Decompose a "age" column into two components: "age1" and "age2"
      final Frame ext = new MRTask() {
        @Override
        public void map(Chunk c, NewChunk nc0, NewChunk nc1) {
          for (int i = 0; i < c._len; i++) {
            double v = c.atd(i);
            if (i % 2 == 0) {
              nc0.addNum(v); nc1.addNum(1);
            } else {
              nc0.addNum(1); nc1.addNum(v);
            }
          }
        }
      }.doAll(new byte[]{Vec.T_NUM, Vec.T_NUM}, fr.vec("age"))
              .outputFrame(Key.make(), new String[]{"age1", "age2"}, null);
      Scope.track(ext);
      fr.add(ext);
      DKV.put(fr);

      CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._calc_cumhaz = true;
      parms._train           = fr._key;
      parms._start_column    = "start";
      parms._stop_column     = "stop";
      parms._response_column = "event";
      // We create interaction pair from the "age" components
      parms._interaction_pairs = new StringPair[]{new StringPair("age1", "age2")};
      parms._interactions_only = new String[]{"age1", "age2"};
      // Exclude the original "age" column
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant", "age"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;
      assertEquals("Surv(start, stop, event) ~ age1:age2", parms.toFormula(fr));

      CoxPH builder = new CoxPH(parms);
      CoxPHModel model = Scope.track_generic(builder.trainModel().get());

      // Expect the same result as we used "age"
      assertEquals(model._output._coef[0],        0.0307077486571334,   1e-8);
      assertEquals(model._output._var_coef[0][0], 0.000203471477951459, 1e-8);
      assertEquals(model._output._null_loglik,    -298.121355672984,    1e-8);
      assertEquals(model._output._loglik,         -295.536762216228,    1e-8);
      assertEquals(model._output._score_test,     4.64097294749287,     1e-8);
      assertTrue(model._output._iter >= 1);
      assertEquals(model._output._x_mean_num[0][0],  -2.48402655078554,    1e-8);
      assertEquals(model._output._n,              172);
      assertEquals(model._output._total_event,    75);
      assertEquals(model._output._wald_test,      4.6343882547245,      1e-8);

      Frame scored = model.score(fr);
      Scope.track(scored);

      assertTrue(model.haveMojo());
      MojoModel mojo = model.toMojo();

      EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
              .setModel(mojo);
      EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(config);

      double age1 = 7;
      double age2 = 6;
      double expectedPrediction = model._output._coef[0] * (age1 * age2 - model._output._x_mean_num[0][0]);
      // Sanity Test on a single row with interaction value provided externally
      {
        RowData rowData = new RowData();
        rowData.put("age1_age2", age1 * age2);
        CoxPHModelPrediction prediction = wrapper.predictCoxPH(rowData);
        assertEquals(prediction.value, expectedPrediction, 1e-8);
      }
      // Sanity Test on a single row with interaction calculated in MOJO itself
      {
        RowData rowData = new RowData();
        rowData.put("age1", age1);
        rowData.put("age2", age2);
        CoxPHModelPrediction prediction = wrapper.predictCoxPH(rowData);
        assertEquals(prediction.value, expectedPrediction, 1e-8);
      }

      // Compare whole frame predictions
      for (int i = 0; i < fr.numRows(); i++) {
        age1 = fr.vec("age1").at(i);
        age2 = fr.vec("age2").at(i);
        expectedPrediction = model._output._coef[0] * (age1 * age2 - model._output._x_mean_num[0][0]);

        // interactions pre-calculated
        {
          RowData rowExternal = new RowData();
          rowExternal.put("age1_age2", age1 * age2);
          CoxPHModelPrediction pExternal = wrapper.predictCoxPH(rowExternal);
          assertEquals(
                  "Predictions for row #" + i + " should match (external interactions)",
                  pExternal.value,
                  expectedPrediction,
                  1e-8);
        }

        // interactions calculated internally
        {
          RowData rowInternal = new RowData();
          rowInternal.put("age1_age2", age1 * age2);
          CoxPHModelPrediction pInternal = wrapper.predictCoxPH(rowInternal);
          assertEquals(
                  "Predictions for row #" + i + " should match (internal interactions)",
                  pInternal.value,
                  model._output._coef[0] * (age1 * age2 - model._output._x_mean_num[0][0]),
                  1e-8);
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCoxPHSingleNodeMode() {
    Key<Frame> rebalancedKey = Key.make();
    try {
      Scope.enter();
      Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv");
      fr = Scope.track(rebalanceToAllNodes(fr, rebalancedKey));

      CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._auto_rebalance  = false; // make sure we keep the original frame layout
      parms._calc_cumhaz     = true;
      parms._train           = fr._key;
      parms._start_column    = "start";
      parms._stop_column     = "stop";
      parms._response_column = "event";
      parms._ignored_columns = new String[]{"id", "year", "surgery", "transplant"};
      parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;
      
      // Concordance computation can't support single node so it's disabled for this test
      System.setProperty("sys.ai.h2o.debug.skipScoring", Boolean.TRUE.toString());
      System.setProperty("sys.ai.h2o.debug.checkRunLocal", Boolean.TRUE.toString());

      assertEquals("Surv(start, stop, event) ~ age", parms.toFormula(fr));

      parms._single_node_mode = true;
      CoxPH builder = new CoxPH(parms);
      CoxPHModel model = builder
              .trainModel()
              .get();
      Scope.track_generic(model);

      assertEquals(model._output._coef[0],        0.0307077486571334,   1e-8);
      assertEquals(model._output._var_coef[0][0], 0.000203471477951459, 1e-8);
      assertEquals(model._output._null_loglik,    -298.121355672984,    1e-8);
      assertEquals(model._output._loglik,         -295.536762216228,    1e-8);
      assertEquals(model._output._score_test,     4.64097294749287,     1e-8);
      assertTrue(model._output._iter >= 1);
      assertEquals(model._output._x_mean_num[0][0],  -2.48402655078554,    1e-8);
      assertEquals(model._output._n,              172);
      assertEquals(model._output._total_event,    75);
      assertEquals(model._output._wald_test,      4.6343882547245,      1e-8);
      assertEquals(model._output._var_cumhaz_2_matrix.rows(), 110);
    } finally {
      System.setProperty("sys.ai.h2o.debug.skipScoring", Boolean.FALSE.toString());
      System.setProperty("sys.ai.h2o.debug.checkRunLocal", Boolean.FALSE.toString());
      Scope.exit();
    }
  }

  private static Frame rebalanceToAllNodes(Frame fr, Key<Frame> rebalancedKey) {
    // this is essentially a complicated way of setting nChunks = H2O.getCloudSize()
    // because the tests are running with 5 nodes and with this number of nodes we use round-robin for the first few chunks
    // (however we can handle any number nodes)
    int nChunks = 0;
    boolean[] nodeHasChunk = new boolean[H2O.getCloudSize()];
    int nNodes = 0;
    while (nNodes != H2O.getCloudSize()) {
      Key k = fr.anyVec().chunkKey(nChunks++);
      int idx = k.home_node().index();
      if (nodeHasChunk[idx])
        continue;
      nodeHasChunk[idx] = true;
      nNodes++;
    }

    H2O.submitTask(new RebalanceDataSet(fr, rebalancedKey, nChunks)).join();
    fr.delete();
    fr = rebalancedKey.get();

    // make sure we do have a non-empty chunk on each node
    Set<H2ONode> nodes = new HashSet<>();
    for (int i = 0; i < fr.anyVec().nChunks(); i++) {
      if (fr.anyVec().chunkLen(i) > 0) {
        nodes.add(fr.anyVec().chunkKey(i).home_node());
      }
    }
    assertEquals(H2O.getCloudSize(), nodes.size());
    return fr;
  }

  @Test
  public void testJavaScoringNumeric() {
    try {
      Scope.enter();
      Frame fr = Scope.track(parseTestFile("smalldata/coxph_test/heart.csv"));
      testJavaScoring(fr);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testJavaScoringCategorical() {
    try {
      Scope.enter();
      Frame fr = Scope.track(parseTestFile("smalldata/coxph_test/heart.csv"))
              .toCategoricalCol("surgery")
              .toCategoricalCol("transplant");
      testJavaScoring(fr);
    } finally {
      Scope.exit();
    }
  }

  private void testJavaScoring(Frame fr) {
    CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
    parms._calc_cumhaz = true;
    parms._train = fr._key;
    parms._start_column = "start";
    parms._stop_column = "stop";
    parms._response_column = "event";
    parms._ignored_columns = new String[]{"id", "year"};
    parms._ties = CoxPHModel.CoxPHParameters.CoxPHTies.efron;

    CoxPHModel model = new CoxPH(parms).trainModel().get();
    assertNotNull(model);
    Scope.track_generic(model);
    Frame scored = model.score(fr);
    Scope.track(scored);
    assertTrue(model.testJavaScoring(fr, scored, 1e-5));
  }

  @Test
  public void testSpecialColumns()  {
    Assume.assumeTrue(H2O.CLOUD.isSingleNode()); // too slow on multinode and not needed
    try {
      Scope.enter();
      final Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv")
              .toCategoricalCol("surgery");

      final CoxPHModel.CoxPHParameters blueprintParms = new CoxPHModel.CoxPHParameters();
      blueprintParms._train           = fr._key;
      blueprintParms._start_column    = "start";
      blueprintParms._stop_column     = "stop";
      blueprintParms._response_column = "event";
      blueprintParms._stratify_by     = new String[]{"surgery"};
      blueprintParms._offset_column   = "year";
      blueprintParms._weights_column  = "transplant";
      blueprintParms._ignored_columns = new String[]{"id"};

      final String[] optionalFields = {"_start_column", "_stratify_by", "_offset_column", "_weights_column"};
      for (Set<String> fields : powerSet(optionalFields)) {
        CoxPHModel.CoxPHParameters parms = (CoxPHModel.CoxPHParameters) blueprintParms.clone();
        fields.forEach(name -> PojoUtils.setField(parms, name, null, PojoUtils.FieldNaming.CONSISTENT));

        final CoxPH builder = new CoxPH(parms);
        final CoxPHModel model = builder.trainModel().get();
        Scope.track_generic(model);
        ModelDescriptor md = model.modelDescriptor();
        
        if (parms._weights_column != null) {
          assertEquals("transplant", model._output.weightsName());
          assertEquals("transplant", md.weightsColumn());
        }
        if (parms._offset_column != null) {
          assertEquals("year", md.offsetColumn());
          assertEquals("year", model._output.offsetName());
        }

        Frame test = new Frame(fr);
        if (parms._weights_column != null) { // we want to test all rows
          test.remove(parms._weights_column);
        }
        Frame scored = model.score(test);
        Scope.track(scored);

        assertTrue(model.testJavaScoring(fr, scored, 1e-8));
      }
    } finally {
      Scope.exit();
    }
  }

  Set<Set<String>> powerSet(String[] vals) {
    Set<Set<String>> ps = new HashSet<>();
    ps.add(Collections.emptySet());
    while (true) {
      Set<Set<String>> newSets = new HashSet<>();
      for (Set<String> set : ps) {
        for (String val : vals) {
          if (set.contains(val))
            continue;
          Set<String> extended = new HashSet<>(set);
          extended.add(val);
          newSets.add(extended);
        }
      }
      if (!ps.addAll(newSets))
        break;
    }
    return ps;
  }

  @Test
  public void testMojoParsesInteractionPairs() throws Exception {
    try {
      Scope.enter();
      final Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv");

      final CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._train             = fr._key;
      parms._start_column      = "start";
      parms._stop_column       = "stop";
      parms._response_column   = "event";
      parms._interaction_pairs = new StringPair[]{new StringPair("surgery", "age")};
      parms._ignored_columns   = new String[]{"id"};
      
      final CoxPH builder = new CoxPH(parms);
      final CoxPHModel model = builder.trainModel().get();
      Scope.track_generic(model);
      
      MojoModel mojoModel = model.toMojo(true);
      ModelAttributes modelAttributes = mojoModel._modelAttributes;
      assertNotNull(modelAttributes);

      List<Object> pairs = Stream.of(modelAttributes.getModelParameters())
              .filter(p -> "interaction_pairs".equals(p.name))
              .map(ModelParameter::getActualValue)
              .collect(Collectors.toList());
      assertEquals(1, pairs.size());
      assertEquals(1, ((Object[]) pairs.get(0)).length);
      assertEquals(
              new hex.genmodel.attributes.parameters.StringPair("surgery", "age"),
              ((Object[]) pairs.get(0))[0]
      );
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCategoricalColumnsDontBreakStratifiedMOJOs() {
    try {
      Scope.enter();
      final Frame fr = parseAndTrackTestFile("smalldata/coxph_test/heart.csv")
              .toCategoricalCol("surgery")
              .toCategoricalCol("start")
              .toCategoricalCol("transplant");

      final CoxPHModel.CoxPHParameters parms = new CoxPHModel.CoxPHParameters();
      parms._train             = fr._key;
      parms._stop_column       = "stop";
      parms._response_column   = "event";
      parms._stratify_by       = new String[]{"surgery"};
      parms._ignored_columns   = new String[]{"id"};
      // Note: there is an issue on Java 11+ that makes efron method to fail
      // because this test is just supposed to test consistency we use breslow
      // the original issue needs to be fixed in https://github.com/h2oai/h2o-3/issues/6577
      parms._ties              = CoxPHModel.CoxPHParameters.CoxPHTies.breslow;

      final CoxPH builder = new CoxPH(parms);
      final CoxPHModel model = builder.trainModel().get();
      Scope.track_generic(model);

      assertEquals(
                      fr.vec("transplant").domain().length - 1 +
                      fr.vec("start").domain().length - 1 +
                      1 /*age */ + 
                      1 /*year*/,
              model._output._coef_names.length);
      assertTrue(Arrays.stream(model._output._coef)
              .filter(x -> x == 0.0d)
              .count() < 10); // only few can be 0

      Frame scored = model.score(fr);
      Scope.track(scored);

      assertTrue(model.testJavaScoring(fr, scored, 1e-6));
    } finally {
      Scope.exit();
    }
  }

}
