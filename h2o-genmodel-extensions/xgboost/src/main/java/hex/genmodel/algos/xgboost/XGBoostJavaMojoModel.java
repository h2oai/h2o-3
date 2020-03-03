package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.learner.ObjFunction;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.TreeSHAPHelper;
import biz.k11i.xgboost.util.FVec;
import hex.genmodel.GenModel;
import hex.genmodel.PredictContributionsFactory;
import hex.genmodel.algos.tree.*;
import hex.genmodel.PredictContributions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
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

  public XGBoostJavaMojoModel(byte[] boosterBytes, String[] columns, String[][] domains, String responseColumn) {
    this(boosterBytes, columns, domains, responseColumn, false);
  }

  public XGBoostJavaMojoModel(byte[] boosterBytes, String[] columns, String[][] domains, String responseColumn, 
                              boolean enableTreeSHAP) {
    super(columns, domains, responseColumn);
    _predictor = makePredictor(boosterBytes);
    _treeSHAPPredictor = enableTreeSHAP ? makeTreeSHAPPredictor(_predictor) : null;
  }

  @Override
  public void postReadInit() {
    _1hotFactory = new OneHotEncoderFactory();
  }

  public static Predictor makePredictor(byte[] boosterBytes) {
    try (InputStream is = new ByteArrayInputStream(boosterBytes)) {
      return new Predictor(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load predictor.", e);
    }
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
    float initPred = TreeSHAPHelper.getInitPrediction(predictor);
    return new TreeSHAPEnsemble<>(predictors, initPred);
  }

  public final double[] score0(double[] doubles, double offset, double[] preds) {
    // backwards compatibility
    if (_mojo_version == 1.0) {
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

  public final Object makeContributionsWorkspace() {
    return _treeSHAPPredictor.makeWorkspace();
  }

  public final float[] calculateContributions(FVec row, float[] out_contribs, Object workspace) {
    _treeSHAPPredictor.calculateContributions(row, out_contribs, 0, -1, workspace);
    return out_contribs;
  }

  @Override
  public final PredictContributions makeContributionsPredictor() {
    TreeSHAPPredictor<FVec> treeSHAPPredictor = _treeSHAPPredictor != null ? 
            _treeSHAPPredictor : makeTreeSHAPPredictor(_predictor);
    return new XGBoostContributionsPredictor(treeSHAPPredictor);
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

  private class OneHotEncoderFactory implements Serializable {
    private final int[] _catMap;
    private final float _notHot;

    OneHotEncoderFactory() {
      _notHot = _sparse ? Float.NaN : 0;
      if (_catOffsets == null) {
        _catMap = new int[0];
      } else {
        _catMap = new int[_catOffsets[_cats]];
        for (int c = 0; c < _cats; c++) {
          for (int j = _catOffsets[c]; j < _catOffsets[c+1]; j++)
            _catMap[j] = c;
        }
      }
    }

    OneHotEncoderFVec fromArray(double[] input) {
      float[] numValues = new float[_nums];
      int[] catValues = new int[_cats];
      GenModel.setCats(input, catValues, _cats, _catOffsets, _useAllFactorLevels);
      for (int i = 0; i < numValues.length; i++) {
        float val = (float) input[_cats + i];
        numValues[i] = _sparse && (val == 0) ? Float.NaN : val;
      }

      return new OneHotEncoderFVec(_catMap, catValues, numValues, _notHot);
    }
  }

  private class OneHotEncoderFVec implements FVec {
    private final int[] _catMap;
    private final int[] _catValues;
    private final float[] _numValues;
    private final float _notHot;

    private  OneHotEncoderFVec(int[] catMap, int[] catValues, float[] numValues, float notHot) {
      _catMap = catMap;
      _catValues = catValues;
      _numValues = numValues;
      _notHot = notHot;
    }

    @Override
    public final float fvalue(int index) {
      if (index >= _catMap.length)
        return _numValues[index - _catMap.length];

      final boolean isHot = _catValues[_catMap[index]] == index;
      return isHot ? 1 : _notHot;
    }
  }

  private final class XGBoostContributionsPredictor extends ContributionsPredictor<FVec> {
    private XGBoostContributionsPredictor(TreeSHAPPredictor<FVec> treeSHAPPredictor) {
      super(_nums + _catOffsets[_cats] + 1, treeSHAPPredictor);
    }

    @Override
    protected FVec toInputRow(double[] input) {
      return _1hotFactory.fromArray(input);
    }
  }

}
