package hex.tree;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import hex.Distribution;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import hex.ModelMetricsMultinomial;
import hex.ModelMetricsRegression;
import hex.ScoreKeeper;
import hex.VarImp;
import water.DKV;
import water.Futures;
import water.H2O;
import water.Key;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.JCodeSB;
import water.util.ArrayUtils;
import water.util.JCodeGen;
import water.util.PojoUtils;
import water.util.RandomUtils;
import water.util.SB;
import water.util.SBPrintStream;
import water.util.TwoDimTable;

public abstract class SharedTreeModel<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends Model<M,P,O> {

  public abstract static class SharedTreeParameters extends Model.Parameters {

    public int _ntrees=50; // Number of trees in the final model. Grid Search, comma sep values:50,100,150,200

    public int _max_depth = 5; // Maximum tree depth. Grid Search, comma sep values:5,7

    public double _min_rows = 10; // Fewest allowed observations in a leaf (in R called 'nodesize'). Grid Search, comma sep values

    public int _nbins = 20; // Numerical (real/int) cols: Build a histogram of this many bins, then split at the best point

    public int _nbins_cats = 1024; // Categorical (factor) cols: Build a histogram of this many bins, then split at the best point

    public double _r2_stopping = 0.999999; // Stop when the r^2 metric equals or exceeds this value

    public long _seed = RandomUtils.getRNG(System.nanoTime()).nextLong();

    public int _nbins_top_level = 1<<10; //hardcoded maximum top-level number of bins for real-valued columns

    public boolean _build_tree_one_node = false;

    public int _initial_score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring the first  4 secs

    public int _score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring each iteration every 4 secs

    public float _sample_rate = 0.632f; //fraction of rows to sample for each tree

    /** Fields which can NOT be modified if checkpoint is specified.
     * FIXME: should be defined in Schema API annotation
     */
    private static String[] CHECKPOINT_NON_MODIFIABLE_FIELDS = new String[] { "_build_tree_one_node", "_sample_rate", "_max_depth", "_min_rows", "_nbins", "_nbins_cats", "_nbins_top_level"};

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

  @Override
  public double deviance(double w, double y, double f) {
    return new Distribution(_parms._distribution, _parms._tweedie_power).deviance(w, y, f);
  }

  final public VarImp varImp() { return _output._varimp; }

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
    final public TreeStats _treeStats;

    /** Trees get big, so store each one seperately in the DKV. */
    public Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;

    public ScoreKeeper _scored_train[/*ntrees+1*/];
    public ScoreKeeper _scored_valid[/*ntrees+1*/];
    public ScoreKeeper[] scoreKeepers() {
      List<ScoreKeeper> sk = new ArrayList<>();
      ScoreKeeper[] ska = _validation_metrics != null ? _scored_valid : _scored_train;
      for (int i=0;i<ska.length;++i) {
        if (!ska[i].isEmpty())
          sk.add(ska[i]);
      }
      return sk.toArray(new ScoreKeeper[0]);
    }
    /** Training time */
    public long _training_time_ms[/*ntrees+1*/] = new long[]{System.currentTimeMillis()};

    /**
     * Variable importances computed during training
     */
    public TwoDimTable _variable_importances;
    public VarImp _varimp;

    public SharedTreeOutput( SharedTree b, double mse_train, double mse_valid ) {
      super(b);
      _ntrees = 0;              // No trees yet
      _treeKeys = new Key[_ntrees][]; // No tree keys yet
      _treeStats = new TreeStats();
      _scored_train = new ScoreKeeper[]{new ScoreKeeper(mse_train)};
      _scored_valid = new ScoreKeeper[]{new ScoreKeeper(mse_valid)};
      _modelClassDist = _priorClassDist;
    }

