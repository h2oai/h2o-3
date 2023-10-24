package hex.tree;

import hex.ContributionsWithBackgroundFrameTask;
import hex.DistributionFactory;
import hex.Model;
import hex.genmodel.algos.tree.*;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

  protected Frame removeSpecialColumns(Frame frame) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);
    // remove non-feature columns
    adaptFrm.remove(_parms._response_column);
    adaptFrm.remove(_parms._fold_column);
    adaptFrm.remove(_parms._weights_column);
    adaptFrm.remove(_parms._offset_column);
    return adaptFrm;
  }

  protected Frame removeSpecialNNonNumericColumns(Frame frame) {
    Frame adaptFrm = removeSpecialColumns(frame);
    // remove non-numeric columns
    int numCols = adaptFrm.numCols()-1;
    for (int index=numCols; index>=0; index--) {
      if (!adaptFrm.vec(index).isNumeric())
        adaptFrm.remove(index);
    }
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
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j, ContributionsOptions options) {
    if (_output.nclasses() > 2) {
      throw new UnsupportedOperationException(
              "Calculating contributions is currently not supported for multinomial models.");
    }
    
    //FIXME: Original in DRF and GBM corresponds to Compact in XGBoost 
//    if (options._outputFormat == ContributionsOutputFormat.Compact) {
//      throw new UnsupportedOperationException(
//              "Only output_format \"Original\" is supported for this model.");
//    }
    if (!options.isSortingRequired()) {
      return scoreContributions(frame, destination_key, j);
    }

    Frame adaptFrm = removeSpecialColumns(frame);
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


  protected abstract ScoreContributionsWithBackgroundTask getScoreContributionsWithBackgroundTask(SharedTreeModel model, Frame fr, Frame backgroundFrame, boolean expand, int[] catOffsets, ContributionsOptions options);

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j, ContributionsOptions options, Frame backgroundFrame) {
    if (backgroundFrame == null)
      return scoreContributions(frame, destination_key, j, options);
    assert !options.isSortingRequired();
    if (_output.nclasses() > 2) {
      throw new UnsupportedOperationException(
              "Calculating contributions is currently not supported for multinomial models.");
    }
    Log.info("Starting contributions calculation for " + this._key + "...");
    try (Scope.Safe s = Scope.safe(frame, backgroundFrame)) {
      if (options._outputFormat == ContributionsOutputFormat.Compact || _output._domains == null) {
        Frame adaptedFrame = removeSpecialColumns(frame);
        Frame adaptedBgFrame = removeSpecialColumns(backgroundFrame);

        DKV.put(adaptedFrame);
        DKV.put(adaptedBgFrame);
        
        final String[] outputNames = ArrayUtils.append(adaptedFrame.names(), "BiasTerm");
        return getScoreContributionsWithBackgroundTask(this, adaptedFrame, adaptedBgFrame, false, null, options)
                .runAndGetOutput(j, destination_key, outputNames);
      } else {
        Frame adaptedFrame = removeSpecialColumns(frame);
        Frame adaptedBgFrame = removeSpecialColumns(backgroundFrame);
        DKV.put(adaptedFrame);
        DKV.put(adaptedBgFrame);
        assert Parameters.CategoricalEncodingScheme.Enum.equals(_parms._categorical_encoding) : "Unsupported categorical encoding. Only enum is supported.";
        int[] catOffsets = new int[_output._domains.length + 1];

        String[] outputNames;
        int nCols = 1;
        for (int i = 0; i < _output._domains.length; i++) {
          if (!(_output._names[i].equals(_parms._response_column) ||
                  _output._names[i].equals(_parms._fold_column) ||
                  _output._names[i].equals(_parms._weights_column) ||
                  _output._names[i].equals(_parms._offset_column))) {
            if (null == _output._domains[i]) {
              catOffsets[i + 1] = catOffsets[i] + 1; // numeric
            } else {
              catOffsets[i + 1] = catOffsets[i] + _output._domains[i].length + 1; // +1 for missing(NA)
            }
            nCols++;
          }
        }
        catOffsets = Arrays.copyOf(catOffsets, nCols);

        outputNames = new String[catOffsets[catOffsets.length - 1] + 1];
        outputNames[catOffsets[catOffsets.length - 1]] = "BiasTerm";

        int l = 0;

        for (int i = 0; i < _output._names.length; i++) {
          if (!(_output._names[i].equals(_parms._response_column) ||
                  _output._names[i].equals(_parms._fold_column) ||
                  _output._names[i].equals(_parms._weights_column) ||
                  _output._names[i].equals(_parms._offset_column))) {
            if (null == _output._domains[i]) {
              outputNames[l++] = _output._names[i];
            } else {
              for (int k = 0; k < _output._domains[i].length; k++) {
                outputNames[l++] = _output._names[i] + "." + _output._domains[i][k];
              }
              outputNames[l++] = _output._names[i] + ".missing(NA)";
            }
          }
        }

        return Scope.untrack(getScoreContributionsWithBackgroundTask(this, adaptedFrame, adaptedBgFrame, true, catOffsets, options)
                .runAndGetOutput(j, destination_key, outputNames));
      }
    } finally {
      Log.info("Finished contributions calculation for " + this._key + "...");
    }
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

  public class ScoreContributionsWithBackgroundTask extends ContributionsWithBackgroundFrameTask<ScoreContributionsWithBackgroundTask> {
    protected final Key<SharedTreeModel> _modelKey;

    protected transient SharedTreeModel _model;
    protected transient SharedTreeOutput _output;
    protected transient TreeSHAPPredictor<double[]> _treeSHAP;
    protected boolean _expand;
    protected boolean _outputSpace;
    protected int[] _catOffsets;

    public ScoreContributionsWithBackgroundTask(Key<Frame> frKey, Key<Frame> backgroundFrameKey, boolean perReference, SharedTreeModel model, boolean expand, int[] catOffsets, boolean outputSpace) {
      super(frKey, backgroundFrameKey, perReference);
      _modelKey = model._key;
      _expand = expand;
      _catOffsets = catOffsets;
      _outputSpace = outputSpace;
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

    protected void fillInput(Chunk chks[], int row, double[] input) {
      for (int i = 0; i < chks.length; i++) {
        input[i] = chks[i].atd(row);
      }
    }

    @Override
    public void map(Chunk[] cs, Chunk[] bgCs, NewChunk[] nc) {
      assert cs.length <= nc.length - 1; // calculate contribution for each feature + the model bias; nc can be bigger due to expanding cat.vars
      double[] input = MemoryManager.malloc8d(cs.length);
      double[] inputBg = MemoryManager.malloc8d(bgCs.length);

      double[] contribs = MemoryManager.malloc8d(nc.length);
      
      for (int row = 0; row < cs[0]._len; row++) {
        fillInput(cs, row, input);
        for (int bgRow = 0; bgRow < bgCs[0]._len; bgRow++) {
          Arrays.fill(contribs, 0);
          fillInput(bgCs, bgRow, inputBg);
          // calculate Shapley values
          _treeSHAP.calculateInterventionalContributions(input, inputBg, contribs, _catOffsets, _expand);
          doModelSpecificComputation(contribs);
          // Add contribs to new chunk
          addContribToNewChunk(contribs, nc);
        }
      }
    }

    protected void doModelSpecificComputation(double[] contribs) {/*For children*/}

    protected void addContribToNewChunk(double[] contribs, NewChunk[] nc) {
      double transformationRatio = 1;
      double biasTerm = contribs[contribs.length - 1];
      if (_outputSpace) {
        final double linkSpaceX = Arrays.stream(contribs).sum();
        final double linkSpaceBg = biasTerm;
        final double outSpaceX = DistributionFactory.getDistribution(_parms).linkInv(linkSpaceX);
        final double outSpaceBg = DistributionFactory.getDistribution(_parms).linkInv(linkSpaceBg);
        transformationRatio = Math.abs(linkSpaceX - linkSpaceBg) < 1e-6 ? 0 : (outSpaceX - outSpaceBg) / (linkSpaceX - linkSpaceBg);
        biasTerm = outSpaceBg;
      }
      for (int i = 0; i < nc.length - 1; i++) {
        nc[i].addNum(contribs[i] * transformationRatio);
      }
      nc[nc.length - 1].addNum(biasTerm);
    }
  }

}
