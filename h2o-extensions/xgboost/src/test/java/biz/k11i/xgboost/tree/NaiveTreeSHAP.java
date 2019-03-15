package biz.k11i.xgboost.tree;

import biz.k11i.xgboost.util.FVec;
import org.junit.Ignore;
import water.util.ReflectionUtils;

import java.util.*;

@Ignore
class NaiveTreeSHAP {

  static double calculateContributionsNaive(double baseMargin, RegTreeImpl tree, FVec row, double[] contribsNaive) {
    final Set<Integer> usedFeatures = usedFeatures(tree);
    final int M = usedFeatures.size();
    // last element is the bias term
    contribsNaive[contribsNaive.length - 1] += treeMeanValue(tree) /* tree bias */ + baseMargin;
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
          contribsNaive[feature] += contrib;
        }
      }
    }
    // expValue of a tree with all features marked as used should sum-up to the total prediction
    return expValue(tree, row, usedFeatures);
  }
  
  private static double expValue(RegTreeImpl t, FVec v, Set<Integer> s) {
    RegTreeImpl.RTreeNodeStat[] stats = ReflectionUtils.getFieldValue(t, "stats");
    RegTreeImpl.Node[] nodes = t.getNodes();

    return expValue(nodes, stats, 0, v, s, 1.0);
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
      features.add(n.getSplitIndex());
    }
    return features;
  }

  private static double expValue(RegTreeImpl.Node[] nodes, RegTreeImpl.RTreeNodeStat[] stats,
                                 int node, FVec v, Set<Integer> s, double w) {
    final RegTreeImpl.Node n = nodes[node];
    if (n.isLeaf()) {
      return w * n.getLeafValue();
    } else {
      if (s.contains(n.getSplitIndex())) {
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
    if (n.isLeaf()) {
      return n.getLeafValue();
    } else {
      return (stats[n.cleft_].sum_hess * nodeMeanValue(nodes, stats, n.cleft_) +
              stats[n.cright_].sum_hess * nodeMeanValue(nodes, stats, n.cright_)) / stats[node].sum_hess;
    }
  }

}
