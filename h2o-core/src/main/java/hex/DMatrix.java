package hex;

import water.*;
import water.H2O.FJWThr;
import water.H2O.H2OCallback;
import water.H2O.H2OCountedCompleter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.NewChunk.Value;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by tomasnykodym on 11/13/14.
 *
 * Distributed matrix operations such as (sparse) multiplication and transpose.
 */
public class DMatrix  {

  /**
   * Transpose the Frame as if it was a matrix (i.e. rows become coumns).
   * Must be all numeric, currently will fail if there are too many rows ( >= ~.5M).
   * Result will be put into a new Vectro Group and will be balanced so that each vec will have
   * (4*num cpus in the cluster) chunks.
   *
   * @param src
   * @return
   */
  public static Frame transpose(Frame src){
    if(src.numRows() != (int)src.numRows())
      throw H2O.unimpl();
    int nchunks = Math.max(1,src.numCols()/10000);
    long [] espc = new long[nchunks+1];
    int rpc = (src.numCols() / nchunks);
    int rem = (src.numCols() % nchunks);
    Arrays.fill(espc, rpc);
    for (int i = 0; i < rem; ++i) ++espc[i];
    long sum = 0;
    for (int i = 0; i < espc.length; ++i) {
      long s = espc[i];
      espc[i] = sum;
      sum += s;
    }
    return transpose(src, new Frame(new Vec(Vec.newKey(),espc).makeZeros((int)src.numRows())));
  }

  /**
   * Transpose the Frame as if it was a matrix (rows <-> columns).
   * Must be all numeric, will fail if there are too many rows ( >= ~.5M).
   *
   * Result is made to be compatible (i.e. the same vector group and chunking) with the target frame.
   *
   * @param src
   * @return
   */
  public static Frame transpose(Frame src, Frame tgt){
    if(src.numRows() != tgt.numCols() || src.numCols() != tgt.numRows())
      throw new IllegalArgumentException("dimension do not match!");
    for(Vec v:src.vecs()) {
      if (v.isCategorical())
        throw new IllegalArgumentException("transpose can only be applied to all-numeric frames (representing a matrix)");
      if(v.length() > 1000000)
        throw new IllegalArgumentException("too many rows, transpose only works for frames with < 1M rows.");
    }
    new TransposeTsk(tgt).doAll(src);
    return tgt;
  }

  /**
   * (MR)Task performing the matrix transpose.
   * It is to be applied to the source frame.
   * Target frame must be created up front (e.g. via Vec.makeZeros() call)
   * and passed in as an argument.
   *
   * Task will utilize sparsity and will preserve compression if possible
   * (compression may differ because of switching from column compressed to row-compressed form)
   */
  public static class TransposeTsk extends MRTask<TransposeTsk> {
    final Frame _tgt; // Target dataset, should be created up front, e.g. via Vec.makeZeros(n) call.
    public TransposeTsk(Frame tgt){ _tgt = tgt;}
    public void map(final Chunk[] chks) {
      final Frame tgt = _tgt;
      final long [] espc = tgt.anyVec()._espc;
      final int colStart = (int)chks[0].start();
//      addToPendingCount(espc.length - 2);
      for (int i = 0; i < espc.length - 1; ++i) {
        final int fi = i;
//        new CountedCompleter(this) {
//          @Override
//          public void compute() {
        final NewChunk[] tgtChunks = new NewChunk[chks[0]._len];
        for (int j = 0; j < tgtChunks.length; ++j)
          tgtChunks[j] = new NewChunk(tgt.vec(j + colStart), fi);
        for (int c = ((int) espc[fi]); c < (int) espc[fi + 1]; ++c) {
          NewChunk nc = chks[c].inflate();
          Iterator<Value> it = nc.values();
          while (it.hasNext()) {
            Value v = it.next();
            NewChunk t = tgtChunks[v.rowId0()];
            t.addZeros(c - (int) espc[fi] - t.len());
            v.add2Chunk(t);
          }
        }
//            addToPendingCount(tgtChunks.length - 1);
        for (int j = 0; j < tgtChunks.length; ++j) { // finalize the target chunks and close them
          final int fj = j;
//              new CountedCompleter(this) {
//                @Override
//                public void compute() {
          tgtChunks[fj].addZeros((int) (espc[fi + 1] - espc[fi]) - tgtChunks[fj]._len);
          tgtChunks[fj].close(_fs);
          tgtChunks[fj] = null;
//                  tryComplete();
        }
//              }.fork();
//            }
//          }
//        }.fork();
      }
    }
  }


