package ml.dmlc.xgboost4j.java;

import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeImpl;
import biz.k11i.xgboost.tree.RegTreeNode;
import biz.k11i.xgboost.util.FVec;
import biz.k11i.xgboost.util.ModelReader;
import water.util.UnsafeUtils;

import java.io.IOException;

/**
 * Regression tree.
 */
public class XGBoostRegTree implements RegTree {

  private static final int NODE_SIZE = 20;
  private static final int STATS_SIZE = 16;

  private byte[] _nodes;

  /**
   * Loads model from stream.
   *
   * @param reader input stream
   * @throws IOException If an I/O error occurs
   */
  XGBoostRegTree(ModelReader reader) throws IOException {
    final int numNodes = readNumNodes(reader);
    _nodes = reader.readByteArray(numNodes * NODE_SIZE);
    reader.skip(numNodes * STATS_SIZE);
  }

  /**
   * Retrieves nodes from root to leaf and returns leaf index.
   *
   * @param feat    feature vector
   * @param root_id starting root index
   * @return leaf index
   */
  @Override
  public int getLeafIndex(FVec feat, int root_id) {
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
    throw new UnsupportedOperationException();
  }


  private static int readNumNodes(ModelReader reader) throws IOException {
    int numRoots = reader.readInt();
    assert numRoots == 1;
    int numNodes = reader.readInt();
    reader.skip(4 * 4 + 31 * 4); // skip {int num_deleted, int max_depth, int num_feature, size_leaf_vector, 31 * reserved int}
    return numNodes;
  }

}
