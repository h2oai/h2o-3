// Code in this file started as 1-1 conversion of Native XGBoost implementation in C++ to Java
// please see:
//    https://github.com/dmlc/xgboost/blob/master/src/tree/tree_model.cc
// All credit for this implementation goes to XGBoost Contributors:
//    https://github.com/dmlc/xgboost/blob/master/CONTRIBUTORS.md
// Licensed under Apache License Version 2.0
package hex.genmodel.algos.tree;

import ai.h2o.algos.tree.INode;
import ai.h2o.algos.tree.INodeStat;

import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;

public class TreeSHAP<R, N extends INode<R>, S extends INodeStat> implements TreeSHAPPredictor<R> {

  private final int rootNodeId;
  private final N[] nodes;
  private final S[] stats;
  private final float expectedTreeValue;

  @SuppressWarnings("unchecked")
  public TreeSHAP(N[] nodes) {
    this(nodes, (S[]) nodes, 0);
  }

  public TreeSHAP(N[] nodes, S[] stats, int rootNodeId) {
    this.rootNodeId = rootNodeId;
    this.nodes = nodes;
    this.stats = stats;
    this.expectedTreeValue = treeMeanValue();
  }

  private static class PathElement implements Serializable {
    int feature_index;
    float zero_fraction;
    float one_fraction;
    float pweight;
    void reset() {
      feature_index = 0;
      zero_fraction = 0;
      one_fraction = 0;
      pweight = 0;
    }
  }

  // extend our decision path with a fraction of one and zero extensions
  private void extendPath(PathPointer unique_path, int unique_depth,
                          float zero_fraction, float one_fraction,
                          int feature_index) {
    unique_path.get(unique_depth).feature_index = feature_index;
    unique_path.get(unique_depth).zero_fraction = zero_fraction;
    unique_path.get(unique_depth).one_fraction = one_fraction;
    unique_path.get(unique_depth).pweight = (unique_depth == 0 ? 1.0f : 0.0f);
    for (int i = unique_depth - 1; i >= 0; i--) {
      unique_path.get(i+1).pweight += one_fraction * unique_path.get(i).pweight * (i + 1)
              / (float) (unique_depth + 1);
      unique_path.get(i).pweight = zero_fraction * unique_path.get(i).pweight * (unique_depth - i)
              / (float) (unique_depth + 1);
    }
  }

  // undo a previous extension of the decision path
  private void unwindPath(PathPointer unique_path, int unique_depth,
                          int path_index) {
    final float one_fraction = unique_path.get(path_index).one_fraction;
    final float zero_fraction = unique_path.get(path_index).zero_fraction;
    float next_one_portion = unique_path.get(unique_depth).pweight;

    for (int i = unique_depth - 1; i >= 0; --i) {
      if (one_fraction != 0) {
        final float tmp = unique_path.get(i).pweight;
        unique_path.get(i).pweight = next_one_portion * (unique_depth + 1)
                / ((i + 1) * one_fraction);
        next_one_portion = tmp - unique_path.get(i).pweight * zero_fraction * (unique_depth - i)
                / (float) (unique_depth + 1);
      } else if (zero_fraction != 0) {
        unique_path.get(i).pweight = (unique_path.get(i).pweight * (unique_depth + 1))
                / (zero_fraction * (unique_depth - i));
      } else {
        unique_path.get(i).pweight = 0;
      }
    }

    for (int i = path_index; i < unique_depth; ++i) {
      unique_path.get(i).feature_index = unique_path.get(i+1).feature_index;
      unique_path.get(i).zero_fraction = unique_path.get(i+1).zero_fraction;
      unique_path.get(i).one_fraction = unique_path.get(i+1).one_fraction;
    }
  }

