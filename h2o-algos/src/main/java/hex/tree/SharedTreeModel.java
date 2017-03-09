package hex.tree;

import hex.*;
import static hex.genmodel.GenModel.createAuxKey;
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

    public double _reg_alpha = 0; //L1 regularization
    public double _reg_lambda = 0; //L2 regularization

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

        CompressedTree ctAux = new CompressedTree(trees[i]._abAux.buf(),-1,-1,-1,-1,_domains);
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

  public Frame scoreLeafNodeAssignment(Frame frame, Key<Frame> destination_key) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);
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
    Frame res = new MRTask() {
      @Override public void map(Chunk chks[], NewChunk[] idx ) {
        double[] input = new double[chks.length];
        String[] output = new String[outputcols];

        for( int row=0; row<chks[0]._len; row++ ) {
          for( int i=0; i<chks.length; i++ )
            input[i] = chks[i].atd(row);

          int col=0;
          for( int tidx=0; tidx<_output._treeKeys.length; tidx++ ) {
            Key[] keys = _output._treeKeys[tidx];
            for (Key key : keys) {
              if (key != null) {
                String pred = DKV.get(key).<CompressedTree>get().getDecisionPath(input);
                output[col++] = pred;
              }
            }
          }
          assert(col==outputcols);
          for (int i=0; i<outputcols; ++i)
            idx[i].addStr(output[i]);
        }
      }
    }.doAll(outputcols, Vec.T_STR, adaptFrm).outputFrame(destination_key, names, null);

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
    res = new Frame(destination_key, names, nvecs);
    DKV.put(res);
    return res;
  }

  @Override protected double[] score0(double[] data, double[] preds, double weight, double offset) {
    return score0(data, preds, weight, offset, _output._treeKeys.length);
  }
  @Override protected double[] score0(double[/*ncols*/] data, double[/*nclasses+1*/] preds) {
    return score0(data, preds, 1.0, 0.0);
  }

  protected double[] score0(double[] data, double[] preds, double weight, double offset, int ntrees) {
    // Prefetch trees into the local cache if it is necessary
    // Invoke scoring
    Arrays.fill(preds,0);
    for( int tidx=0; tidx<ntrees; tidx++ )
      score0(data, preds, tidx);
    return preds;
  }

  // Score per line per tree
  private void score0(double[] data, double[] preds, int treeIdx) {
    Key[] keys = _output._treeKeys[treeIdx];
    for( int c=0; c<keys.length; c++ ) {
      if (keys[c] != null) {
        double pred = DKV.get(keys[c]).<CompressedTree>get().score(data);
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
