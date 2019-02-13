package biz.k11i.xgboost.tree;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.util.FVec;
import ml.dmlc.xgboost4j.java.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.ReflectionUtils;

import java.io.*;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static water.util.FileUtils.locateFile;

// this test demonstrates that XGBoost Predictor can be used to calculate feature contributions (Tree SHAP values)
// naive (=slow) algorithm implemented
public class XgbPredictContribsTest {

  List<Map<Integer, Float>> trainData;
  DMatrix trainMat;
  DMatrix testMat;
  
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

    trainData = parseData(locateFile("smalldata/xgboost/demo/data/agaricus.txt.train"));
    trainMat = new DMatrix(locateFile("smalldata/xgboost/demo/data/agaricus.txt.train").getAbsolutePath());
    testMat = new DMatrix(locateFile("smalldata/xgboost/demo/data/agaricus.txt.test").getAbsolutePath());
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
    float[][] ctrbs = booster.predictContrib(trainMat, 0);
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

    // 3. Calculate contributions using naive (and extremely slow) approach
    GBTree gbTree = (GBTree) predictor.getBooster();
    RegTree[] trees = gbTree.getGroupedTrees()[0];
    for (int i = 0; i < 100; i++) {
      double[] contribs = new double[ctrbs[0].length];
      FVec row = new MapBackedFVec(trainData.get(i));
      float[] predicted = predictor.predict(row, true);
      double pred = 0;
      for (int t = 0; t < trees.length; t++) {
        final RegTreeImpl tree = (RegTreeImpl) trees[t];
        final Set<Integer> usedFeatures = usedFeatures(tree);
        final int M = usedFeatures.size();
        Log.info("Tree " + t + ": " + usedFeatures);
        // last element is the bias
        contribs[contribs.length - 1] += treeMeanValue(tree) /* tree bias */ + baseMargin;
        // pre-calculate expValue for each subset
        Map<Set<Integer>, Double> expVals = new HashMap<>();
        for (Set<Integer> subset : allSubsets(usedFeatures)) {
          expVals.put(subset, expValue(tree, row, subset));
        }
        // calculate contributions using pre-calculated expValues
        for (Integer feature : usedFeatures) {
          for (Set<Integer> subset : expVals.keySet()) {
            if (subset.contains(feature)) {
              Set<Integer> noFeature = new HashSet<>(subset);
              noFeature.remove(feature);
              double mult = fact(noFeature.size()) * (long) fact(M - subset.size()) / (double) fact(M);
              double contrib = mult * (expVals.get(subset) - expVals.get(noFeature));
              contribs[feature] += contrib;
            }
          }
        }
        // expValue of a tree with all features marked as used should sum-up to the total prediction
        pred += expValue(tree, row, usedFeatures);
      }
      // contributions should sum-up to the prediction
      double pc = ArrayUtils.sum(contribs);
      assertEquals(predicted[0], pc, 1e-6);
      assertEquals(predicted[0], pred, 1e-6);
    }
  }

  private static float baseMargin(Predictor predictor) {
    Object mparam = ReflectionUtils.getFieldValue(predictor, "mparam");
    return ReflectionUtils.getFieldValue(mparam, "base_score");
  }
  
  private static int fact(int v) {
    int f = 1;
    for (int i = 1; i <= v; i++) {
      f *= i;
    }
    return f;
  }
  
  private static List<Set<Integer>> allSubsets(Set<Integer> s) {
    List<Set<Integer>> result = new LinkedList<>();
    Integer[] ary = s.toArray(new Integer[0]);
    // Run a loop from 0 to 2^n
    for (int i = 0; i < (1<<ary.length); i++) {
      Set<Integer> subset = new HashSet<>(s.size());
      int m = 1;
      for (Integer item : ary) {
        if ((i & m) > 0) {
          subset.add(item);
        }
        m = m << 1;
      }

      result.add(subset);
    }
    return result;
  }
  
  private static Set<Integer> usedFeatures(RegTreeImpl t) {
    Set<Integer> features = new HashSet<>();
    for(RegTreeImpl.Node n : t.getNodes()) {
      features.add(n.split_index());
    }
    return features;
  }
  
  private static double expValue(RegTreeImpl t, FVec v, Set<Integer> s) {
    RegTreeImpl.RTreeNodeStat[] stats = ReflectionUtils.getFieldValue(t, "stats");
    RegTreeImpl.Node[] nodes = t.getNodes();
    
    return expValue(nodes, stats, 0, v, s, 1.0);
  } 

  private static double expValue(RegTreeImpl.Node[] nodes, RegTreeImpl.RTreeNodeStat[] stats,
                                 int node, FVec v, Set<Integer> s, double w) {
    final RegTreeImpl.Node n = nodes[node];
    if (n.is_leaf()) {
      return w * n.getLeafValue(); 
    } else {
      if (s.contains(n.split_index())) {
        return expValue(nodes, stats, n.next(v), v, s, w);
      } else {
        double wL = stats[n.cleft_].sum_hess;
        double wR = stats[n.cright_].sum_hess;
        return expValue(nodes, stats, n.cleft_, v, s, w * wL / stats[node].sum_hess) +
                expValue(nodes, stats, n.cright_, v, s, w * wR / stats[node].sum_hess);
      }
    }
  }

  private static double treeMeanValue(RegTreeImpl t) {
    RegTreeImpl.RTreeNodeStat[] stats = ReflectionUtils.getFieldValue(t, "stats");
    RegTreeImpl.Node[] nodes = t.getNodes();

    return nodeMeanValue(nodes, stats, 0);
  }
  
  private static double nodeMeanValue(RegTreeImpl.Node[] nodes, RegTreeImpl.RTreeNodeStat[] stats, int node) {
    final RegTreeImpl.Node n = nodes[node];
    if (n.is_leaf()) {
      return n.getLeafValue();
    } else {
      return (stats[n.cleft_].sum_hess * nodeMeanValue(nodes, stats, n.cleft_) +
              stats[n.cright_].sum_hess * nodeMeanValue(nodes, stats, n.cright_)) / stats[node].sum_hess;
    }
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
