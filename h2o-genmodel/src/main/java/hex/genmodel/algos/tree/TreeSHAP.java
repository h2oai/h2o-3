// Code in this file started as 1-1 conversion of Native XGBoost implementation in C++ to Java
// please see:
//    https://github.com/dmlc/xgboost/blob/master/src/tree/tree_model.cc
// All credit for this implementation goes to XGBoost Contributors:
//    https://github.com/dmlc/xgboost/blob/master/CONTRIBUTORS.md
// Licensed under Apache License Version 2.0
package hex.genmodel.algos.tree;

import hex.genmodel.algos.tree.INode;
import hex.genmodel.algos.tree.INodeStat;

import java.io.Serializable;

public class TreeSHAP<R, N extends INode<R>, S extends INodeStat> implements TreeSHAPPredictor<R> {

  private final int rootNodeId;
  private final N[] nodes;
  private final S[] stats;
  private final float expectedTreeValue;

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
      } else {
        unique_path.get(i).pweight = (unique_path.get(i).pweight * (unique_depth + 1))
                / (zero_fraction * (unique_depth - i));
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
      final float hot_zero_fraction = stats[hot_index].getWeight() / w;
      final float cold_zero_fraction = stats[cold_index].getWeight() / w;
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

  public static class PathPointer {
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
  }

  @Override
  public float[] calculateContributions(final R feat, float[] out_contribs) {
    return calculateContributions(feat, out_contribs, 0, -1, makeWorkspace());
  }

  @Override
  public float[] calculateContributions(final R feat,
                                        float[] out_contribs, int condition, int condition_feature,
                                        Object workspace) {

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
    if (n.isLeaf()) {
      return n.getLeafValue();
    } else {
      return (stats[n.getLeftChildIndex()].getWeight() * nodeMeanValue(nodes, stats, n.getLeftChildIndex()) +
              stats[n.getRightChildIndex()].getWeight() * nodeMeanValue(nodes, stats, n.getRightChildIndex())) / stats[node].getWeight();
    }
  }

}
