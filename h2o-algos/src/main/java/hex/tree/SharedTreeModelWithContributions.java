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
        > extends SharedTreeModel<M, P, O> implements Model.Contributions {
    
  public SharedTreeModelWithContributions(Key<M> selfKey, P parms, O output) {
        super(selfKey, parms, output);
    }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key) {
    return scoreContributions(frame, destination_key, null);
  }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j) {
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
    
    return getScoreContributionsTask(this)
            .withPostMapAction(JobUpdatePostMap.forJob(j))
            .doAll(outputNames.length, Vec.T_NUM, adaptFrm)
            .outputFrame(destination_key, outputNames, null);
  }

  protected abstract ScoreContributionsTask getScoreContributionsTask(SharedTreeModel model);
  
  public class ScoreContributionsTask extends MRTask<ScoreContributionsTask> {
    private final Key<SharedTreeModel> _modelKey;
    
    private transient SharedTreeModel _model;
    private transient SharedTreeOutput _output;
    private transient TreeSHAPPredictor<double[]> _treeSHAP;

    public ScoreContributionsTask(SharedTreeModel model) {
      _modelKey = model._key;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void setupLocal() {
      _model = _modelKey.get();
      assert _model != null;
      _output = (SharedTreeOutput) _model._output; // Need to cast to SharedTreeModel to access ntrees, treeKeys, & init_f params
      assert _output != null;
      final SharedTreeNode[] empty = new SharedTreeNode[0];
      List<TreeSHAPPredictor<double[]>> treeSHAPs = new ArrayList<>(_output._ntrees);
      for (int treeIdx = 0; treeIdx < _output._ntrees; treeIdx++) {
        for (int treeClass = 0; treeClass < _output._treeKeys[treeIdx].length; treeClass++) {
          if (_output._treeKeys[treeIdx][treeClass] == null) {
            continue;
          }
          SharedTreeSubgraph tree = _model.getSharedTreeSubgraph(treeIdx, treeClass);
          SharedTreeNode[] nodes = tree.nodesArray.toArray(empty);
          treeSHAPs.add(new TreeSHAP<>(nodes, nodes, 0));
        }
      }
      assert treeSHAPs.size() == _output._ntrees; // for now only regression and binomial to keep the output sane
      _treeSHAP = new TreeSHAPEnsemble<>(treeSHAPs, (float) _output._init_f);
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

    protected void addContribToNewChunk(float[] contribs, NewChunk[] nc) {
      for (int i = 0; i < nc.length; i++) {
        nc[i].addNum(contribs[i]);
      }
    }
  }
}
