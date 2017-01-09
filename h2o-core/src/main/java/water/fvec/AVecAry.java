package water.fvec;

import water.DKV;
import water.Futures;
import water.Key;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Created by tomas on 1/9/17.
 */
public abstract class AVecAry extends Vec implements Iterable<Vec> {
  protected int [] _vecIds;
  protected transient Vec [] _vecs;

  public AVecAry(Key<Vec> k, int rowLayout, int numCols) {
    super(k,rowLayout,numCols);
  }

  protected AVecAry() {
  }

  boolean checkCompatible( Vec v ) {return isEmpty() || super.checkCompatible(v);}

  public long[] espc() {
    return isEmpty()?null:fetchVec(0).espc();
  }

  private boolean isEmpty() {return _vecIds == null || _vecIds.length == 0;}

  protected void prefetchVec(int i){
    DKV.prefetch(Vec.getVecKeyById(this,_vecIds[i]));
  }
  private Vec getVec(int i){
    Vec v = Vec.getVecKeyById(this,_vecIds[i]).get();
    assert v != null:"missing vec " + getVecKeyById(this,_vecIds[i]);
    return v;
  }

  @Override
  public final Futures startRollupStats(Futures fs) { return startRollupStats(fs,false);}

  @Override
  public final Futures startRollupStats(Futures fs,boolean computeHisto) {
    for(Vec v:vecs()) v.startRollupStats(fs,computeHisto);
    return fs;
  }

  protected final Vec fetchVec(int i){return fetchVecs()[i];}
  protected final Vec[] fetchVecs(){
    if(_vecIds == null) return null;
    Vec [] vecs = _vecs;
    if(_vecs == null) {
      for(int i = 0; i < _vecIds.length; ++i)
        prefetchVec(i);
      vecs = new Vec[_vecIds.length];
      for(int i = 0; i < vecs.length; ++i)
        vecs[i] = getVec(i);
      _vecs = vecs;
    }
    return vecs;
  }

  public abstract VecAry selectRange(int from, int to);

  public abstract VecAry select(int... idxs);

  public abstract VecAry remove(int... idxs);

  public abstract VecAry append(Vec v);

  public abstract void swap(int lo, int hi);

  public abstract void moveFirst(int[] cols);


  public abstract VecAry replace(int col, Vec nv);
  public abstract void insertVec(int i, VecAry x);


  @Override
  public Iterator<Vec> iterator() {
    return new Iterator<Vec>() {
      int _id;
      final int _numCols = numCols();
      @Override
      public boolean hasNext() {return _id < _numCols;}
      @Override
      public Vec next() {
        return select(_id++);
      }
      @Override public void remove(){throw new UnsupportedOperationException();}
    };
  }
}