  // determine what the total permutation getWeight would be if
  // we unwound a previous extension in the decision path
  private float unwoundPathSum(final PathPointer unique_path, int unique_depth,
                               int path_index) {
    final float one_fraction = unique_path.get(path_index).one_fraction;
    final float zero_fraction = unique_path.get(path_index).zero_fraction;
    float next_one_portion = unique_path.get(unique_depth).pweight;
    float total = 0;
    for (int i = unique_depth - 1; i >= 0; --i) {
      if (one_fraction != 0) {
        final float tmp = next_one_portion * (unique_depth + 1)
                / ((i + 1) * one_fraction);
        total += tmp;
        next_one_portion = unique_path.get(i).pweight - tmp * zero_fraction * ((unique_depth - i)
                / (float)(unique_depth + 1));
      } else if (zero_fraction != 0) {
        total += (unique_path.get(i).pweight / zero_fraction) / ((unique_depth - i)
                / (float)(unique_depth + 1));
      } else {
        if (unique_path.get(i).pweight != 0)
          throw new IllegalStateException("Unique path " + i + " must have zero getWeight");
      }
    }
    return total;
  }

  // recursive computation of SHAP values for a decision tree
  private void treeShap(R feat, float[] phi,
                        N node, S nodeStat, int unique_depth,
                        PathPointer parent_unique_path,
                        float parent_zero_fraction,
                        float parent_one_fraction, int parent_feature_index,
                        int condition, int condition_feature,
                        float condition_fraction) {

    // stop if we have no getWeight coming down to us
    if (condition_fraction == 0) return;

    // extend the unique path
    PathPointer unique_path = parent_unique_path.move(unique_depth);

    if (condition == 0 || condition_feature != parent_feature_index) {
      extendPath(unique_path, unique_depth, parent_zero_fraction,
              parent_one_fraction, parent_feature_index);
    }
    final int split_index = node.getSplitIndex();

    // leaf node
    if (node.isLeaf()) {
      for (int i = 1; i <= unique_depth; ++i) {
        final float w = unwoundPathSum(unique_path, unique_depth, i);
        final PathElement el = unique_path.get(i);
        phi[el.feature_index] += w * (el.one_fraction - el.zero_fraction)
                * node.getLeafValue() * condition_fraction;
      }

      // internal node
    } else {
      // find which branch is "hot" (meaning x would follow it)
      final int hot_index = node.next(feat);
      final int cold_index = hot_index == node.getLeftChildIndex() ? node.getRightChildIndex() : node.getLeftChildIndex();
      final float w = nodeStat.getWeight();
      // if w == 0 then weights in child nodes are 0 as well (are identical) -> that is why we split hot and cold evenly (0.5 fraction)
      final float zero_weight_fraction = 0.5f;
      final float hot_zero_fraction = w != 0 ? stats[hot_index].getWeight() / w : zero_weight_fraction;
      final float cold_zero_fraction = w != 0 ? stats[cold_index].getWeight() / w : zero_weight_fraction;
      float incoming_zero_fraction = 1;
      float incoming_one_fraction = 1;

      // see if we have already split on this feature,
      // if so we undo that split so we can redo it for this node
      int path_index = 0;
      for (; path_index <= unique_depth; ++path_index) {
        if (unique_path.get(path_index).feature_index == split_index)
          break;
      }
      if (path_index != unique_depth + 1) {
        incoming_zero_fraction = unique_path.get(path_index).zero_fraction;
        incoming_one_fraction = unique_path.get(path_index).one_fraction;
        unwindPath(unique_path, unique_depth, path_index);
        unique_depth -= 1;
      }

      // divide up the condition_fraction among the recursive calls
      float hot_condition_fraction = condition_fraction;
      float cold_condition_fraction = condition_fraction;
      if (condition > 0 && split_index == condition_feature) {
        cold_condition_fraction = 0;
        unique_depth -= 1;
      } else if (condition < 0 && split_index == condition_feature) {
        hot_condition_fraction *= hot_zero_fraction;
        cold_condition_fraction *= cold_zero_fraction;
        unique_depth -= 1;
      }

      treeShap(feat, phi, nodes[hot_index], stats[hot_index], unique_depth + 1, unique_path,
              hot_zero_fraction * incoming_zero_fraction, incoming_one_fraction,
              split_index, condition, condition_feature, hot_condition_fraction);

      treeShap(feat, phi, nodes[cold_index], stats[cold_index], unique_depth + 1, unique_path,
              cold_zero_fraction * incoming_zero_fraction, 0,
              split_index, condition, condition_feature, cold_condition_fraction);
    }
  }
  