  /**
   * Info about matrix multiplication currently in progress.
   *
   * Contains runtime and (already computed)chunks stats
   *
   */
  public static class MatrixMulStats extends Iced {
    public final Key jobKey;
    public final long chunksTotal;
    public final long _startTime;
    public long lastUpdateAt;
    public long chunksDone;
    public long size;
    public int [] chunkTypes = new int[0];
    public long [] chunkCnts = new long[0];

    public MatrixMulStats(long n, Key jobKey){chunksTotal = n; _startTime = System.currentTimeMillis(); this.jobKey = jobKey;}

    public float progress(){ return (float)((double)chunksDone/chunksTotal);}
  }

  public static Frame mmul(Frame x, Frame y) {
    MatrixMulTsk t = new MatrixMulTsk(null,null,x,y);
    if(Thread.currentThread() instanceof FJWThr)
      t.fork().join();
    else
      H2O.submitTask(t).join();
    return t._z;
  }

  public static class MatrixMulTsk extends H2OCountedCompleter {
    final transient Frame _x;
    Frame _y;
    Frame _z;
    final Key _progressKey;
    AtomicInteger _cntr;
    public MatrixMulTsk(H2OCountedCompleter cmp, Key progressKey, Frame x, Frame y) {
      super(cmp);
      if(x.numCols() != y.numRows())
        throw new IllegalArgumentException("dimensions do not match! x.numcols = " + x.numCols() + ", y.numRows = " + y.numRows());
      _x = x;
      _y = y;
      _progressKey = progressKey;
    }

    @Override
    public void compute2() {
      _z = new Frame(_x.anyVec().makeZeros(_y.numCols()));
      int total_cores = H2O.CLOUD.size()*H2O.NUMCPUS;
      int chunksPerCol = _y.anyVec().nChunks();
      int maxP = 256*total_cores/chunksPerCol;
      Log.info("maxP = " + maxP);
      _cntr = new AtomicInteger(maxP-1);
      addToPendingCount(2*_y.numCols()-1);
      for(int i = 0; i < Math.min(_y.numCols(),maxP); ++i)
        forkVecTask(i);
    }

    private void forkVecTask(final int i) {
      new GetNonZerosTsk(new H2OCallback<GetNonZerosTsk>(this) {
        @Override
        public void callback(GetNonZerosTsk gnz) {
          new VecTsk(new Callback(), _progressKey, gnz._vals).asyncExec(ArrayUtils.append(_x.vecs(gnz._idxs), _z.vec(i)));
        }
      }).asyncExec(_y.vec(i));
    }
    private class Callback extends H2OCallback{
      public Callback(){super(MatrixMulTsk.this);}
      @Override
      public void callback(H2OCountedCompleter h2OCountedCompleter) {
        int i = _cntr.incrementAndGet();
        if(i < _y.numCols())
          forkVecTask(i);
      }
    }
  }
  static int cnt = 0;
  // to be invoked from R expression



  private static class GetNonZerosTsk extends MRTask<GetNonZerosTsk>{
    final int _maxsz;
    int     [] _idxs;
    double  [] _vals;
    public GetNonZerosTsk(H2OCountedCompleter cmp){super(cmp);_maxsz = 10000000;}
    public GetNonZerosTsk(H2OCountedCompleter cmp, int maxsz){super(cmp); _maxsz = maxsz;}

