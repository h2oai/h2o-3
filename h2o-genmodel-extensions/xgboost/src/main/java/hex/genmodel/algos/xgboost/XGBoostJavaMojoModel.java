package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.learner.ObjFunction;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.TreeSHAPHelper;
import biz.k11i.xgboost.util.FVec;
import hex.genmodel.PredictContributionsFactory;
import hex.genmodel.algos.tree.*;
import hex.genmodel.PredictContributions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of XGBoostMojoModel that uses Pure Java Predict
 * see https://github.com/h2oai/xgboost-predictor
 */
public final class XGBoostJavaMojoModel extends XGBoostMojoModel implements PredictContributionsFactory {

  private Predictor _predictor;
  private TreeSHAPPredictor<FVec> _treeSHAPPredictor;
  private OneHotEncoderFactory _1hotFactory;

  @Deprecated
  public XGBoostJavaMojoModel(byte[] boosterBytes, String[] columns, String[][] domains, String responseColumn) {
    this(boosterBytes, null, columns, domains, responseColumn, false);
  }

  @Deprecated
  public XGBoostJavaMojoModel(byte[] boosterBytes,
                              String[] columns, String[][] domains, String responseColumn,
                              boolean enableTreeSHAP) {
    this(boosterBytes, null, columns, domains, responseColumn, enableTreeSHAP);
  }

  public XGBoostJavaMojoModel(byte[] boosterBytes, byte[] auxNodeWeightBytes, 
                              String[] columns, String[][] domains, String responseColumn, 
                              boolean enableTreeSHAP) {
    super(columns, domains, responseColumn);
    _predictor = makePredictor(boosterBytes, auxNodeWeightBytes);
    _treeSHAPPredictor = enableTreeSHAP ? makeTreeSHAPPredictor(_predictor) : null;
  }

  @Override
  public void postReadInit() {
    _1hotFactory = new OneHotEncoderFactory(
        backwardsCompatibility10(), _sparse, _catOffsets, _cats, _nums, _useAllFactorLevels
    );
  }
  
  private boolean backwardsCompatibility10() {
    return _mojo_version == 1.0 && !"gbtree".equals(_boosterType);
  }

  public static Predictor makePredictor(byte[] boosterBytes, byte[] auxNodeWeightBytes) {
    try (InputStream is = new ByteArrayInputStream(boosterBytes)) {
      Predictor p = new Predictor(is);
      updateNodeWeights(p, auxNodeWeightBytes);
      return p;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load predictor.", e);
    }
  }
  public static void updateNodeWeights(Predictor predictor, byte[] auxNodeWeightBytes) {
    if (auxNodeWeightBytes == null)
      return;
    assert predictor.getNumClass() <= 2;
    GBTree gbTree = (GBTree) predictor.getBooster();
    RegTree[] trees = gbTree.getGroupedTrees()[0];
    double[][] weights = AuxNodeWeightsHelper.fromBytes(auxNodeWeightBytes);
    assert trees.length == weights.length;
    AuxNodeWeightsHelper.updateNodeWeights(trees, weights);
  }
  private static TreeSHAPPredictor<FVec> makeTreeSHAPPredictor(Predictor predictor) {
    if (predictor.getNumClass() > 2) {
      throw new UnsupportedOperationException("Calculating contributions is currently not supported for multinomial models.");
    }
    GBTree gbTree = (GBTree) predictor.getBooster();
    RegTree[] trees = gbTree.getGroupedTrees()[0];
    List<TreeSHAPPredictor<FVec>> predictors = new ArrayList<>(trees.length);
    for (RegTree tree : trees) {
      predictors.add(TreeSHAPHelper.makePredictor(tree));
    }
    float initPred = predictor.getBaseScore();
    return new TreeSHAPEnsemble<>(predictors, initPred);
  }