  public static class PathPointer implements TreeSHAPPredictor.Workspace {
    PathElement[] path;
    int position;

    PathPointer(PathElement[] path) {
      this.path = path;
    }

    PathPointer(PathElement[] path, int position) {
      this.path = path;
      this.position = position;
    }

    PathElement get(int i) {
      return path[position + i];
    }

    PathPointer move(int len) {
      for (int i = 0; i < len; i++) {
        path[position + len + i].feature_index = path[position + i].feature_index;
        path[position + len + i].zero_fraction = path[position + i].zero_fraction;
        path[position + len + i].one_fraction = path[position + i].one_fraction;
        path[position + len + i].pweight = path[position + i].pweight;
      }
      return new PathPointer(path, position + len);
    }

    void reset() {
      path[0].reset();
    }

    @Override
    public int getSize() {
      return path.length;
    }
  }

  @Override
  public float[] calculateContributions(final R feat, float[] out_contribs) {
    return calculateContributions(feat, out_contribs, 0, -1, makeWorkspace());
  }

  @Override
  public float[] calculateContributions(final R feat,
                                        float[] out_contribs, int condition, int condition_feature,
                                        TreeSHAP.Workspace workspace) {

    // find the expected value of the tree's predictions
    if (condition == 0) {
      out_contribs[out_contribs.length - 1] += expectedTreeValue;
    }

    PathPointer uniquePathWorkspace = (PathPointer) workspace; 
    uniquePathWorkspace.reset();

    treeShap(feat, out_contribs, nodes[rootNodeId], stats[rootNodeId], 0, uniquePathWorkspace,
            1, 1, -1, condition, condition_feature, 1);
    return out_contribs;
  }

  @Override
  public double[] calculateInterventionalContributions(R feat, R background, double[] out_contribs, int[] catOffsets, boolean expand) {
    interventionalTreeShap(feat, background, out_contribs, nodes[rootNodeId], new BitSet(out_contribs.length), new BitSet(out_contribs.length), catOffsets, expand);
    return out_contribs;
  }
  
  private double w(int k, int d) {
    // assume d > k
    // (k! (d-k-1)! / d!) => 
    // lets denote a = min(k, d-k-1), b = max(k, d-k-1), then 
    // (a!b!)/d! => a!/((b+1)(b+2)...(d-1)(d))
    assert k >= 0;
    assert d >= k;
    int a = Math.min(k, d-k-1);
    int b = Math.max(k, d-k-1);
    double nom=1, denom=1;

    for (int i = 2; i <= a; i++) {
      nom *= i;
    }
    for (int i = b+1; i <= d; i++) {
      denom *= i;
    }
    
    return nom/denom;
  }

  int mapToOutputSpace(int featureIdx, double featureVal, int[] catOffsets, boolean expand) {
    if (null == catOffsets)
      return featureIdx;
    if (expand) {
      if (catOffsets[featureIdx+1] - catOffsets[featureIdx] == 1) {
        // Numerical variable
        return catOffsets[featureIdx];
      }
      // Categorical variable
      if (Double.isNaN(featureVal))
        return catOffsets[featureIdx+1]-1;
      return catOffsets[featureIdx] + (int)featureVal;
    } else {
      if (catOffsets[catOffsets.length - 1] < featureIdx)
        return featureIdx - catOffsets[catOffsets.length - 1] + catOffsets.length - 1;
      int outputIdx = Arrays.binarySearch(catOffsets, featureIdx);
      if (outputIdx < 0)
        return -outputIdx - 2;
      return outputIdx;
    }
  }

