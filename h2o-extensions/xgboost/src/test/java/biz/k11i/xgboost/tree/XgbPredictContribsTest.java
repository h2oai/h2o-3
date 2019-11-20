package biz.k11i.xgboost.tree;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.util.FVec;
import hex.genmodel.algos.tree.TreeSHAP;
import hex.util.NaiveTreeSHAP;
import ml.dmlc.xgboost4j.java.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import water.TestBase;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.ReflectionUtils;

import java.io.*;
import java.util.*;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static water.util.FileUtils.getFile;

// this test demonstrates that XGBoost Predictor can be used to calculate feature contributions (Tree SHAP values)
// naive (=slow) algorithm implemented and compared to implementation in XGBoost Predictor
public class XgbPredictContribsTest extends TestBase {

  private List<Map<Integer, Float>> trainData;
  private DMatrix trainMat;
  private DMatrix testMat;
  
  private static List<Map<Integer, Float>> parseData(File f) throws IOException {
    List<Map<Integer, Float>> data = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
      String line;
      while ((line = br.readLine()) != null) {
        String[] cols = line.split(" ");
        Map<Integer, Float> values = new HashMap<>();
        for (int i = 1; i < cols.length; i++) {
          String[] coords = cols[i].split(":", 2);
          values.put(Integer.parseInt(coords[0]), Float.parseFloat(coords[1]));
        }
        data.add(values);
      }
    }
    return data;
  }

  @Before
  public void loadData() throws XGBoostError, IOException {
    Map<String, String> rabitEnv = new HashMap<>();
    rabitEnv.put("DMLC_TASK_ID", "0");
    Rabit.init(rabitEnv);

    trainData = parseData(getFile("smalldata/xgboost/demo/data/agaricus.txt.train"));
    trainMat = new DMatrix(getFile("smalldata/xgboost/demo/data/agaricus.txt.train").getAbsolutePath());
    testMat = new DMatrix(getFile("smalldata/xgboost/demo/data/agaricus.txt.test").getAbsolutePath());
  }

  @After
  public void shutdown() throws XGBoostError {
    Rabit.shutdown();
  }
  
  @Test
  public void testPredictContrib() throws XGBoostError, IOException {
    HashMap<String, Object> params = new HashMap<>();
    params.put("eta", 0.1);
    params.put("max_depth", 5);
    params.put("silent", 1);
    params.put("objective", "binary:logistic");
    HashMap<String, DMatrix> watches = new HashMap<>();
    watches.put("train", trainMat);
    watches.put("test",  testMat);

    // 1. Train an XGBoost model & parse using Predictor
    final Booster booster = XGBoost.train(trainMat, params, 10, watches, null, null);
    final Predictor predictor = new Predictor(new ByteArrayInputStream(booster.toByteArray()));
    final double baseMargin = baseMargin(predictor);
    
    // 2. Sanity check - make sure booster & predictor agree on predictions
    float[][] preds = booster.predict(trainMat, true);
    float[][] ctrbs = booster.predictContrib(trainMat, 0); // these are approximate contributions no TreeSHAP values (included for completeness)
    for (int i = 0; i < preds.length; ++i) {
      FVec fvec = new MapBackedFVec(trainData.get(i));
      float[] pp = predictor.predict(fvec, true);
      float[] ps = preds[i];
      float[] cs = ctrbs[i];
      if (i < 10) {
        Log.info(ps[0] + " = Sum" + Arrays.toString(cs).replaceAll("0.0, ", ""));
      }
      assertEquals(ps[0], ArrayUtils.sum(cs), 1e-6);
      assertEquals(ps[0], pp[0], 1e-6);
    }

    // 3. Calculate contributions using naive (and extremely slow) approach and compare with Predictor's result
    GBTree gbTree = (GBTree) predictor.getBooster();
    RegTree[] trees = gbTree.getGroupedTrees()[0];
    for (int i = 0; i < 100; i++) {
      double[] contribsNaive = new double[ctrbs[0].length]; // contributions calculated naive approach (exponential complexity)
      float[] contribsPredictor = new float[ctrbs[0].length]; // contributions calculated by Predictor
      FVec row = new MapBackedFVec(trainData.get(i));
      float[] predicted = predictor.predict(row, true);

      double predExpVal = 0;
      for (int t = 0; t < trees.length; t++) {
        // A) Calculate contributions using Predictor 
        final RegTreeImpl tree = (RegTreeImpl) trees[t];
        final TreeSHAP<FVec, RegTreeImpl.Node, RegTreeImpl.RTreeNodeStat> treeSHAP = new TreeSHAP<>(
                tree.getNodes(), tree.getStats(), 0);
        contribsPredictor = treeSHAP.calculateContributions(row, contribsPredictor);
        // B) Calculate contributions the hard way
        final NaiveTreeSHAP<FVec, RegTreeImpl.Node, RegTreeImpl.RTreeNodeStat> naiveTreeSHAP = new NaiveTreeSHAP<>(
                tree.getNodes(), tree.getStats(), 0, baseMargin);
        predExpVal += naiveTreeSHAP.calculateContributions(row, contribsNaive);  
      }
      // sanity check - contributions should sum-up to the prediction
      final double predNaive = ArrayUtils.sum(contribsNaive);
      assertEquals(predicted[0], predNaive, 1e-6);
      assertEquals(predicted[0], predExpVal, 1e-6);
      // contributions should match!
      assertArrayEquals(contribsNaive, ArrayUtils.toDouble(contribsPredictor), 1e-6);
    }
  }

  private static float baseMargin(Predictor predictor) {
    Object mparam = ReflectionUtils.getFieldValue(predictor, "mparam");
    return ReflectionUtils.getFieldValue(mparam, "base_score");
  }
  
  private static class MapBackedFVec implements FVec {
    private final Map<Integer, Float> _data;
    private MapBackedFVec(Map<Integer, Float> data) {
      _data = data;
    }
    @Override
    public float fvalue(int index) {
      Float val = _data.get(index);
      if (val == null) {
        return Float.NaN;
      }
      return val;
    }
  }
  
}
