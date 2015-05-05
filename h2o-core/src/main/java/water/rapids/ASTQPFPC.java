package water.rapids;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import jsr166y.CountedCompleter;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;

/**
 * Created by spencer on 5/2/15.
 *
 * Quantile Per Feature Per Column
 */
public class ASTQPFPC extends ASTUniPrefixOp {
  int _cidx;  // the class index
  double _probs[];
  // ast => (qpfpc %fr #c.idx (dlist ...))
  @Override String opStr() { return "qpfpc"; }
  @Override ASTOp make() {return new ASTQPFPC(); }
  ASTQPFPC parse_impl(Exec E) {
    AST ary = E.parse();
    _cidx = (int)E.nextDbl();
    AST a = E.parse();
    if( a instanceof ASTDoubleList ) _probs = ((ASTDoubleList)a)._d;
    else if( a instanceof ASTNum )   _probs = new double[]{((ASTNum)a)._d};
    else _probs=null;
    E.eatEnd();

    ASTQPFPC res = (ASTQPFPC)clone();
    res._probs = _probs;
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env e) {
    // For each Vec, subset to class ID and compute quantiles
    // Blow out if Vec is not numeric.
    Frame f = e.popAry();
    Vec classVec = f.vecs()[_cidx];
    for( Vec v: f.vecs() ) {
      if( v==classVec ) continue;
      if(!v.isNumeric() ) throw new IllegalArgumentException("Columns must be numeric!");
    }
    // must unfortunately create the frames -- could be painful data copy move... TODO: make quantile accept conditionals
    final int nclasses = classVec.domain().length;
    long[] selectValues = new long[nclasses];
    for(int i=0;i<selectValues.length;++i) selectValues[i]=i;

    String[] colnames = new String[f.numCols()-1];
    String exName=f._names[_cidx];
    int p=0;
    for( int i=0;i<colnames.length;++i)
      if( !exName.equals(f._names[i])) colnames[p++]=f._names[i];

    Vec[] vecs = new Vec[f.numCols()-1];
    int i=0;
    for( Vec v: f.vecs() ) if(v!=classVec) vecs[i++]=v;
    ParallelSubsetTask t;
    H2O.submitTask(t=new ParallelSubsetTask(vecs, classVec, selectValues, _probs, colnames,classVec.domain())).join();
    final double[][][] dist= t._dist;
    final String[] rownames = ArrayUtils.flat(t._rownames);
    Vec layout = Vec.makeZero(vecs.length*nclasses);
    Vec[] resultVecs = new Vec[1+_probs.length];  // 1 more for the row label
    resultVecs[0]=layout.makeCopy(rownames, Vec.T_ENUM);
    for(int j=1;j<resultVecs.length;++j) resultVecs[j] = layout.makeCopy(null);

    String[] names = new String[1+_probs.length];
    names[0]="rownames";
    for(int j=1;j<names.length;++j)
      names[j] = "X"+_probs[j-1];

    Frame fr2 = new Frame(names, resultVecs);

    new MRTask() {
      @Override public void map(Chunk[] c) {
        long start = c[0].start();
        int i,j;
        for(int r=0;r<c[0]._len;++r) {
          // compute the indices into dist
          i = (int)(r+start)/nclasses;
          j = (int)(r+start)%nclasses;
          c[0].set(r, r+start);
          for(int v=1;v<c.length;++v)
            c[v].set(r, dist[i][j][v-1]);
        }
      }
    }.doAll(fr2);
    layout.remove();
    e.pushAry(fr2);
  }

  private static class SubsetTask extends H2O.H2OCountedCompleter<SubsetTask> {
    final long _selectValue;
    final Frame _fr;
    final Key _key;

    SubsetTask(H2O.H2OCountedCompleter cc, long s, Frame fr, Key key) { super(cc); _selectValue = s; _fr = fr; _key = key; }
    @Override protected void compute2() {
      Frame f = new MRTask() {
        @Override public void map(Chunk[] c, NewChunk nc) {
          for (int i = 0; i < c[0]._len; ++i)
            if (c[1].at8(i) == _selectValue) nc.addNum(c[0].atd(i));
        }
      }.doAll(1, _fr, false/*run_local*/).outputFrame(_key, new String[]{_fr.names()[0]}, null);
      DKV.put(_key,f);
      tryComplete();
    }
  }

  private static class ParallelSubsets extends H2O.H2OCountedCompleter<ParallelSubsets> {
    // runs over each class for the given Vec and creates the subset.
    // TODO: call back to quantile computation and finally return the result up the chain...