    @Override public void map(Chunk c){
      int istart = (int)c.start();
      assert (c.start() + c._len) == (istart + c._len);
      final int n = c.sparseLen();
      _idxs = MemoryManager.malloc4(n);
      _vals = MemoryManager.malloc8d(n);
      int j = 0;
      for(int i = c.nextNZ(-1); i < c._len; i = c.nextNZ(i),++j) {
        _idxs[j] = i + istart;
        _vals[j] = c.atd(i);
      }
      assert j == n;
      if(_idxs.length > _maxsz)
        throw new RuntimeException("too many nonzeros! found at least " + _idxs.length + " nonzeros.");
    }

    @Override public void reduce(GetNonZerosTsk gnz){
      if(_idxs.length + gnz._idxs.length > _maxsz)
        throw new RuntimeException("too many nonzeros! found at least " + (_idxs.length + gnz._idxs.length) + " nonzeros.");
      int [] idxs = MemoryManager.malloc4(_idxs.length + gnz._idxs.length);
      double [] vals = MemoryManager.malloc8d(_vals.length + gnz._vals.length);
      ArrayUtils.sortedMerge(_idxs,_vals,gnz._idxs,gnz._vals,idxs,vals);
      _idxs = idxs;
      _vals = vals;
    }
  }
  // compute single vec of the output in matrix multiply
  private static class VecTsk extends MRTask<VecTsk> {
    double [] _y;
    Key _progressKey;
    public VecTsk(H2OCountedCompleter cmp, Key progressKey, double [] y){
      super(cmp);
      _progressKey = progressKey;
      _y = y;
    }

    @Override public void setupLocal(){_fr.lastVec().preWriting();}
    @Override public void map(Chunk [] chks) {
      Chunk zChunk = chks[chks.length-1];
      double [] res = MemoryManager.malloc8d(chks[0]._len);
      for(int i = 0; i < _y.length; ++i) {
        final double yVal = _y[i];
        final Chunk xChunk = chks[i];
        for (int k = xChunk.nextNZ(-1); k < res.length; k = xChunk.nextNZ(k))
          try { res[k] += yVal * xChunk.atd(k);} catch(Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
          }
      }
      Chunk modChunk = new NewChunk(res).setSparseRatio(2).compress();
      if(_progressKey != null)
        new UpdateProgress(modChunk.getBytes().length,modChunk.frozenType()).fork(_progressKey);
      DKV.put(zChunk.vec().chunkKey(zChunk.cidx()),modChunk,_fs);
    }
    @Override public void closeLocal(){
      _y = null; // drop inputs
      _progressKey = null;
    }
  }

  private static class UpdateProgress extends TAtomic<MatrixMulStats> {
    final int _chunkSz;
    final int _chunkType;

    public UpdateProgress(int sz, int type) {
      _chunkSz = sz;
      _chunkType = type;
    }

    @Override
    public MatrixMulStats atomic(MatrixMulStats old) {
      old.chunkCnts = old.chunkCnts.clone();
      int j = -1;
      for(int i = 0; i < old.chunkTypes.length; ++i) {
        if(_chunkType == old.chunkTypes[i]) {
          j = i;
          break;
        }
      }
      if(j == -1) {
        old.chunkTypes = Arrays.copyOf(old.chunkTypes,old.chunkTypes.length+1);
        old.chunkCnts = Arrays.copyOf(old.chunkCnts,old.chunkCnts.length+1);
        old.chunkTypes[old.chunkTypes.length-1] = _chunkType;
        j = old.chunkTypes.length-1;
      }
      old.chunksDone++;
      old.chunkCnts[j]++;
      old.lastUpdateAt = System.currentTimeMillis();
      old.size += _chunkSz;
      return old;
    }
  }
}
