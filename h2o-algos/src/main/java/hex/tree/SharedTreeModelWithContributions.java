package hex.tree;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.tree.*;
import hex.genmodel.attributes.parameters.KeyValue;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.*;

import java.util.ArrayList;
import java.util.Arrays;
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

  private Frame removeSpecialColumns(Frame frame) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);
    // remove non-feature columns
    adaptFrm.remove(_parms._response_column);
    adaptFrm.remove(_parms._fold_column);
    adaptFrm.remove(_parms._weights_column);
    adaptFrm.remove(_parms._offset_column);
    return adaptFrm;
  }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j) {
    if (_output.nclasses() > 2) {
      throw new UnsupportedOperationException(
              "Calculating contributions is currently not supported for multinomial models.");
    }

    Frame adaptFrm = removeSpecialColumns(frame);

    final String[] outputNames = ArrayUtils.append(adaptFrm.names(), "BiasTerm");
    
    return getScoreContributionsTask(this)
            .withPostMapAction(JobUpdatePostMap.forJob(j))
            .doAll(outputNames.length, Vec.T_NUM, adaptFrm)
            .outputFrame(destination_key, outputNames, null);
  }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, int topN, int topBottomN, boolean abs, Job<Frame> j) {
    if (_output.nclasses() > 2) {
      throw new UnsupportedOperationException(
              "Calculating contributions is currently not supported for multinomial models.");
    }

    Frame adaptFrm = removeSpecialColumns(frame);
    final String[] contribNames = ArrayUtils.append(adaptFrm.names(), "BiasTerm");

    final ContributionComposer contributionComposer = new ContributionComposer();
    int topNAdjusted = contributionComposer.checkAndAdjustInput(topN, adaptFrm.names().length);
    int topBottomNAdjusted = contributionComposer.checkAndAdjustInput(topBottomN, adaptFrm.names().length);

    int outputSize = Math.min((topNAdjusted+topBottomNAdjusted)*2, adaptFrm.names().length*2);
    String[] names = new String[outputSize];
    byte[] types = new byte[outputSize];

    for (int i = 0, topFeatureIterator = 1, bottomFeatureIterator = 1; i < outputSize; i+=2, topFeatureIterator++) {
      if (topFeatureIterator <= topNAdjusted) {
        names[i] = "top_feature_" + topFeatureIterator;
        names[i+1] = "top_value_" + topFeatureIterator;
      } else {
        names[i] = "bottom_top_feature_" + bottomFeatureIterator;
        names[i+1] = "bottom_top_value_" + bottomFeatureIterator;
        bottomFeatureIterator++;
      }
      types[i] = Vec.T_STR;
      types[i+1] = Vec.T_NUM;
    }

    final String[] outputNames = ArrayUtils.append(names, "BiasTerm");
    types = ArrayUtils.append(types, Vec.T_NUM);

    return getScoreContributionsSoringTask(this, contribNames, topN, topBottomN, abs)
            .withPostMapAction(JobUpdatePostMap.forJob(j))
            .doAll(types, adaptFrm)
            .outputFrame(destination_key, outputNames, null);
  }

  protected abstract ScoreContributionsTask getScoreContributionsTask(SharedTreeModel model);

  protected abstract ScoreContributionsTask getScoreContributionsSoringTask(SharedTreeModel model, String[] contribNames, int topN, int topBottomN, boolean abs);

  public class ScoreContributionsTask extends MRTask<ScoreContributionsTask> {
    protected final Key<SharedTreeModel> _modelKey;

    protected transient SharedTreeModel _model;
    protected transient SharedTreeOutput _output;
    protected transient TreeSHAPPredictor<double[]> _treeSHAP;

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
        Arrays.fill(contribs, 0);

        // calculate Shapley values
        _treeSHAP.calculateContributions(input, contribs, 0, -1, workspace);

        doModelSpecificComputation(contribs);

        // Add contribs to new chunk
        addContribToNewChunk(contribs, nc);
      }
    }

    protected void doModelSpecificComputation(float[] contribs) {/*For children*/}

    protected void addContribToNewChunk(float[] contribs, NewChunk[] nc) {
      for (int i = 0; i < nc.length; i++) {
        nc[i].addNum(contribs[i]);
      }
    }
  }

  public class ScoreContributionsSortingTask extends ScoreContributionsTask {

    private transient String[] _contribNames;
    private transient int _topN;
    private transient int _topBottomN;
    private transient boolean _abs;

    public ScoreContributionsSortingTask(SharedTreeModel model, String[] contribNames, int topN, int topBottomN, boolean abs) {
      super(model);
      _topN = topN;
      _topBottomN = topBottomN;
      _abs = abs;
      _contribNames = contribNames;
    }

    @Override
    public void map(Chunk chks[], NewChunk[] nc) {
      double[] input = MemoryManager.malloc8d(chks.length);
      float[] contribs = MemoryManager.malloc4f(chks.length+1);

      Object workspace = _treeSHAP.makeWorkspace();

      for (int row = 0; row < chks[0]._len; row++) {
        for (int i = 0; i < chks.length; i++) {
          input[i] = chks[i].atd(row);
        }
        Arrays.fill(contribs, 0);

        // calculate Shapley values
        _treeSHAP.calculateContributions(input, contribs, 0, -1, workspace);
        doModelSpecificComputation(contribs);
        KeyValue[] contribsSorted = (new ContributionComposer()).composeContributions(contribs, _contribNames, _topN, _topBottomN, _abs);

        // Add contribs to new chunk
        addContribToNewChunk(contribsSorted, nc);
      }
    }

    protected void addContribToNewChunk(KeyValue[] contribs, NewChunk[] nc) {
      for (int i = 0, inputPointer = 0; i < nc.length-1; i+=2, inputPointer++) {
        nc[i].addStr(contribs[inputPointer].key);
        nc[i+1].addNum(contribs[inputPointer].value);
      }
      nc[nc.length-1].addNum(contribs[contribs.length-1].value); // bias
    }
  }
}
