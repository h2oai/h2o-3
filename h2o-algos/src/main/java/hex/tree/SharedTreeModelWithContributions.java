package hex.tree;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.tree.*;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.*;

import java.util.ArrayList;
import java.util.List;
import hex.Model;
import water.Key;

public abstract class SharedTreeModelWithContributions<
        M extends SharedTreeModel<M, P, O>,
        P extends SharedTreeModel.SharedTreeParameters,
        O extends SharedTreeModel.SharedTreeOutput
        > extends SharedTreeModel<M, P, O> implements Model.Contributions{
    
    public SharedTreeModelWithContributions(Key<M> selfKey, P parms, O output) {
        super(selfKey, parms, output);
    }
    
  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key) {
    if (_output.nclasses() > 2) {
      throw new UnsupportedOperationException(
              "Calculating contributions is currently not supported for multinomial models.");
    }

    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);
    // remove non-feature columns
    adaptFrm.remove(_parms._response_column);
    adaptFrm.remove(_parms._fold_column);
    adaptFrm.remove(_parms._weights_column);
    adaptFrm.remove(_parms._offset_column);

    final String[] outputNames = ArrayUtils.append(adaptFrm.names(), "BiasTerm");
    
    return getScoreContributionsTask(this, _output._ntrees, _output._treeKeys, _output._init_f)
            .doAll(outputNames.length, Vec.T_NUM, adaptFrm)
            .outputFrame(destination_key, outputNames, null);
  }

  protected abstract ScoreContributionsTask getScoreContributionsTask(SharedTreeModel model, int ntrees, Key<CompressedTree>[][] treeKeys, double init_f);
    
  public class ScoreContributionsTaskGBM extends ScoreContributionsTask {

    public ScoreContributionsTaskGBM(SharedTreeModel model, int ntrees, Key<CompressedTree>[][] treeKeys, double init_f) {
      super(model, ntrees, treeKeys, init_f);
    }

    @Override
    public void addContribToNewChunk(float[] contribs, NewChunk[] nc) {
      for (int i = 0; i < nc.length; i++) {
        nc[i].addNum(contribs[i]);
      }
    }
  }
  
  public class ScoreContributionsTaskDRF extends ScoreContributionsTask {

    public ScoreContributionsTaskDRF(SharedTreeModel model, int ntrees, Key<CompressedTree>[][] treeKeys, double init_f) {
      super(model, ntrees, treeKeys, init_f);
    }

    @Override
    public void addContribToNewChunk(float[] contribs, NewChunk[] nc) {
      for (int i = 0; i < nc.length; i++) {
        // Prediction of DRF tree ensemble is an average prediction of all trees. So, divide contribs by ntrees
        if (_model._output.nclasses() == 1) { //Regression
          nc[i].addNum(contribs[i] / _ntrees);
        } else { //Binomial
          float featurePlusBiasRatio = (float)1 / (_model._output.nfeatures() + 1); // + 1 for bias term
          nc[i].addNum(featurePlusBiasRatio - (contribs[i] / _ntrees));
        }
      }
    }
  }
  
  public abstract class ScoreContributionsTask extends MRTask<ScoreContributionsTask> {

    private final Key<SharedTreeModel> _modelKey;

    protected transient SharedTreeModel _model;
    protected transient int _ntrees;
    protected transient Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;
    protected transient double _init_f;
    protected transient TreeSHAPPredictor<double[]> _treeSHAP;

    public ScoreContributionsTask(SharedTreeModel model, int ntrees,
                                  Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] treeKeys,
                                  double init_f) {
      _modelKey = model._key;
      _ntrees = ntrees;
      _treeKeys = treeKeys;
      _init_f = init_f;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void setupLocal() {
      _model = _modelKey.get();
      assert _model != null;
      final SharedTreeNode[] empty = new SharedTreeNode[0];
      List<TreeSHAPPredictor<double[]>> treeSHAPs = new ArrayList<>(_ntrees);
      for (int treeIdx = 0; treeIdx < _ntrees; treeIdx++) {
        for (int treeClass = 0; treeClass < _treeKeys[treeIdx].length; treeClass++) {
          if (_treeKeys[treeIdx][treeClass] == null) {
            continue;
          }
          SharedTreeSubgraph tree = _model.getSharedTreeSubgraph(treeIdx, treeClass);
          SharedTreeNode[] nodes = tree.nodesArray.toArray(empty);
          treeSHAPs.add(new TreeSHAP<>(nodes, nodes, 0));
        }
      }
      assert treeSHAPs.size() == _ntrees; // for now only regression and binomial to keep the output sane
      _treeSHAP = new TreeSHAPEnsemble<>(treeSHAPs, (float) _init_f);
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
        
        // Add contribs to new chunk
        addContribToNewChunk(contribs, nc);

      }
    }
    
    public abstract void addContribToNewChunk(float[] contribs, NewChunk[] nc);
    
  }
}
