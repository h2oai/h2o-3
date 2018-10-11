package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeImpl;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import ml.dmlc.xgboost4j.java.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Please note: user is advised to explicitly release the native resources of XGBoost by calling close method on the instance.
 */
public final class XGBoostNativeMojoModel extends XGBoostMojoModel {
  Booster _booster;

  public XGBoostNativeMojoModel(byte[] boosterBytes, String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
    _booster = makeBooster(boosterBytes);
  }

  private static Booster makeBooster(byte[] boosterBytes) {
    try (InputStream is = new ByteArrayInputStream(boosterBytes)) {
      return BoosterHelper.loadModel(is);
    } catch (Exception xgBoostError) {
      throw new IllegalStateException("Unable to load XGBooster", xgBoostError);
    }
  }

  public final double[] score0(double[] doubles, double offset, double[] preds) {
    return score0(doubles, offset, preds,
            _booster, _nums, _cats, _catOffsets, _useAllFactorLevels,
            _nclasses, _priorClassDistrib, _defaultThreshold, _sparse);
  }

  public static double[] score0(double[] doubles, double offset, double[] preds,
                                Booster _booster, int _nums, int _cats,
                                int[] _catOffsets, boolean _useAllFactorLevels,
                                int nclasses, double[] _priorClassDistrib,
                                double _defaultThreshold, final boolean _sparse) {
    if (offset != 0) throw new UnsupportedOperationException("Unsupported: offset != 0");

    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    // convert dense doubles to expanded floats
    final float[] floats = new float[_nums + cats]; //TODO: use thread-local storage
    GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, null, null, _useAllFactorLevels, _sparse /*replace NA with 0*/);
    float[] out;
    DMatrix dmat = null;
    try {
      dmat = new DMatrix(floats,1, floats.length, _sparse ? 0 : Float.NaN);
      final DMatrix row = dmat;
      BoosterHelper.BoosterOp<float[]> predictOp = new BoosterHelper.BoosterOp<float[]>() {
        @Override
        public float[] apply(Booster booster) throws XGBoostError {
          return booster.predict(row)[0];
        }
      };
      out = BoosterHelper.doWithLocalRabit(predictOp, _booster);
    } catch (XGBoostError xgBoostError) {
      throw new IllegalStateException("Failed XGBoost prediction.", xgBoostError);
    } finally {
      BoosterHelper.dispose(dmat);
    }