  /**
   * If catOffsets == null calculate contributions for one hot encoded feature with an assumption that cardinality can change
   * @param feat
   * @param background
   * @param out_contribs
   * @param node
   * @param sX
   * @param sZ
   * @param catOffsets
   */
  void interventionalTreeShap(R feat, R background, double[] out_contribs, N node, BitSet sX, BitSet sZ, int[] catOffsets, boolean expand) {
    // Notation here follows [1]. X denotes data point for which we calculate the contribution (feat) and Z denotes the data point from the background distribution.
    // [1] LABERGE, Gabriel and PEQUIGNOT, Yann, 2022. Understanding Interventional TreeSHAP: How and Why it Works. arXiv:2209.15123.

    if (node.isLeaf()) {    // is leaf
      final int sXCard = sX.cardinality();
      final int sZCard = sZ.cardinality();
      double wPos = sXCard == 0 ? 0 : w(sXCard - 1, sXCard + sZCard);
      double wNeg = w(sXCard, sXCard + sZCard);
      for (int i = sX.nextSetBit(0); i >= 0; i = sX.nextSetBit(i + 1)) {
        out_contribs[i] += wPos * node.getLeafValue();
      }
      for (int i = sZ.nextSetBit(0); i >= 0; i = sZ.nextSetBit(i + 1)) {
        out_contribs[i] -= wNeg * node.getLeafValue();
      }
      if (sX.cardinality() == 0) // Bias Term
        out_contribs[out_contribs.length-1] += node.getLeafValue();
    } else { // not a leaf node
      final int nextX = node.next(feat);
      final int nextZ = node.next(background);
      final int iN = mapToOutputSpace(node.getSplitIndex(), feat instanceof double[] ? ((double[])feat)[node.getSplitIndex()]: -1, catOffsets, expand);
      if (nextX == nextZ) {
        // this feature (iN) is present in both paths (for X and Z) => no change in contributions
        interventionalTreeShap(feat, background, out_contribs, nodes[nextX], sX, sZ, catOffsets, expand);
      } else if (sX.get(iN)) { // this feature (iN) was already seen in this path -> go the same way to keep the traversal disjoint
        interventionalTreeShap(feat, background, out_contribs, nodes[nextX], sX, sZ, catOffsets, expand);
      } else if (sZ.get(iN)) { // this feature (iN) was already seen in this path -> go the same way to keep the traversal disjoint
        interventionalTreeShap(feat, background, out_contribs, nodes[nextZ], sX, sZ, catOffsets, expand);
      } else { // this feature (iN) wasn't seen before go down both ways
        BitSet newSx = (BitSet) sX.clone();
        BitSet newSz = (BitSet) sZ.clone();
        newSx.set(iN);
        newSz.set(iN);
        interventionalTreeShap(feat, background, out_contribs, nodes[nextX], newSx, sZ, catOffsets, expand);
        interventionalTreeShap(feat, background, out_contribs, nodes[nextZ], sX, newSz, catOffsets, expand);
      }
    }
  }
  
  @Override
  public PathPointer makeWorkspace() {
    int wsSize = getWorkspaceSize();
    PathElement[] unique_path_data = new PathElement[wsSize];
    for (int i = 0; i < unique_path_data.length; i++) {
      unique_path_data[i] = new PathElement();
    }
    return new PathPointer(unique_path_data);
  }

  @Override
  public int getWorkspaceSize() {
    final int maxd = treeDepth() + 2;
    return (maxd * (maxd + 1)) / 2;
  }

  private int treeDepth() {
    return nodeDepth(nodes, 0);
  }

  private static <N extends INode> int nodeDepth(N[] nodes, int node) {
    final N n = nodes[node];
    if (n.isLeaf()) {
      return 1;
    } else {
      return 1 + Math.max(nodeDepth(nodes, n.getLeftChildIndex()), nodeDepth(nodes, n.getRightChildIndex()));
    }
  }

  private float treeMeanValue() {
    return nodeMeanValue(nodes, stats, 0);
  }

  private static <N extends INode, S extends INodeStat> float nodeMeanValue(N[] nodes, S[] stats, int node) {
    final N n = nodes[node];
    if (stats[node].getWeight() == 0) {
      return 0;
    } else if (n.isLeaf()) {
      return n.getLeafValue();
    } else {
      return (stats[n.getLeftChildIndex()].getWeight() * nodeMeanValue(nodes, stats, n.getLeftChildIndex()) +
              stats[n.getRightChildIndex()].getWeight() * nodeMeanValue(nodes, stats, n.getRightChildIndex())) / stats[node].getWeight();
    }
  }

}
