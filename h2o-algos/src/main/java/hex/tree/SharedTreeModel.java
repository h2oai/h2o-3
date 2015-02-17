package hex.tree;

import hex.*;
import water.DKV;
import water.Futures;
import water.H2O;
import water.Key;
import water.util.ArrayUtils;
import water.util.ModelUtils;
import water.util.TwoDimTable;

import java.util.Arrays;

public abstract class SharedTreeModel<M extends SharedTreeModel<M,P,O>, P extends SharedTreeModel.SharedTreeParameters, O extends SharedTreeModel.SharedTreeOutput> extends SupervisedModel<M,P,O> {

  public abstract static class SharedTreeParameters extends SupervisedModel.SupervisedParameters {
    /** Maximal number of supported levels in response. */
    static final int MAX_SUPPORTED_LEVELS = 1000;

    public int _ntrees=50; // Number of trees in the final model. Grid Search, comma sep values:50,100,150,200

    public int _max_depth = 5; // Maximum tree depth. Grid Search, comma sep values:5,7

    public int _min_rows = 10; // Fewest allowed observations in a leaf (in R called 'nodesize'). Grid Search, comma sep values

    public int _nbins = 20; // Build a histogram of this many bins, then split at the best point

    public boolean _score_each_iteration;

    public long _seed;          // Seed for psuedo-random redistribution

    // TRUE: Continue extending an existing checkpointed model
    // FALSE: Overwrite any prior model
    public boolean _checkpoint;
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain, ModelUtils.DEFAULT_THRESHOLDS);
      case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(domain);
      case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
      default: throw H2O.unimpl();
    }
  }

  public abstract static class SharedTreeOutput extends SupervisedModel.SupervisedOutput {

    /** Initially predicted value (for zero trees) */
    public double _initialPrediction;

    /** Number of trees actually in the model (as opposed to requested) */
    public int _ntrees;

    /** More indepth tree stats */
    final TreeStats _treeStats;

    /** Trees get big, so store each one seperately in the DKV. */
    public Key<CompressedTree>[/*_ntrees*/][/*_nclass*/] _treeKeys;

    /** Train and validation errors per-tree (scored).  Zero index is the no-tree
     *  error, guessing only the class distribution.  Not all trees are
     *  scored, NaN represents trees not scored. */
    public double _mse_train[/*_ntrees+1*/];
    public double _mse_valid[/*_ntrees+1*/];

    /** Variable Importance */
    public TwoDimTable _variableImportances;

    public SharedTreeOutput( SharedTree b, double mse_train, double mse_valid ) {
      super(b);
      _ntrees = 0;              // No trees yet
      _treeKeys = new Key[_ntrees][]; // No tree keys yet
      _treeStats = new TreeStats();
      _mse_train = new double[]{mse_train};
      _mse_valid  = Double.isNaN(mse_valid) ? null : new double[]{mse_valid };
    }

    // Append next set of K trees
    public void addKTrees( DTree[] trees) {
      assert nclasses()==trees.length;
      _treeStats.updateBy(trees); // Update tree shape stats
      // Compress trees and record tree-keys
      _treeKeys = Arrays.copyOf(_treeKeys ,_ntrees+1);
      Key[] keys = _treeKeys[_ntrees] = new Key[trees.length];
      Futures fs = new Futures();
      for( int i=0; i<nclasses(); i++ ) if( trees[i] != null ) {
        CompressedTree ct = trees[i].compress(_ntrees,i);
        DKV.put(keys[i]=ct._key,ct,fs);
      }
      _ntrees++;
      // 1-based for errors; _mse_train[0] is for zero trees, not 1 tree
      _mse_train = ArrayUtils.copyAndFillOf(_mse_train, _ntrees+1, Double.NaN);
      if( _mse_valid != null )
        _mse_valid = ArrayUtils.copyAndFillOf(_mse_valid, _ntrees+1, Double.NaN);
      fs.blockForPending();
    }

    public String toStringTree( int tnum, int knum ) {
      return _treeKeys[tnum][knum].get().toString(this);
    }

  }

  public SharedTreeModel(Key selfKey, P parms, O output) { super(selfKey,parms,output); }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    // Prefetch trees into the local cache if it is necessary
    // Invoke scoring
    Arrays.fill(preds,0);
    for( int tidx=0; tidx<_output._treeKeys.length; tidx++ )
      score0(data, preds, tidx);
    return preds;
  }
  // Score per line per tree
  public void score0(double data[], float preds[], int treeIdx) {
    Key[] keys = _output._treeKeys[treeIdx];
    for( int c=0; c<keys.length; c++ )
      if( keys[c] != null )
        preds[keys.length==1?0:c+1] += DKV.get(keys[c]).<CompressedTree>get().score(data);
  }

  // Numeric type used in generated code to hold predicted value between the
  // calls; i.e. the numerical precision of predictions.
  static final String PRED_TYPE = "float";

  @Override protected Futures remove_impl( Futures fs ) {
    for( Key ks[] : _output._treeKeys)
      for( Key k : ks )
        if( k != null ) k.remove(fs);
    return super.remove_impl(fs);
  }
}