    return toPreds(doubles, out, preds, nclasses, _priorClassDistrib, _defaultThreshold);
  }

  @Override
  public void close() {
    BoosterHelper.dispose(_booster);
  }

  public String[] getBoosterDump(final boolean withStats, final String format) {
    final Path featureMapFile;
    if (_featureMap != null && _featureMap.length > 0)
      try {
        featureMapFile = Files.createTempFile("featureMap", ".txt");
      } catch (IOException e) {
        throw new IllegalStateException("Unable to write a temporary file with featureMap");
      }
    else
      featureMapFile = null;
    try {
      if (featureMapFile != null) {
        Files.write(featureMapFile, Arrays.asList(_featureMap), Charset.defaultCharset(), StandardOpenOption.WRITE);
      }

      BoosterHelper.BoosterOp<String[]> dumpOp = new BoosterHelper.BoosterOp<String[]>() {
        @Override
        public String[] apply(Booster booster) throws XGBoostError {
          String featureMap = featureMapFile != null ? featureMapFile.toFile().getAbsolutePath() : null;
          return booster.getModelDump(featureMap, withStats, format);
        }
      };

      return BoosterHelper.doWithLocalRabit(dumpOp, _booster);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write feature map file", e);
    } catch (XGBoostError e) {
      throw new IllegalStateException("Failed to dump model", e);
    } finally {
      if (featureMapFile != null) {
        try {
          Files.deleteIfExists(featureMapFile);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 2 || ! "--dump".equals(args[0])) {
      usage();
      System.exit(1);
    }
    String mojoFile = args[1];
    boolean withStats = args.length > 2 ? Boolean.valueOf(args[2]) : false;
    String format = args.length > 3 ? args[3] : "text";

    XGBoostNativeMojoModel mojoModel = (XGBoostNativeMojoModel) MojoModel.load(mojoFile);
    for (String dumpLine : mojoModel.getBoosterDump(withStats, format))
      System.out.println(dumpLine);
  }

  private static void usage() {
    System.out.println("java -cp h2o-genmodel.jar " + XGBoostNativeMojoModel.class.getCanonicalName() + " --dump <mojo> [withStats?] [format]");
  }

  @Override
  public SharedTreeGraph computeGraph(int treeNumber, int treeClass) {
    GradBooster booster = null;
    try {
      booster = new Predictor(new ByteArrayInputStream(_booster.toByteArray())).getBooster();
    } catch (IOException | XGBoostError e) {
      e.printStackTrace();
    }

    if (!(booster instanceof GBTree)) {
      throw new IllegalArgumentException(String.format("Given XGBoost model is not backed by a tree-based booster. Booster class is %d",
              booster.getClass().getCanonicalName()));
    }

    final RegTree[][] groupedTrees = ((GBTree) booster).getGroupedTrees();
    if (treeClass >= groupedTrees.length) {
      throw new IllegalArgumentException("Given XGBoost model does not have given class"); //Todo: better info - print at least number of existing classes, ideal situation would be to print the tring
    }

    final RegTree[] treesInGroup = groupedTrees[treeClass];

    if (treeNumber >= treesInGroup.length) {
      throw new IllegalArgumentException("There is no such tree number for given class"); // Todo: better info - same as above
    }

    final RegTreeImpl.Node[] treeNodes = treesInGroup[treeNumber].getNodes();
    assert treeNodes.length >= 1;

    SharedTreeGraph sharedTreeGraph = new SharedTreeGraph();
    final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeGraph.makeSubgraph("XGBoost Graph");

    final boolean[] oneHotEncodedMap = markOneHotEncodedCategoricals(_featureMap); // XGBoost's usage of one-hot encoding assumed
    constructSubgraph(treeNodes, sharedTreeSubgraph.makeRootNode(), 0, sharedTreeSubgraph, oneHotEncodedMap, true); // Root node is at index 0
    return sharedTreeGraph;

  }

  private void constructSubgraph(final RegTreeImpl.Node[] xgBoostNodes, final SharedTreeNode sharedTreeNode,
                                        final int nodeIndex, final SharedTreeSubgraph sharedTreeSubgraph,
                                        final boolean[] oneHotEncodedMap, boolean inclusiveNA) {
    final RegTreeImpl.Node xgBoostNode = xgBoostNodes[nodeIndex];
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
    sharedTreeNode.setCol(xgBoostNode.split_index(), _featureMap[xgBoostNode.split_index()]);
    sharedTreeNode.setInclusiveNa(inclusiveNA);
    sharedTreeNode.setNodeNumber(nodeIndex);

    if (xgBoostNode.getLeftChildIndex() != -1) {
      constructSubgraph(xgBoostNodes, sharedTreeSubgraph.makeLeftChildNode(sharedTreeNode),
              xgBoostNode.getLeftChildIndex(), sharedTreeSubgraph, oneHotEncodedMap, xgBoostNode.default_left());
    }

    if (xgBoostNode.getRightChildIndex() != -1) {
      constructSubgraph(xgBoostNodes, sharedTreeSubgraph.makeRightChildNode(sharedTreeNode),
              xgBoostNode.getRightChildIndex(), sharedTreeSubgraph, oneHotEncodedMap, !xgBoostNode.default_left());
    }
  }


  private boolean[] markOneHotEncodedCategoricals(final String[] featureMap) {
    final int numColumns = featureMap.length;

    int numCatCols = -1;
    for (int i = 0; i < featureMap.length;i++) {
      final String[] s = featureMap[i].split(" ");
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
}
