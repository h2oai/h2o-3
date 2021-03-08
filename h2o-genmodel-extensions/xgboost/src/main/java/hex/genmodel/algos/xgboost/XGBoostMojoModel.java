package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.*;

import java.io.Closeable;
import java.util.Arrays;

import static hex.genmodel.algos.tree.SharedTreeMojoModel.treeName;

/**
 * "Gradient Boosting Machine" MojoModel
 */
public abstract class XGBoostMojoModel extends MojoModel implements TreeBackedMojoModel, SharedTreeGraphConverter, PlattScalingMojoHelper.MojoModelWithCalibration, Closeable {

  private static final String SPACE = " ";

  public enum ObjectiveType {
    BINARY_LOGISTIC("binary:logistic"),
    REG_GAMMA("reg:gamma"),
    REG_TWEEDIE("reg:tweedie"),
    COUNT_POISSON("count:poisson"),
    REG_SQUAREDERROR("reg:squarederror"),
    @Deprecated REG_LINEAR("reg:linear"), // deprectated in favour of REG_SQUAREDERROR
    MULTI_SOFTPROB("multi:softprob"),
    RANK_PAIRWISE("rank:pairwise");

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

  public String _boosterType;
  public int _ntrees;
  public int _nums;
  public int _cats;
  public int[] _catOffsets;
  public boolean _useAllFactorLevels;
  public boolean _sparse;
  public String _featureMap;
  public boolean _hasOffset;

  /**
   * GLM's beta used for calibrating output probabilities using Platt Scaling.
   */
  protected double[] _calib_glm_beta;

  public XGBoostMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  // finalize MOJO initialization after all the fields are read
  public void postReadInit() {}

  @Override
  public boolean requiresOffset() {
    return _hasOffset;
  }

  @Override
  public final double[] score0(double[] row, double[] preds) {
    if (_hasOffset) {
      throw new IllegalStateException("Model was trained with offset, use score0 with offset");
    }
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

  @Override
  public int getNTreeGroups() {
    return _ntrees;
  }

  @Override
  public int getNTreesPerGroup() {
    return _nclasses > 2 ? _nclasses : 1;
  }

  @Override
  public double[] getCalibGlmBeta() {
    return _calib_glm_beta;
  }

  @Override
  public boolean calibrateClassProbabilities(double[] preds) {
    return PlattScalingMojoHelper.calibrateClassProbabilities(this, preds);
  }

  protected void constructSubgraph(final RegTreeNode[] xgBoostNodes, final SharedTreeNode sharedTreeNode,
                                   final int nodeIndex, final SharedTreeSubgraph sharedTreeSubgraph,
                                   final boolean[] oneHotEncodedMap, final boolean inclusiveNA, final String[] features) {
    final RegTreeNode xgBoostNode = xgBoostNodes[nodeIndex];
    // Not testing for NaNs, as SharedTreeNode uses NaNs as default values.
    //No domain set, as the structure mimics XGBoost's tree, which is numeric-only
    if (oneHotEncodedMap[xgBoostNode.getSplitIndex()]) {
      //Shared tree model uses < to the left and >= to the right. Transforiming one-hot encoded categoricals
      // from 0 to 1 makes it fit the current split description logic
      sharedTreeNode.setSplitValue(1.0F);
    } else {
      sharedTreeNode.setSplitValue(xgBoostNode.getSplitCondition());
    }
    sharedTreeNode.setPredValue(xgBoostNode.getLeafValue());
    sharedTreeNode.setCol(xgBoostNode.getSplitIndex(), features[xgBoostNode.getSplitIndex()].split(SPACE)[1]);
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
      assert s.length >= 3; // There should be at least three tokens, the third token is feature type (int, categorical etc).
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

  /**
   * Converts a given XGBoost tree (or a collection of trees) to a common tree representation 
   * 
   * @param booster tree based booster
   * @param treeToPrint index of tree to convert or -1 if all trees should be converted
   * @return instance of SharedTreeGraph
   */
  SharedTreeGraph computeGraph(final GradBooster booster, final int treeToPrint) {
    if (!(booster instanceof GBTree)) {
      throw new IllegalArgumentException(String.format("Given XGBoost model is not backed by a tree-based booster. Booster class is %s",
              booster.getClass().getCanonicalName()));
    }

    final int ntreeGroups = getNTreeGroups();
    final int ntreePerGroup = getNTreesPerGroup();

    if (treeToPrint >= ntreeGroups) {
      throw new IllegalArgumentException("Tree " + treeToPrint + " does not exist (max " + ntreeGroups + ")");
    }

    final String[] features = constructFeatureMap();
    final boolean[] oneHotEncodedMap = markOneHotEncodedCategoricals(features); // XGBoost's usage of one-hot encoding assumed
    final RegTree[][] treesAndClasses = ((GBTree) booster).getGroupedTrees();
    final SharedTreeGraph g = new SharedTreeGraph();
    for (int j = Math.max(treeToPrint, 0); j < ntreeGroups; j++) {
      for (int i = 0; i < ntreePerGroup; i++) {
        if (j >= treesAndClasses[i].length || treesAndClasses[i][j] == null)
          continue; // tree doesn't exist for the given class (in multiclass some can be missing)
        RegTreeNode[] treeNodes = treesAndClasses[i][j].getNodes();
        assert treeNodes.length >= 1;
        String[] domainValues = isSupervised() ? getDomainValues(getResponseIdx()) : null;
        String treeName = treeName(j, i, domainValues);
        SharedTreeSubgraph sg = g.makeSubgraph(treeName);
        constructSubgraph(treeNodes, sg.makeRootNode(), 0, sg, oneHotEncodedMap,
                true, features); // Root node is at index 0
      }

      if (treeToPrint >= 0)
        break;
    }
    
    return g;
  }

  @Override
  public SharedTreeGraph convert(int treeNumber, String treeClass, ConvertTreeOptions options) {
    return convert(treeNumber, treeClass); // no use for options as of now
  }

}
