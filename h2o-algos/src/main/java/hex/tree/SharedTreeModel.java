package hex.tree;

import hex.*;

import static hex.ModelCategory.Binomial;
import static hex.genmodel.GenModel.createAuxKey;

import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.glm.GLMModel;
import hex.util.LinearAlgebraUtils;
import water.*;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.JCodeSB;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;

public abstract class SharedTreeModel<
        M extends SharedTreeModel<M, P, O>,
        P extends SharedTreeModel.SharedTreeParameters,
        O extends SharedTreeModel.SharedTreeOutput
        > extends Model<M, P, O> implements Model.LeafNodeAssignment, Model.GetMostImportantFeatures {

  @Override
  public String[] getMostImportantFeatures(int n) {
    if (_output == null) return null;
    TwoDimTable vi = _output._variable_importances;
    if (vi==null) return null;
    n = Math.min(n, vi.getRowHeaders().length);
    String[] res = new String[n];
    System.arraycopy(vi.getRowHeaders(), 0, res, 0, n);
    return res;
  }

  @Override public ToEigenVec getToEigenVec() { return LinearAlgebraUtils.toEigen; }

  public abstract static class SharedTreeParameters extends Model.Parameters {

    public int _ntrees=50; // Number of trees in the final model. Grid Search, comma sep values:50,100,150,200

    public int _max_depth = 5; // Maximum tree depth. Grid Search, comma sep values:5,7

    public double _min_rows = 10; // Fewest allowed observations in a leaf (in R called 'nodesize'). Grid Search, comma sep values

    public int _nbins = 20; // Numerical (real/int) cols: Build a histogram of this many bins, then split at the best point

    public int _nbins_cats = 1024; // Categorical (factor) cols: Build a histogram of this many bins, then split at the best point

    public double _min_split_improvement = 1e-5; // Minimum relative improvement in squared error reduction for a split to happen

    public enum HistogramType { AUTO, UniformAdaptive, Random, QuantilesGlobal, RoundRobin }
    public HistogramType _histogram_type = HistogramType.AUTO; // What type of histogram to use for finding optimal split points

    public double _r2_stopping = Double.MAX_VALUE; // Stop when the r^2 metric equals or exceeds this value

    public int _nbins_top_level = 1<<10; //hardcoded maximum top-level number of bins for real-valued columns

    public boolean _build_tree_one_node = false;

    public int _score_tree_interval = 0; // score every so many trees (no matter what)

    public int _initial_score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring the first  4 secs

    public int _score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring each iteration every 4 secs

    public double _sample_rate = 0.632; //fraction of rows to sample for each tree

    public double[] _sample_rate_per_class; //fraction of rows to sample for each tree, per class

    public boolean _calibrate_model = false; // Use Platt Scaling
    public Key<Frame> _calibration_frame;

    public Frame calib() { return _calibration_frame == null ? null : _calibration_frame.get(); }

    @Override public long progressUnits() { return _ntrees + (_histogram_type==HistogramType.QuantilesGlobal || _histogram_type==HistogramType.RoundRobin ? 1 : 0); }

    public double _col_sample_rate_change_per_level = 1.0f; //relative change of the column sampling rate for every level
    public double _col_sample_rate_per_tree = 1.0f; //fraction of columns to sample for each tree

    /** Fields which can NOT be modified if checkpoint is specified.
     * FIXME: should be defined in Schema API annotation
     */
    private static String[] CHECKPOINT_NON_MODIFIABLE_FIELDS = { "_build_tree_one_node", "_sample_rate", "_max_depth", "_min_rows", "_nbins", "_nbins_cats", "_nbins_top_level"};

    protected String[] getCheckpointNonModifiableFields() {
      return CHECKPOINT_NON_MODIFIABLE_FIELDS;
    }

    /** This method will take actual parameters and validate them with parameters of
     * requested checkpoint. In case of problem, it throws an API exception.
     *
     * @param checkpointParameters checkpoint parameters
     */
    public void validateWithCheckpoint(SharedTreeParameters checkpointParameters) {
      for (Field fAfter : this.getClass().getFields()) {
        // only look at non-modifiable fields
        if (ArrayUtils.contains(getCheckpointNonModifiableFields(),fAfter.getName())) {
          for (Field fBefore : checkpointParameters.getClass().getFields()) {
            if (fBefore.equals(fAfter)) {
              try {
                if (!PojoUtils.equals(this, fAfter, checkpointParameters, checkpointParameters.getClass().getField(fAfter.getName()))) {
                  throw new H2OIllegalArgumentException(fAfter.getName(), "TreeBuilder", "Field " + fAfter.getName() + " cannot be modified if checkpoint is specified!");
                }
              } catch (NoSuchFieldException e) {
                throw new H2OIllegalArgumentException(fAfter.getName(), "TreeBuilder", "Field " + fAfter.getName() + " is not supported by checkpoint!");
              }
            }
          }
        }
      }
    }
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
      case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(),domain);
      case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
      default: throw H2O.unimpl();
    }
  }

  public abstract static class SharedTreeOutput extends Model.Output {
    /** InitF value (for zero trees)
     *  f0 = mean(yi) for gaussian
     *  f0 = log(yi/1-yi) for bernoulli
     *
     *  For GBM bernoulli, the initial prediction for 0 trees is
     *  p = 1/(1+exp(-f0))
     *
     *  From this, the mse for 0 trees (null model) can be computed as follows:
     *  mean((yi-p)^2)
     * */
    public double _init_f;

    /** Number of trees actually in the model (as opposed to requested) */
    public int _ntrees;

    /** More indepth tree stats */
    public final TreeStats _treeStats;

    /** Trees get big, so store each one separately in the DKV. */
    public Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;
    public Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeysAux;

    public ScoreKeeper[/*ntrees+1*/] _scored_train;
    public ScoreKeeper[/*ntrees+1*/] _scored_valid;
    public ScoreKeeper[] scoreKeepers() {
      ArrayList<ScoreKeeper> skl = new ArrayList<>();
      ScoreKeeper[] ska = _validation_metrics != null ? _scored_valid : _scored_train;
      for( ScoreKeeper sk : ska )
        if (!sk.isEmpty())
          skl.add(sk);
      return skl.toArray(new ScoreKeeper[skl.size()]);
    }
    /** Training time */
    public long[/*ntrees+1*/] _training_time_ms = {System.currentTimeMillis()};

    /**
     * Variable importances computed during training
     */
    public TwoDimTable _variable_importances;
    public VarImp _varimp;

    public GLMModel _calib_model;

    public SharedTreeOutput( SharedTree b) {
      super(b);
      _ntrees = 0;              // No trees yet
      _treeKeys = new Key[_ntrees][]; // No tree keys yet
      _treeKeysAux = new Key[_ntrees][]; // No tree keys yet
      _treeStats = new TreeStats();
      _scored_train = new ScoreKeeper[]{new ScoreKeeper(Double.NaN)};
      _scored_valid = new ScoreKeeper[]{new ScoreKeeper(Double.NaN)};
      _modelClassDist = _priorClassDist;
    }

    // Append next set of K trees
    public void addKTrees( DTree[] trees) {
      // DEBUG: Print the generated K trees
      //SharedTree.printGenerateTrees(trees);
      assert nclasses()==trees.length;
      // Compress trees and record tree-keys
      _treeKeys = Arrays.copyOf(_treeKeys ,_ntrees+1);
      _treeKeysAux = Arrays.copyOf(_treeKeysAux ,_ntrees+1);
      Key[] keys = _treeKeys[_ntrees] = new Key[trees.length];
      Key[] keysAux = _treeKeysAux[_ntrees] = new Key[trees.length];
      Futures fs = new Futures();
      for( int i=0; i<nclasses(); i++ ) if( trees[i] != null ) {
        CompressedTree ct = trees[i].compress(_ntrees,i,_domains);
        DKV.put(keys[i]=ct._key,ct,fs);
        _treeStats.updateBy(trees[i]); // Update tree shape stats

        CompressedTree ctAux = new CompressedTree(trees[i]._abAux.buf(),-1,-1,-1);
        keysAux[i] = ctAux._key = Key.make(createAuxKey(ct._key.toString()));
        DKV.put(ctAux);
      }
      _ntrees++;
      // 1-based for errors; _scored_train[0] is for zero trees, not 1 tree
      _scored_train = ArrayUtils.copyAndFillOf(_scored_train, _ntrees+1, new ScoreKeeper());
      _scored_valid = _scored_valid != null ? ArrayUtils.copyAndFillOf(_scored_valid, _ntrees+1, new ScoreKeeper()) : null;
      _training_time_ms = ArrayUtils.copyAndFillOf(_training_time_ms, _ntrees+1, System.currentTimeMillis());
      fs.blockForPending();
    }

    public CompressedTree ctree( int tnum, int knum ) { return _treeKeys[tnum][knum].get(); }
    public String toStringTree ( int tnum, int knum ) { return ctree(tnum,knum).toString(this); }
  }

  public SharedTreeModel(Key<M> selfKey, P parms, O output) {
    super(selfKey, parms, output);
  }


  protected String[] makeAllTreeColumnNames() {
    int classTrees = 0;
    for (int i = 0; i < _output._treeKeys[0].length; ++i) {
      if (_output._treeKeys[0][i] != null) classTrees++;
    }
    final int outputcols = _output._treeKeys.length * classTrees;
    final String[] names = new String[outputcols];
    int col = 0;
    for (int tidx = 0; tidx < _output._treeKeys.length; tidx++) {
      Key[] keys = _output._treeKeys[tidx];
      for (int c = 0; c < keys.length; c++) {
        if (keys[c] != null) {
          names[col++] = "T" + (tidx + 1) + (keys.length == 1 ? "" : (".C" + (c + 1)));
        }
      }
    }
    return names;
  }

  @Override
  public Frame scoreLeafNodeAssignment(Frame frame, LeafNodeAssignmentType type, Key<Frame> destination_key) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);

    final String[] names = makeAllTreeColumnNames();
    AssignLeafNodeTaskBase task = AssignLeafNodeTaskBase.make(_output, type);
    return task.execute(adaptFrm, names, destination_key);
  }

  public static class BufStringDecisionPathTracker implements SharedTreeMojoModel.DecisionPathTracker<BufferedString> {
    private final byte[] _buf = new byte[64];
    private final BufferedString _bs = new BufferedString(_buf, 0, 0);
    private int _pos = 0;
    @Override
    public boolean go(int depth, boolean right) {
      _buf[depth] = right ? (byte) 'R' : (byte) 'L';
      if (right) _pos = depth;
      return true;
    }
    @Override
    public BufferedString terminate() {
      _bs.setLen(_pos);
      _pos = 0;
      return _bs;
    }
  }

  private static abstract class AssignLeafNodeTaskBase extends MRTask<AssignLeafNodeTaskBase> {
    final Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;
    final String _domains[][];

    AssignLeafNodeTaskBase(SharedTreeOutput output) {
      _treeKeys = output._treeKeys;
      _domains = output._domains;
    }

    protected abstract void initMap();

    protected abstract void assignNode(final int tidx, final int cls, final CompressedTree tree, final double[] input,
                                       final NewChunk out);

    @Override
    public void map(Chunk chks[], NewChunk[] idx) {
      double[] input = new double[chks.length];

      initMap();
      for (int row = 0; row < chks[0]._len; row++) {
        for (int i = 0; i < chks.length; i++)
          input[i] = chks[i].atd(row);

        int col = 0;
        for (int tidx = 0; tidx < _treeKeys.length; tidx++) {
          Key[] keys = _treeKeys[tidx];
          for (int cls = 0; cls < keys.length; cls++) {
            Key key = keys[cls];
            if (key != null) {
              CompressedTree tree = DKV.get(key).get();
              assignNode(tidx, cls, tree, input, idx[col++]);
            }
          }
        }
        assert (col == idx.length);
      }
    }

    protected abstract Frame execute(Frame adaptFrm, String[] names, Key<Frame> destKey);

    private static AssignLeafNodeTaskBase make(SharedTreeOutput modelOutput, LeafNodeAssignmentType type) {
      switch (type) {
        case Path:
          return new AssignTreePathTask(modelOutput);
        case Node_ID:
          return new AssignLeafNodeIdTask(modelOutput);
        default:
          throw new UnsupportedOperationException("Unknown leaf node assignment type: " + type);
      }
    }
  }

  private static class AssignTreePathTask extends AssignLeafNodeTaskBase {
    private transient BufStringDecisionPathTracker _tr;

    private AssignTreePathTask(SharedTreeOutput output) {
      super(output);
    }

    @Override
    protected void initMap() {
      _tr = new BufStringDecisionPathTracker();
    }

    @Override
    protected void assignNode(int tidx, int cls, CompressedTree tree, double[] input, NewChunk out) {
      BufferedString pred = tree.getDecisionPath(input, _domains, _tr);
      out.addStr(pred);
    }

    @Override
    protected Frame execute(Frame adaptFrm, String[] names, Key<Frame> destKey) {
      Frame res = doAll(names.length, Vec.T_STR, adaptFrm).outputFrame(destKey, names, null);
      // convert to categorical
      Vec vv;
      Vec[] nvecs = new Vec[res.vecs().length];
      for(int c=0;c<res.vecs().length;++c) {
        vv = res.vec(c);
        try {
          nvecs[c] = vv.toCategoricalVec();
        } catch (Exception e) {
          VecUtils.deleteVecs(nvecs, c);
          throw e;
        }
      }
      res.delete();
      res = new Frame(destKey, names, nvecs);
      DKV.put(res);
      return res;
    }
  }

  private static class AssignLeafNodeIdTask extends AssignLeafNodeTaskBase {
    private final Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _auxTreeKeys;
    private final int _nclasses;
    private transient BufStringDecisionPathTracker _tr;

    private AssignLeafNodeIdTask(SharedTreeOutput output) {
      super(output);
      _auxTreeKeys = output._treeKeysAux;
      _nclasses = output.nclasses();
    }

    @Override
    protected void initMap() {
      _tr = new BufStringDecisionPathTracker();
    }

    @Override
    protected void assignNode(int tidx, int cls, CompressedTree tree, double[] input, NewChunk out) {
      CompressedTree auxTree = _auxTreeKeys[tidx][cls].get();
      assert auxTree != null;

      final double d = SharedTreeMojoModel.scoreTree(tree._bits, input, true, _domains);
      final int nodeId = SharedTreeMojoModel.getLeafNodeId(d, auxTree._bits);

      out.addNum(nodeId, 0);
    }

    @Override
    protected Frame execute(Frame adaptFrm, String[] names, Key<Frame> destKey) {
      return doAll(names.length, Vec.T_NUM, adaptFrm).outputFrame(destKey, names, null);
    }
  }

  @Override
  protected Frame postProcessPredictions(Frame adaptedFrame, Frame predictFr, Job j) {
    if (_output._calib_model == null)
      return predictFr;
    if (_output.getModelCategory() == Binomial) {
      Key<Job> jobKey = j != null ? j._key : null;
      Key<Frame> calibInputKey = Key.make();
      Frame calibOutput = null;
      try {
        Frame calibInput = new Frame(calibInputKey, new String[]{"p"}, new Vec[]{predictFr.vec(1)});
        calibOutput = _output._calib_model.score(calibInput);
        assert calibOutput._names.length == 3;
        Vec[] calPredictions = calibOutput.remove(new int[]{1, 2});
        // append calibrated probabilities to the prediction frame
        predictFr.write_lock(jobKey);
        for (int i = 0; i < calPredictions.length; i++)
          predictFr.add("cal_" + predictFr.name(1 + i), calPredictions[i]);
        return predictFr.update(jobKey);
      } finally {
        predictFr.unlock(jobKey);
        DKV.remove(calibInputKey);
        if (calibOutput != null)
          calibOutput.remove();
      }
    } else
      throw H2O.unimpl("Calibration is only supported for binomial models");
  }

  protected double[] score0Incremental(Score.ScoreIncInfo sii, Chunk chks[], double offset, int row_in_chunk, double[] tmp, double[] preds) {
    return score0(chks, offset, row_in_chunk, tmp, preds); // by default delegate to non-incremental implementation
  }

  @Override protected double[] score0(double[] data, double[] preds, double offset) {
    return score0(data, preds, offset, _output._treeKeys.length);
  }
  @Override protected double[] score0(double[/*ncols*/] data, double[/*nclasses+1*/] preds) {
    return score0(data, preds, 0.0);
  }

  protected double[] score0(double[] data, double[] preds, double offset, int ntrees) {
    Arrays.fill(preds,0);
    return score0(data, preds, offset, 0, ntrees);
  }

  protected double[] score0(double[] data, double[] preds, double offset, int startTree, int ntrees) {
    // Prefetch trees into the local cache if it is necessary
    // Invoke scoring
    for( int tidx=startTree; tidx<ntrees; tidx++ )
      score0(data, preds, tidx);
    return preds;
  }

  // Score per line per tree
  private void score0(double[] data, double[] preds, int treeIdx) {
    Key[] keys = _output._treeKeys[treeIdx];
    for( int c=0; c<keys.length; c++ ) {
      if (keys[c] != null) {
        double pred = DKV.get(keys[c]).<CompressedTree>get().score(data,_output._domains);
        assert (!Double.isInfinite(pred));
        preds[keys.length == 1 ? 0 : c + 1] += pred;
      }
    }
  }

  /** Performs deep clone of given model.  */
  protected M deepClone(Key<M> result) {
    M newModel = IcedUtils.deepCopy(self());
    newModel._key = result;
    // Do not clone model metrics
    newModel._output.clearModelMetrics();
    newModel._output._training_metrics = null;
    newModel._output._validation_metrics = null;
    // Clone trees
    Key[][] treeKeys = newModel._output._treeKeys;
    for (int i = 0; i < treeKeys.length; i++) {
      for (int j = 0; j < treeKeys[i].length; j++) {
        if (treeKeys[i][j] == null) continue;
        CompressedTree ct = DKV.get(treeKeys[i][j]).get();
        CompressedTree newCt = IcedUtils.deepCopy(ct);
        newCt._key = CompressedTree.makeTreeKey(i, j);
        DKV.put(treeKeys[i][j] = newCt._key,newCt);
      }
    }
    // Clone Aux info
    Key[][] treeKeysAux = newModel._output._treeKeysAux;
    if (treeKeysAux!=null) {
      for (int i = 0; i < treeKeysAux.length; i++) {
        for (int j = 0; j < treeKeysAux[i].length; j++) {
          if (treeKeysAux[i][j] == null) continue;
          CompressedTree ct = DKV.get(treeKeysAux[i][j]).get();
          CompressedTree newCt = IcedUtils.deepCopy(ct);
          newCt._key = Key.make(createAuxKey(treeKeys[i][j].toString()));
          DKV.put(treeKeysAux[i][j] = newCt._key,newCt);
        }
      }
    }
    return newModel;
  }

  @Override protected Futures remove_impl( Futures fs ) {
    for (Key[] ks : _output._treeKeys)
      for (Key k : ks)
        if( k != null ) k.remove(fs);
    for (Key[] ks : _output._treeKeysAux)
      for (Key k : ks)
        if( k != null ) k.remove(fs);
    if (_output._calib_model != null)
      _output._calib_model.remove(fs);
    return super.remove_impl(fs);
  }

  /** Write out K/V pairs */
  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    for (Key<CompressedTree>[] ks : _output._treeKeys)
      for (Key<CompressedTree> k : ks)
        ab.putKey(k);
    for (Key<CompressedTree>[] ks : _output._treeKeysAux)
      for (Key<CompressedTree> k : ks)
        ab.putKey(k);
    return super.writeAll_impl(ab);
  }

  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    for (Key<CompressedTree>[] ks : _output._treeKeys)
      for (Key<CompressedTree> k : ks)
        ab.getKey(k,fs);
    for (Key<CompressedTree>[] ks : _output._treeKeysAux)
      for (Key<CompressedTree> k : ks)
        ab.getKey(k,fs);
    return super.readAll_impl(ab,fs);
  }

  @SuppressWarnings("unchecked")  // `M` is really the type of `this`
  private M self() { return (M)this; }

  /**
   * Converts a given tree of the ensemble to a user-understandable representation.
   * @param tidx tree index
   * @param cls tree class
   * @return instance of SharedTreeSubgraph
   */
  public SharedTreeSubgraph getSharedTreeSubgraph(final int tidx, final int cls) {
    if (tidx < 0 || tidx >= _output._ntrees) {
      throw new IllegalArgumentException("Invalid tree index: " + tidx +
              ". Tree index must be in range [0, " + (_output._ntrees -1) + "].");
    }
    final CompressedTree auxCompressedTree = _output._treeKeysAux[tidx][cls].get();
    return _output._treeKeys[tidx][cls].get().toSharedTreeSubgraph(auxCompressedTree, _output._names, _output._domains);
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Serialization into a POJO
  //--------------------------------------------------------------------------------------------------------------------

  // Override in subclasses to provide some top-level model-specific goodness
  @Override protected boolean toJavaCheckTooBig() {
    // If the number of leaves in a forest is more than N, don't try to render it in the browser as POJO code.
    return _output==null || _output._treeStats._num_trees * _output._treeStats._mean_leaves > 1000000;
  }

  protected boolean binomialOpt() { return true; }

  @Override protected SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    if (_parms._categorical_encoding != Parameters.CategoricalEncodingScheme.AUTO &&
        _parms._categorical_encoding != Parameters.CategoricalEncodingScheme.Enum) {
      throw new IllegalArgumentException("Only default categorical_encoding scheme is supported for POJO/MOJO");
    }
    sb.nl();
    sb.ip("public boolean isSupervised() { return true; }").nl();
    sb.ip("public int nfeatures() { return " + _output.nfeatures() + "; }").nl();
    sb.ip("public int nclasses() { return " + _output.nclasses() + "; }").nl();
    return sb;
  }

  @Override protected void toJavaPredictBody(SBPrintStream body,
                                             CodeGeneratorPipeline classCtx,
                                             CodeGeneratorPipeline fileCtx,
                                             final boolean verboseCode) {
    final int nclass = _output.nclasses();
    body.ip("java.util.Arrays.fill(preds,0);").nl();
    final String mname = JCodeGen.toJavaId(_key.toString());

    // One forest-per-GBM-tree, with a real-tree-per-class
    for (int t=0; t < _output._treeKeys.length; t++) {
      // Generate score method for given tree
      toJavaForestName(body.i(),mname,t).p(".score0(data,preds);").nl();

      final int treeIdx = t;

      fileCtx.add(new CodeGenerator() {
        @Override
        public void generate(JCodeSB out) {
          try {
            // Generate a class implementing a tree
            out.nl();
            toJavaForestName(out.ip("class "), mname, treeIdx).p(" {").nl().ii(1);
            out.ip("public static void score0(double[] fdata, double[] preds) {").nl().ii(1);
            for (int c = 0; c < nclass; c++) {
              if (_output._treeKeys[treeIdx][c] == null) continue;
              if (!(binomialOpt() && c == 1 && nclass == 2)) // Binomial optimization
                toJavaTreeName(out.ip("preds[").p(nclass == 1 ? 0 : c + 1).p("] += "), mname, treeIdx, c).p(".score0(fdata);").nl();
            }
            out.di(1).ip("}").nl(); // end of function
            out.di(1).ip("}").nl(); // end of forest class

            // Generate the pre-tree classes afterwards
            for (int c = 0; c < nclass; c++) {
              if (_output._treeKeys[treeIdx][c] == null) continue;
              if (!(binomialOpt() && c == 1 && nclass == 2)) { // Binomial optimization
                String javaClassName = toJavaTreeName(new SB(), mname, treeIdx, c).toString();
                CompressedTree ct = _output.ctree(treeIdx, c);
                SB sb = new SB();
                new TreeJCodeGen(SharedTreeModel.this, ct, sb, javaClassName, verboseCode).generate();
                out.p(sb);
              }
            }
          } catch (Throwable t) {
            t.printStackTrace();
            throw new IllegalArgumentException("Internal error creating the POJO.", t);
          }
        }
      });
    }

    toJavaUnifyPreds(body);
  }

  protected abstract void toJavaUnifyPreds(SBPrintStream body);

  protected <T extends JCodeSB> T toJavaTreeName(T sb, String mname, int t, int c ) {
    return (T) sb.p(mname).p("_Tree_").p(t).p("_class_").p(c);
  }

  protected <T extends JCodeSB> T toJavaForestName(T sb, String mname, int t ) {
    return (T) sb.p(mname).p("_Forest_").p(t);
  }

}
