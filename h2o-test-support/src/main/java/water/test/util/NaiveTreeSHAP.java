package water.test.util;

import hex.genmodel.algos.tree.INode;
import hex.genmodel.algos.tree.INodeStat;
import org.junit.Ignore;

import java.util.*;

@Ignore
public class NaiveTreeSHAP<R, N extends INode<R>, S extends INodeStat> {

  private final int rootNodeId;
  private final N[] nodes;
  private final S[] stats;

  public NaiveTreeSHAP(N[] nodes, S[] stats, int rootNodeId) {
    this.rootNodeId = rootNodeId;
    this.nodes = nodes;
    this.stats = stats;
  }

  public double calculateContributions(R row, double[] contribsNaive) {
    final Set<Integer> usedFeatures = usedFeatures();
    final int M = usedFeatures.size();
    // last element is the bias term
    contribsNaive[contribsNaive.length - 1] += treeMeanValue() /* tree bias */;
    // pre-calculate expValue for each subset
    Map<Set<Integer>, Double> expVals = new HashMap<>();
    for (Set<Integer> subset : allSubsets(usedFeatures)) {
      expVals.put(subset, expValue(row, subset));
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
    return expValue(row, usedFeatures);
  }
  
  private double expValue(R v, Set<Integer> s) {
    return expValue(rootNodeId, v, s, 1.0);
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

  private Set<Integer> usedFeatures() {
    Set<Integer> features = new HashSet<>();
    for(N n : nodes) {
      features.add(n.getSplitIndex());
    }
    return features;
  }

  private double expValue(int node, R v, Set<Integer> s, double w) {
    final INode<R> n = nodes[node];
    if (n.isLeaf()) {
      return w * n.getLeafValue();
    } else {
      if (s.contains(n.getSplitIndex())) {
        return expValue(n.next(v), v, s, w);
      } else {
        double wP = stats[node].getWeight();
        double wL = stats[n.getLeftChildIndex()].getWeight();
        double wR = stats[n.getRightChildIndex()].getWeight();
        return expValue(n.getLeftChildIndex(), v, s, w * wL / wP) +
                expValue(n.getRightChildIndex(), v, s, w * wR / wP);
      }
    }
  }

  private double treeMeanValue() {
    return nodeMeanValue(rootNodeId);
  }

  private double nodeMeanValue(int node) {
    final INode n = nodes[node];
    if (n.isLeaf()) {
      return n.getLeafValue();
    } else {
      return (stats[n.getLeftChildIndex()].getWeight() * nodeMeanValue(n.getLeftChildIndex()) +
              stats[n.getRightChildIndex()].getWeight() * nodeMeanValue(n.getRightChildIndex())) / stats[node].getWeight();
    }
  }

}
