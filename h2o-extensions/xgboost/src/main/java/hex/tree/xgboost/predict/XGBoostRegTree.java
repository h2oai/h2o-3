package hex.tree.xgboost.predict;

import biz.k11i.xgboost.tree.*;
import biz.k11i.xgboost.util.FVec;
import biz.k11i.xgboost.util.ModelReader;
import water.util.UnsafeUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Regression tree.
 */
public class XGBoostRegTree implements RegTree {

  static final int NODE_SIZE = 20;
  static final int STATS_SIZE = 16;

  private final byte[] _nodes;

  /**
   * Loads model from stream.
   *
   * @param reader input stream
   * @throws IOException If an I/O error occurs
   */
  XGBoostRegTree(ModelReader reader) throws IOException {
    final int numNodes = readNumNodes(reader);
    _nodes = reader.readByteArray(numNodes * NODE_SIZE);
    reader.skip((long) numNodes * STATS_SIZE);
  }

  @Override
  public int getLeafIndex(FVec feat) {
    throw new UnsupportedOperationException("Leaf node id assignment is currently not supported");
  }

  @Override
  public void getLeafPath(FVec fVec, StringBuilder stringBuilder) {
    throw new UnsupportedOperationException("Leaf node id assignment is currently not supported");
  }

  /**
   * Retrieves nodes from root to leaf and returns leaf value.
   *
   * @param feat    feature vector
   * @param root_id starting root index
   * @return leaf value
   */
  @Override
  public final float getLeafValue(FVec feat, int root_id) {
    int pid = root_id;

    int pos = pid * NODE_SIZE + 4;
    int cleft_ = UnsafeUtils.get4(_nodes, pos);

    while (cleft_ != -1) {
      final int sindex_ = UnsafeUtils.get4(_nodes, pos + 8);
      final float fvalue = feat.fvalue((int) (sindex_ & ((1L << 31) - 1L)));
      if (Float.isNaN(fvalue)) {
        pid = (sindex_ >>> 31) != 0 ? cleft_ : UnsafeUtils.get4(_nodes, pos + 4);
      } else {
        final float value_ = UnsafeUtils.get4f(_nodes, pos + 12);
        pid = (fvalue < value_) ? cleft_ : UnsafeUtils.get4(_nodes, pos + 4);
      }
      pos = pid * NODE_SIZE + 4;
      cleft_ = UnsafeUtils.get4(_nodes, pos);
    }

    return UnsafeUtils.get4f(_nodes, pos + 12);
  }

  @Override
  public RegTreeNode[] getNodes() {
    try (InputStream nodesStream = new ByteArrayInputStream(_nodes)) {
      ModelReader reader = new ModelReader(nodesStream);
      RegTreeNode[] nodes = new RegTreeNode[_nodes.length / NODE_SIZE];
      for (int i = 0; i < nodes.length; i++) {
        nodes[i] = NodeHelper.read(reader);
      }
      return nodes;
    } catch (IOException e) {
      throw new RuntimeException("Cannot extract nodes from tree", e);
    }
  }

  @Override
  public RegTreeNodeStat[] getStats() {
    throw new UnsupportedOperationException("Scoring-optimized trees don't contain node stats");
  }

  static int readNumNodes(ModelReader reader) throws IOException {
    int numRoots = reader.readInt();
    assert numRoots == 1;
    int numNodes = reader.readInt();
    reader.skip(4 * 4 + 31 * 4); // skip {int num_deleted, int max_depth, int num_feature, size_leaf_vector, 31 * reserved int}
    return numNodes;
  }

}
