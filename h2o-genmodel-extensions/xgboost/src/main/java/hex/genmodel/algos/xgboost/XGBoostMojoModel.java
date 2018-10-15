package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.tree.SharedTreeGraphConverter;

import java.io.Closeable;
import java.util.Arrays;


/**
 * "Gradient Boosting Machine" MojoModel
 */
public abstract class XGBoostMojoModel extends MojoModel implements SharedTreeGraphConverter,Closeable {

  private static final String SPACE = " ";

  public enum ObjectiveType {
    BINARY_LOGISTIC("binary:logistic"),
    REG_GAMMA("reg:gamma"),
    REG_TWEEDIE("reg:tweedie"),
    COUNT_POISSON("count:poisson"),
    REG_LINEAR("reg:linear"),
    MULTI_SOFTPROB("multi:softprob");

    private String _id;

    ObjectiveType(String id) {
      _id = id;
    }

    public String getId() {
      return _id;
    }

    public static ObjectiveType fromXGBoost(String type) {
      for (ObjectiveType t : ObjectiveType.values())
        if (t.getId().equals(type))
          return t;
      return null;
    }
  }

  public int _nums;
  public int _cats;
  public int[] _catOffsets;
  public boolean _useAllFactorLevels;
  public boolean _sparse;
    public String _featureMap;

  public XGBoostMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  // finalize MOJO initialization after all the fields are read
  public void postReadInit() {}

  @Override
  public final double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  // for float output
  public static double[] toPreds(double in[], float[] out, double[] preds,
                          int nclasses, double[] priorClassDistrib, double defaultThreshold) {
    if (nclasses > 2) {
      for (int i = 0; i < out.length; ++i)
        preds[1 + i] = out[i];
      preds[0] = GenModel.getPrediction(preds, priorClassDistrib, in, defaultThreshold);
    } else if (nclasses==2){
      preds[1] = 1f - out[0];
      preds[2] = out[0];
      preds[0] = GenModel.getPrediction(preds, priorClassDistrib, in, defaultThreshold);
    } else {
      preds[0] = out[0];
    }
    return preds;
  }

  protected void constructSubgraph(final RegTreeNode[] xgBoostNodes, final SharedTreeNode sharedTreeNode,
                                   final int nodeIndex, final SharedTreeSubgraph sharedTreeSubgraph,
                                   final boolean[] oneHotEncodedMap, final boolean inclusiveNA, final String[] features) {
    final RegTreeNode xgBoostNode = xgBoostNodes[nodeIndex];
    // Not testing for NaNs, as SharedTreeNode uses NaNs as default values.
    //No domain set, as the structure mimics XGBoost's tree, which is numeric-only
    if (oneHotEncodedMap[xgBoostNode.split_index()]) {
      //Shared tree model uses < to the left and >= to the right. Transforiming one-hot encoded categoricals
      // from 0 to 1 makes it fit the current split description logic
      sharedTreeNode.setSplitValue(1.0F);
    } else {
      sharedTreeNode.setSplitValue(xgBoostNode.getSplitCondition());
    }
    sharedTreeNode.setPredValue(xgBoostNode.getLeafValue());
      sharedTreeNode.setCol(xgBoostNode.split_index(), features[xgBoostNode.split_index()].split(SPACE)[1]);
    sharedTreeNode.setInclusiveNa(inclusiveNA);
    sharedTreeNode.setNodeNumber(nodeIndex);

    if (xgBoostNode.getLeftChildIndex() != -1) {
      constructSubgraph(xgBoostNodes, sharedTreeSubgraph.makeLeftChildNode(sharedTreeNode),
              xgBoostNode.getLeftChildIndex(), sharedTreeSubgraph, oneHotEncodedMap, xgBoostNode.default_left(),
              features);
    }

    if (xgBoostNode.getRightChildIndex() != -1) {
      constructSubgraph(xgBoostNodes, sharedTreeSubgraph.makeRightChildNode(sharedTreeNode),
              xgBoostNode.getRightChildIndex(), sharedTreeSubgraph, oneHotEncodedMap, !xgBoostNode.default_left(),
              features);
    }
  }

    private String[] constructFeatureMap() {
        final String[] featureMapTokens = _featureMap.split("\n");
        // There might be an empty line after "\n", this part avoids parsing empty token(s) at the end
        int nonEmptyTokenRange = featureMapTokens.length;
        for (int i = 0; i < featureMapTokens.length; i++) {
            if (featureMapTokens[i].trim().isEmpty()) {
                nonEmptyTokenRange = i + 1;
                break;
            }
        }

        return Arrays.copyOfRange(featureMapTokens, 0, nonEmptyTokenRange);
    }


  protected boolean[] markOneHotEncodedCategoricals(final String[] featureMap) {
    final int numColumns = featureMap.length;

    int numCatCols = -1;
    for (int i = 0; i < featureMap.length;i++) {
      final String[] s = featureMap[i].split(SPACE);
      assert s.length > 3; // There should be at least three tokens, the third token is feature type (int, categorical etc).
      if(!s[2].equals("i")){
        numCatCols = i;
        break;
      }
    }

    if (numCatCols == -1) {
      numCatCols = featureMap.length;
    }


    boolean[] categorical = new boolean[numColumns];
    for (int i = 0; i < numColumns; ++i) {
      if (i < numCatCols) {
        categorical[i] = true;
      }
    }

    return categorical;
  }

  protected SharedTreeGraph _computeGraph(final GradBooster booster, final int treeNumber) {

    if (!(booster instanceof GBTree)) {
      throw new IllegalArgumentException(String.format("Given XGBoost model is not backed by a tree-based booster. Booster class is %d",
              booster.getClass().getCanonicalName()));
    }

    final RegTree[][] treesAndClasses = ((GBTree) booster).getGroupedTrees();

    SharedTreeGraph sharedTreeGraph = new SharedTreeGraph();

    for (int i = 0; i < treesAndClasses.length; i++) {
      final RegTree[] treesInGroup = treesAndClasses[i];

    if (treeNumber >= treesInGroup.length || treeNumber < 0) {
      throw new IllegalArgumentException(String.format("There is no such tree number for given class. Total number of trees is %d.", treesInGroup.length));
    }


    final RegTreeNode[] treeNodes = treesInGroup[treeNumber].getNodes();
    assert treeNodes.length >= 1;


    final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeGraph.makeSubgraph(String.format("Class %d", i));
        final String[] features = constructFeatureMap();
        final boolean[] oneHotEncodedMap = markOneHotEncodedCategoricals(features); // XGBoost's usage of one-hot encoding assumed
        constructSubgraph(treeNodes, sharedTreeSubgraph.makeRootNode(), 0, sharedTreeSubgraph, oneHotEncodedMap,
                true, features); // Root node is at index 0
    }
    return sharedTreeGraph;
  }

}