    // IN
    final Vec _feature;           // the feature vector
    final Vec _class;             // the class vector
    final long[] _selectValues;   // which value to subset on
    final Key[] _frameKeys;       // array of Keys for the subsetted Frames
    final double _probs[];
    final String[] _classLabels;
    final String _colLabel;

    // OUT
    double[/*classes*/][/*probs*/] _dist; // results for this feature over the set of classes
    String[] _rownames;  // row labels of the form "<class_label>_<column_label>"

    ParallelSubsets(H2O.H2OCountedCompleter cc, Vec f, Vec c, long[] selectValues, Key[] frameKeys, double[] probs, String classLabels[], String colLabel) {
      super(cc); _feature=f; _class=c; _selectValues=selectValues; _frameKeys=frameKeys; _probs=probs; _dist = new double[_selectValues.length][_probs.length];
      _classLabels=classLabels; _colLabel=colLabel; _rownames=new String[_selectValues.length];
    }

    @Override protected void compute2() {
      addToPendingCount(2*_selectValues.length-1);
      int i=0;
      for( long l:_selectValues ) {
        Frame f = new Frame(_feature, _class);
        _rownames[i]=_classLabels[i]+"_"+_colLabel;
        new SubsetTask(new QuantileCallback(i), l, f, _frameKeys[i++]).fork();
      }
    }

    private class QuantileCallback extends H2O.H2OCallback {
      int _i;
      Quantile.QTask _q;
      public QuantileCallback(int i) { super(ParallelSubsets.this); _i=i; }
      @Override public void callback(H2O.H2OCountedCompleter cc) {
        // launch Quantile task on ith _frameKey
        QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
        parms._probs = _probs;
        parms._train = _frameKeys[_i];
        _q = new Quantile.QTask(new QuantileCallback2(_i,this), _probs, (Frame)DKV.getGet(_frameKeys[_i]), QuantileModel.CombineMethod.INTERPOLATE);
        _q.fork();
      }
    }

    private class QuantileCallback2 extends H2O.H2OCallback {
      int _i;
      QuantileCallback _q;
      public QuantileCallback2(int i, QuantileCallback q) { super(ParallelSubsets.this); _i = i; _q=q; }
      @Override public void callback(H2O.H2OCountedCompleter cc) {
        _dist[_i] = _q._q._quantiles[0];
        Keyed.remove(_frameKeys[_i]);  // toss the subset frame
      }
    }
  }

  private static class ParallelSubsetTask extends H2O.H2OCountedCompleter<ParallelSubsetTask> {
    // launch a SubsetMRTask for each <Feature, ClassLabel> pair.
    // On callback, toss into the Quantile computation

    // IN
    final Vec[] _features;
    final Vec   _classes;
    final long[] _selectValues;
    final double[] _probs;
    final String[] _colLabels;
    final String[] _classLabels;

    // OUT:
    double[/*features*/][/*classes*/][/*probs*/] _dist; // limit length of probs to at most 100 so that results can easily fit into a long[][][], with which a Frame will be made. Should be fine for most cases (5000 features, 500 classes ~ 2GBs); for 100 features, 20 classes ~ 16 MBs
    String[][] _rownames;
    Key[] _fkeys;
    ParallelSubsets[] _ps;

    ParallelSubsetTask(Vec[] features, Vec classes, long[] selectValues, double[] probs, String[] colLabels, String[] classLabels) {
      _features=features; _classes=classes; _selectValues=selectValues; _probs=probs;
      _dist=new double[features.length][][]; _colLabels=colLabels; _classLabels=classLabels; _rownames=new String[features.length][];
    }

    @Override protected void compute2() {
      addToPendingCount(_features.length-1);
      // loop over Vecs
      _ps=new ParallelSubsets[_features.length];
      Key[][] fkeys = new Key[_features.length][_selectValues.length];
      for(int i=0;i<fkeys.length;++i)
        for(int j=0;j<_selectValues.length;j++)
          fkeys[i][j]=Key.make();

      _fkeys = ArrayUtils.flat(fkeys);
      for(int i=0;i<_features.length;++i)
        (_ps[i]=new ParallelSubsets(new Callback(i), _features[i], _classes, _selectValues, fkeys[i],_probs, _classLabels, _colLabels[i])).fork();
    }

    @Override public void onCompletion(CountedCompleter cc) {
      for(Key k:_fkeys) Keyed.remove(k);
      _fkeys=null;
    }

    private class Callback extends H2O.H2OCallback {
      int _i;
      public Callback(int i){super(ParallelSubsetTask.this); _i=i; }
      @Override public void callback(H2O.H2OCountedCompleter h2OCountedCompleter) {
        _dist[_i]=_ps[_i]._dist;
        _rownames[_i]=_ps[_i]._rownames;
      }
    }
  }
}
