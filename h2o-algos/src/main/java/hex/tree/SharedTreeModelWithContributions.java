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

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j) {
    if (_output.nclasses() > 2) {
      throw new UnsupportedOperationException(
              "Calculating contributions is currently not supported for multinomial models.");
    }

    Frame adaptFrm = makeCompatibleWithTrainingFrame(frame);

    final String[] outputNames = ArrayUtils.append(adaptFrm.names(), "BiasTerm");
    
    return getScoreContributionsTask(this)
            .withPostMapAction(JobUpdatePostMap.forJob(j))
            .doAll(outputNames.length, Vec.T_NUM, adaptFrm)
            .outputFrame(destination_key, outputNames, null);
  }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j, ContributionsOptions options) {
    if (_output.nclasses() > 2) {
      throw new UnsupportedOperationException(
              "Calculating contributions is currently not supported for multinomial models.");
    }
    if (options._outputFormat == ContributionsOutputFormat.Compact) {
      throw new UnsupportedOperationException(
              "Only output_format \"Original\" is supported for this model.");
    }
    if (!options.isSortingRequired()) {
      return scoreContributions(frame, destination_key, j);
    }

    Frame adaptFrm = makeCompatibleWithTrainingFrame(frame);
    final String[] contribNames = ArrayUtils.append(adaptFrm.names(), "BiasTerm");

    final ContributionComposer contributionComposer = new ContributionComposer();
    int topNAdjusted = contributionComposer.checkAndAdjustInput(options._topN, adaptFrm.names().length);
    int bottomNAdjusted = contributionComposer.checkAndAdjustInput(options._bottomN, adaptFrm.names().length);

    int outputSize = Math.min((topNAdjusted+bottomNAdjusted)*2, adaptFrm.names().length*2);
    String[] names = new String[outputSize+1];
    byte[] types = new byte[outputSize+1];
    String[][] domains = new String[outputSize+1][contribNames.length];

    composeScoreContributionTaskMetadata(names, types, domains, adaptFrm.names(), options);

    return getScoreContributionsSoringTask(this, options)
            .withPostMapAction(JobUpdatePostMap.forJob(j))
            .doAll(types, adaptFrm)
            .outputFrame(destination_key, names, domains);
  }

  protected abstract ScoreContributionsTask getScoreContributionsTask(SharedTreeModel model);

  protected abstract ScoreContributionsTask getScoreContributionsSoringTask(SharedTreeModel model, ContributionsOptions options);

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
      List<TreeSHAPPredictor<double[]>> treeSHAPs = new ArrayList<>(_output._ntrees);
      for (int treeIdx = 0; treeIdx < _output._ntrees; treeIdx++) {
        for (int treeClass = 0; treeClass < _output._treeKeys[treeIdx].length; treeClass++) {
          if (_output._treeKeys[treeIdx][treeClass] == null) {
            continue;
          }
          SharedTreeSubgraph tree = _model.getSharedTreeSubgraph(treeIdx, treeClass);
          SharedTreeNode[] nodes = tree.getNodes();
          treeSHAPs.add(new TreeSHAP<>(nodes));
        }
      }
      assert treeSHAPs.size() == _output._ntrees; // for now only regression and binomial to keep the output sane
      _treeSHAP = new TreeSHAPEnsemble<>(treeSHAPs, (float) _output._init_f);
    }

    protected void fillInput(Chunk chks[], int row, double[] input, float[] contribs) {
      for (int i = 0; i < chks.length; i++) {
        input[i] = chks[i].atd(row);
      }
      Arrays.fill(contribs, 0);
    }

    @Override
    public void map(Chunk chks[], NewChunk[] nc) {
      assert chks.length == nc.length - 1; // calculate contribution for each feature + the model bias
      double[] input = MemoryManager.malloc8d(chks.length);
      float[] contribs = MemoryManager.malloc4f(nc.length);

      TreeSHAPPredictor.Workspace workspace = _treeSHAP.makeWorkspace();

      for (int row = 0; row < chks[0]._len; row++) {
        fillInput(chks, row, input, contribs);
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

    private final int _topN;
    private final int _bottomN;
    private final boolean _compareAbs;

    public ScoreContributionsSortingTask(SharedTreeModel model, ContributionsOptions options) {
      super(model);
      _topN = options._topN;
      _bottomN = options._bottomN;
      _compareAbs = options._compareAbs;
    }

    protected void fillInput(Chunk[] chks, int row, double[] input, float[] contribs, int[] contribNameIds) {
      super.fillInput(chks, row, input, contribs);
      for (int i = 0; i < contribNameIds.length; i++) {
        contribNameIds[i] = i;
      }
    }

    @Override
    public void map(Chunk chks[], NewChunk[] nc) {
      double[] input = MemoryManager.malloc8d(chks.length);
      float[] contribs = MemoryManager.malloc4f(chks.length+1);
      int[] contribNameIds = MemoryManager.malloc4(chks.length+1);

      TreeSHAPPredictor.Workspace workspace = _treeSHAP.makeWorkspace();

      for (int row = 0; row < chks[0]._len; row++) {
        fillInput(chks, row, input, contribs, contribNameIds);

        // calculate Shapley values
        _treeSHAP.calculateContributions(input, contribs, 0, -1, workspace);
        doModelSpecificComputation(contribs);
        ContributionComposer contributionComposer = new ContributionComposer();

        int[] contribNameIdsSorted = contributionComposer.composeContributions(
                contribNameIds, contribs, _topN, _bottomN, _compareAbs);

        // Add contribs to new chunk
        addContribToNewChunk(contribs, contribNameIdsSorted, nc);
      }
    }

    protected void addContribToNewChunk(float[] contribs, int[] contribNameIdsSorted, NewChunk[] nc) {
      for (int i = 0, inputPointer = 0; i < nc.length-1; i+=2, inputPointer++) {
        nc[i].addNum(contribNameIdsSorted[inputPointer]);
        nc[i+1].addNum(contribs[contribNameIdsSorted[inputPointer]]);
      }
      nc[nc.length-1].addNum(contribs[contribs.length-1]); // bias
    }
  }
}