  public final double[] score0(double[] doubles, double offset, double[] preds) {
    if (backwardsCompatibility10()) {
      // throw an exception for unexpectedly long input vector
      if (doubles.length > _cats + _nums) {
        throw new ArrayIndexOutOfBoundsException("Too many input values.");
      }
      // for unexpectedly short input vector handle the situation gracefully
      if (doubles.length < _cats + _nums) {
        double[] tmp = new double[_cats + _nums];
        System.arraycopy(doubles, 0,tmp, 0, doubles.length);
        doubles = tmp;
      }
    }
    FVec row = _1hotFactory.fromArray(doubles);
    float[] out;
    if (_hasOffset) {
      out = _predictor.predict(row, (float) offset);
    } else if (offset != 0) {
      throw new UnsupportedOperationException("Unsupported: offset != 0");
    } else {
      out = _predictor.predict(row);
    }
    return toPreds(doubles, out, preds, _nclasses, _priorClassDistrib, _defaultThreshold);
  }

  public final TreeSHAPPredictor.Workspace makeContributionsWorkspace() {
    return _treeSHAPPredictor.makeWorkspace();
  }

  public final float[] calculateContributions(FVec row, float[] out_contribs, TreeSHAPPredictor.Workspace workspace) {
    _treeSHAPPredictor.calculateContributions(row, out_contribs, 0, -1, workspace);
    return out_contribs;
  }

  @Override
  public final PredictContributions makeContributionsPredictor() {
    TreeSHAPPredictor<FVec> treeSHAPPredictor = _treeSHAPPredictor != null ? 
            _treeSHAPPredictor : makeTreeSHAPPredictor(_predictor);
    return new XGBoostContributionsPredictor(this, treeSHAPPredictor);
  }

  static ObjFunction getObjFunction(String name) {
    return ObjFunction.fromName(name);
  }

  @Override
  public void close() {
    _predictor = null;
    _treeSHAPPredictor = null;
    _1hotFactory = null;
  }

  @Override
  public SharedTreeGraph convert(final int treeNumber, final String treeClass) {
    GradBooster booster = _predictor.getBooster();
    return computeGraph(booster, treeNumber);
  }

  @Override
  public SharedTreeGraph convert(final int treeNumber, final String treeClass, final ConvertTreeOptions options) {
    return convert(treeNumber, treeClass); // Options currently do not apply to XGBoost trees conversion
  }

  @Override
  public double getInitF() {
    return _predictor.getBaseScore();
  }

  @Override
  public SharedTreeMojoModel.LeafNodeAssignments getLeafNodeAssignments(double[] doubles) {
    FVec row = _1hotFactory.fromArray(doubles);
    final SharedTreeMojoModel.LeafNodeAssignments result = new SharedTreeMojoModel.LeafNodeAssignments();
    result._paths = _predictor.predictLeafPath(row);
    result._nodeIds = _predictor.predictLeaf(row);
    return result;
  }

  @Override
  public String[] getDecisionPath(double[] doubles) {
    FVec row = _1hotFactory.fromArray(doubles);
    return _predictor.predictLeafPath(row);
  }

  private final class XGBoostContributionsPredictor extends ContributionsPredictor<FVec> {
    private XGBoostContributionsPredictor(XGBoostMojoModel model, TreeSHAPPredictor<FVec> treeSHAPPredictor) {
      super(_nums + _catOffsets[_cats] + 1, makeFeatureContributionNames(model), treeSHAPPredictor);
    }

    @Override
    protected FVec toInputRow(double[] input) {
      return _1hotFactory.fromArray(input);
    }
  }

  private static String[] makeFeatureContributionNames(XGBoostMojoModel m) {
    final String[] names = new String[m._nums + m._catOffsets[m._cats]];
    final String[] features = m.features();
    int i = 0;
    for (int c = 0; c < features.length; c++) {
      if (m._domains[c] == null) {
        names[i++] = features[c];
      } else {
        for (String d : m._domains[c])
          names[i++] = features[c] + "." + d;
        names[i++] = features[c] + ".missing(NA)";
      }
    }
    assert names.length == i;
    return names;
  }

}