    // Append next set of K trees
    public void addKTrees( DTree[] trees) {
      // DEBUG: Print the generated K trees
      //SharedTree.printGenerateTrees(trees);
      assert nclasses()==trees.length;
      // Compress trees and record tree-keys
      _treeKeys = Arrays.copyOf(_treeKeys ,_ntrees+1);
      Key[] keys = _treeKeys[_ntrees] = new Key[trees.length];
      Futures fs = new Futures();
      for( int i=0; i<nclasses(); i++ ) if( trees[i] != null ) {
        CompressedTree ct = trees[i].compress(_ntrees,i);
        DKV.put(keys[i]=ct._key,ct,fs);
        _treeStats.updateBy(trees[i]); // Update tree shape stats
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

  public SharedTreeModel(Key selfKey, P parms, O output) { super(selfKey,parms,output); }

  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    return score0(data, preds, 1.0, 0.0);
  }
  @Override
  protected double[] score0(double[] data, double[] preds, double weight, double offset) {
    // Prefetch trees into the local cache if it is necessary
    // Invoke scoring
    Arrays.fill(preds,0);
    for( int tidx=0; tidx<_output._treeKeys.length; tidx++ )
      score0(data, preds, tidx);
    return preds;
  }
  // Score per line per tree
  private void score0(double data[], double preds[], int treeIdx) {
    Key[] keys = _output._treeKeys[treeIdx];
    for( int c=0; c<keys.length; c++ ) {
      if (keys[c] != null) {
        double pred = DKV.get(keys[c]).<CompressedTree>get().score(data);
        assert (!Double.isInfinite(pred));
        preds[keys.length == 1 ? 0 : c + 1] += pred;
      }
    }
  }

  @Override protected Futures remove_impl( Futures fs ) {
    for( Key ks[] : _output._treeKeys)
      for( Key k : ks )
        if( k != null ) k.remove(fs);
    return super.remove_impl(fs);
  }

  // Override in subclasses to provide some top-level model-specific goodness
  @Override protected boolean toJavaCheckTooBig() {
    // If the number of leaves in a forest is more than N, don't try to render it in the browser as POJO code.
    return _output==null || _output._treeStats._num_trees * _output._treeStats._mean_leaves > 1000000;
  }
  protected boolean binomialOpt() { return true; }
  @Override protected SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    sb.nl();
    sb.ip("public boolean isSupervised() { return true; }").nl();
    sb.ip("public int nfeatures() { return "+_output.nfeatures()+"; }").nl();
    sb.ip("public int nclasses() { return "+_output.nclasses()+"; }").nl();
    return sb;
  }
  @Override protected void toJavaPredictBody(SBPrintStream body,
                                             CodeGeneratorPipeline classCtx,
                                             CodeGeneratorPipeline fileCtx,
                                             final boolean verboseCode) {
    final int nclass = _output.nclasses();
    body.ip("java.util.Arrays.fill(preds,0);").nl();
    body.ip("double[] fdata = hex.genmodel.GenModel.SharedTree_clean(data);").nl();
    final String mname = JCodeGen.toJavaId(_key.toString());

    // One forest-per-GBM-tree, with a real-tree-per-class
    for (int t=0; t < _output._treeKeys.length; t++) {
      // Generate score method for given tree
      toJavaForestName(body.i(),mname,t).p(".score0(fdata,preds);").nl();

      final int treeIdx = t;

      fileCtx.add(new CodeGenerator() {
        @Override
        public void generate(JCodeSB out) {
          // Generate a class implementing a tree
          out.nl();
          toJavaForestName(out.ip("class "), mname, treeIdx).p(" {").nl().ii(1);
          out.ip("public static void score0(double[] fdata, double[] preds) {").nl().ii(1);
          for( int c=0; c<nclass; c++ )
            if( !binomialOpt() || !(c==1 && nclass==2) ) // Binomial optimization
              toJavaTreeName(out.ip("preds[").p(nclass==1?0:c+1).p("] += "), mname, treeIdx, c).p(".score0(fdata);").nl();
          out.di(1).ip("}").nl(); // end of function
          out.di(1).ip("}").nl(); // end of forest class

          // Generate the pre-tree classes afterwards
          for (int c = 0; c < nclass; c++) {
            if( !binomialOpt() || !(c==1 && nclass==2) ) { // Binomial optimization
              String javaClassName = toJavaTreeName(new SB(), mname, treeIdx, c).toString();
              CompressedTree ct = _output.ctree(treeIdx, c);
              SB sb = new SB();
              new TreeJCodeGen(SharedTreeModel.this, ct, sb, javaClassName, verboseCode).generate();
              out.p(sb);
            }
          }
        }
      });
    }

    toJavaUnifyPreds(body);
  }
  abstract protected void toJavaUnifyPreds( SBPrintStream body);

  protected <T extends JCodeSB> T toJavaTreeName(final T sb, String mname, int t, int c ) {
    return (T) sb.p(mname).p("_Tree_").p(t).p("_class_").p(c);
  }
  protected <T extends JCodeSB> T toJavaForestName(final T sb, String mname, int t ) {
    return (T) sb.p(mname).p("_Forest_").p(t);
  }

  @Override
  public List<Key> getPublishedKeys() {
    assert _output._ntrees == _output._treeKeys.length :
            "Tree model is inconsistent: number of trees do not match number of tree keys!";
    List<Key> superP = super.getPublishedKeys();
    List<Key> p = new ArrayList<Key>(_output._ntrees * _output.nclasses());
    for (int i = 0; i < _output._treeKeys.length; i++) {
      for (int j = 0; j < _output._treeKeys[i].length; j++) {
        p.add(_output._treeKeys[i][j]);
      }
    }
    p.addAll(superP);
    return p;
  }
}
