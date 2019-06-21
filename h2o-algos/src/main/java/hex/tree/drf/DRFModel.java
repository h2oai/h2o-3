package hex.tree.drf;

import hex.Model;
import hex.genmodel.algos.tree.*;
import hex.tree.SharedTreeModel;
import water.Key;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.MathUtils;
import water.util.SBPrintStream;

import java.util.ArrayList;
import java.util.List;


public class DRFModel extends SharedTreeModel<DRFModel, DRFModel.DRFParameters, DRFModel.DRFOutput>
        implements Model.Contributions{

  public static class DRFParameters extends SharedTreeModel.SharedTreeParameters {
    public String algoName() { return "DRF"; }
    public String fullName() { return "Distributed Random Forest"; }
    public String javaName() { return DRFModel.class.getName(); }
    public boolean _binomial_double_trees = false;
    public int _mtries = -1; //number of columns to use per split. default depeonds on the algorithm and problem (classification/regression)

    public DRFParameters() {
      super();
      // Set DRF-specific defaults (can differ from SharedTreeModel's defaults)
      _mtries = -1;
      _sample_rate = 0.632f;
      _max_depth = 20;
      _min_rows = 1;
    }
  }

  public static class DRFOutput extends SharedTreeModel.SharedTreeOutput {
    public DRFOutput( DRF b) { super(b); }
  }

  public DRFModel(Key<DRFModel> selfKey, DRFParameters parms, DRFOutput output ) { super(selfKey, parms, output); }

  @Override protected boolean binomialOpt() { return !_parms._binomial_double_trees; }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected double[] score0(double[] data, double[] preds, double offset, int ntrees) {
    super.score0(data, preds, offset, ntrees);
    int N = _output._ntrees;
    if (_output.nclasses() == 1) { // regression - compute avg over all trees
      if (N>=1) preds[0] /= N;
    } else { // classification
      if (_output.nclasses() == 2 && binomialOpt()) {
        if (N>=1) {
          preds[1] /= N; //average probability
        }
        preds[2] = 1. - preds[1];
      } else {
        double sum = MathUtils.sum(preds);
        if (sum > 0) MathUtils.div(preds, sum);
      }
    }
    return preds;
  }
  
  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key) {
    if (_output.nclasses() >= 2) {
      throw new UnsupportedOperationException(
              "Calculating contributions for a DRF model is currently not supported for binomial and multinomial models.");
    }

    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);
    // remove non-feature columns
    adaptFrm.remove(_parms._response_column);
    adaptFrm.remove(_parms._fold_column);
    adaptFrm.remove(_parms._weights_column);
    adaptFrm.remove(_parms._offset_column);

    final String[] outputNames = ArrayUtils.append(adaptFrm.names(), "BiasTerm");
    return new ScoreContributionsTask(this)
            .doAll(outputNames.length, Vec.T_NUM, adaptFrm)
            .outputFrame(destination_key, outputNames, null);
  }

  private static class ScoreContributionsTask extends MRTask<DRFModel.ScoreContributionsTask> {
    private final Key<DRFModel> _modelKey;

    private transient DRFModel _model;
    private transient TreeSHAPPredictor<double[]> _treeSHAP;

    private ScoreContributionsTask(DRFModel model) {
      _modelKey = model._key;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void setupLocal() {
      _model = _modelKey.get();
      assert _model != null;
      final SharedTreeNode[] empty = new SharedTreeNode[0];
      List<TreeSHAPPredictor<double[]>> treeSHAPs = new ArrayList<>(_model._output._ntrees);
      for (int treeIdx = 0; treeIdx < _model._output._ntrees; treeIdx++) {
        for (int treeClass = 0; treeClass < _model._output._treeKeys[treeIdx].length; treeClass++) {
          if (_model._output._treeKeys[treeIdx][treeClass] == null) {
            continue;
          }
          SharedTreeSubgraph tree = _model.getSharedTreeSubgraph(treeIdx, treeClass);
          SharedTreeNode[] nodes = tree.nodesArray.toArray(empty);
          treeSHAPs.add(new TreeSHAP<>(nodes, nodes, 0));
        }
      }
      assert treeSHAPs.size() == _model._output._ntrees; // for now only regression and binomial to keep the output sane
      _treeSHAP = new TreeSHAPEnsemble<>(treeSHAPs, (float) _model._output._init_f);
    }

    @Override
    public void map(Chunk chks[], NewChunk[] nc) {
      assert chks.length == nc.length - 1; // calculate contribution for each feature + the model bias
      double[] input = MemoryManager.malloc8d(chks.length);
      float[] contribs = MemoryManager.malloc4f(nc.length);

      Object workspace = _treeSHAP.makeWorkspace();

      for (int row = 0; row < chks[0]._len; row++) {
        for (int i = 0; i < chks.length; i++) {
          input[i] = chks[i].atd(row);
        }
        for (int i = 0; i < contribs.length; i++) {
          contribs[i] = 0;
        }

        // calculate Shapley values
        _treeSHAP.calculateContributions(input, contribs, 0, -1, workspace);

        for (int i = 0; i < nc.length; i++) {
          // Prediction of DRF tree ensemble is an average prediction of all trees. So, divide contribs by ntrees
          nc[i].addNum(contribs[i] / _model._output._ntrees); 
        }
      }
    }
  }
  
  @Override protected void toJavaUnifyPreds(SBPrintStream body) {
    if (_output.nclasses() == 1) { // Regression
      body.ip("preds[0] /= " + _output._ntrees + ";").nl();
    } else { // Classification
      if( _output.nclasses()==2 && binomialOpt()) { // Kept the initial prediction for binomial
        body.ip("preds[1] /= " + _output._ntrees + ";").nl();
        body.ip("preds[2] = 1.0 - preds[1];").nl();
      } else {
        body.ip("double sum = 0;").nl();
        body.ip("for(int i=1; i<preds.length; i++) { sum += preds[i]; }").nl();
        body.ip("if (sum>0) for(int i=1; i<preds.length; i++) { preds[i] /= sum; }").nl();
      }
      if (_parms._balance_classes)
        body.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
      body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, " + defaultThreshold() + ");").nl();
    }
  }

  @Override
  public DrfMojoWriter getMojo() {
    return new DrfMojoWriter(this);
  }

}
