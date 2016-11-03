package hex.tree;

import hex.genmodel.utils.DistributionFamily;
import jsr166y.CountedCompleter;
import jsr166y.ForkJoinTask;
import sun.util.resources.uk.CalendarData_uk;
import water.*;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import water.fvec.Vec;
import water.util.Log;
import water.util.VecUtils;

/**
 * Created by tomas on 10/28/16.
 *
 * Experimental alternative for ScoreBuildHistogram.
 *
 * Allows for paralellization in both dimensions and histograms can be shared or deep cloned.
 * In the first step, it computes leaf assignemnt and sorts the rows according to their leaf (might not be neccessary for not-shared histos).
 * Second step is paralellized over columns (groups of columns) and possibly over rows and calculates histo usung results from step 1.
 *
 * The idea is to limit the number of CAS calls which causes issues on multiprocessor machines.
 * Ideally we would paralllize by columns. Since histogram for different columns are independent, there are no conflicts.
 * However, there is an overhead per given chunk of data (extracting response, weights) and there might be not enough paralellization this way.
 * The first issue ais addressed by grouping the columns, thus reducing the per-chunk overhead. The lack of parallelization is when there is lfewer columns than cores.
 * That can easily happen especially if the columns are grouped. For this reason, there is also a parallelization over row dimension.
 * the histos in this can be shared or deep cloned. The idea is that since we're parallelizing over the columns, there is gonna be fewer threads working over the rows and less collisions (or lower memory overhead of deep cloned histos).
 */
public class ScoreBuildHistogram2 extends ScoreBuildHistogram {
  transient int []   _cids;
  transient int [][] _nhs;
  transient int [][] _rss;
  transient int [][] _nnids;

  Frame _fr2;

  public static class SCBParms extends Iced{
    public int blockSz;
    public boolean sharedHisto;
    public int min_threads;
    public boolean _unordered;
  }
  SCBParms _parms;

  public ScoreBuildHistogram2(H2O.H2OCountedCompleter cc, int k, int ncols, int nbins, int nbins_cats, DTree tree, int leaf, DHistogram[][] hcs, DistributionFamily family, int weightIdx, int workIdx, int nidIdx, SCBParms parms) {
    super(cc, k, ncols, nbins, nbins_cats, tree, leaf, hcs, family, weightIdx, workIdx, nidIdx);
    _parms = parms;
  }

  @Override
  public ScoreBuildHistogram dfork2(byte[] types, Frame fr, boolean run_local) {
    _fr2 = fr;
    dfork((Key[])null);
    return this;
  }

  @Override public void map(Chunk [] chks){
    throw H2O.unimpl();
  }
  @Override
  public void setupLocal() {
    // Init all the internal tree fields after shipping over the wire
    _tree.init_tree();
    _cids = VecUtils.getLocalChunkIds(_fr2.anyVec());
    _nhs = new int[_cids.length][];
    _rss = new int[_cids.length][];
    if(_parms._unordered)
      _nnids = new int[_cids.length][];

    H2O.submitTask(new LocalMR(new MRT(){
      // more or less copied from ScoreBuildHistogram
      private void map(int id, Chunk [] chks) {
        final Chunk wrks = chks[_workIdx];
        final Chunk nids = chks[_nidIdx];
        final Chunk weight = _weightIdx>=0 ? chks[_weightIdx] : new C0DChunk(1, chks[0].len());

        // Pass 1: Score a prior partially-built tree model, and make new Node
        // assignments to every row.  This involves pulling out the current
        // assigned DecidedNode, "scoring" the row against that Node's decision
        // criteria, and assigning the row to a new child UndecidedNode (and
        // giving it an improved prediction).
        int nnids[] = new int[nids._len];
        if(_parms._unordered)
          _nnids[id] = nnids;
        if( _leaf > 0)            // Prior pass exists?
          score_decide(chks,nids,nnids);
        else                      // Just flag all the NA rows
          for( int row=0; row<nids._len; row++ ) {
            if( weight.atd(row) == 0) continue;
            if( isDecidedRow((int)nids.atd(row)) )
              nnids[row] = DECIDED_ROW;
          }
        // Pass 2: accumulate all rows, cols into histograms
        // Sort the rows by NID, so we visit all the same NIDs in a row
        // Find the count of unique NIDs in this chunk
        int nh[] = (_nhs[id] = new int[_hcs.length+1]);
        for( int i : nnids )
          if( i >= 0 )
            nh[i+1]++;
        // Rollup the histogram of rows-per-NID in this chunk
        for( int i=0; i<_hcs.length; i++ ) nh[i+1] += nh[i];
        // Splat the rows into NID-groups
        if(_parms._unordered){
          for(int i = 0; i < nnids.length; ++i)
            nnids[i] -= _leaf;
        } else {
          int rows[] = (_rss[id] = new int[nnids.length]);
          for (int row = 0; row < nnids.length; row++)
            if (nnids[row] >= 0)
              rows[nh[nnids[row]]++] = row;
        }
        // rows[] has Chunk-local ROW-numbers now, in-order, grouped by NID.
        // nh[] lists the start of each new NID, and is indexed by NID+1.
      }
      @Override
      void map(int id) {
        Chunk [] chks = new Chunk[_fr2.numCols()];
        Vec [] vecs = _fr2.vecs();
        for(int i = 0; i < chks.length; ++i)
          chks[i] = vecs[i].chunkForChunkIdx(_cids[id]);
        map(id,chks);
      }
    })).join();
    int ncores = H2O.NUMCPUS;

    if(_parms.sharedHisto){
      for (int l = _leaf; l < _tree._len; l++) {
        DTree.UndecidedNode udn = _tree.undecided(l);
        DHistogram hs[] = _hcs[l - _leaf];
        int sCols[] = udn._scoreCols;
        if (sCols != null) { // Sub-selecting just some columns?
          for (int col : sCols) // For tracked cols
            hs[col].init();
        } else {                 // Else all columns
          for (int j = 0; j < hs.length; j++) // For all columns
            if (hs[j] != null)        // Tracking this column?
              hs[j].init();
        }
      }
    }
    long [] espc = _fr2.anyVec().espc();
    int largestChunkSz = 0;
    for(int i = 1; i < espc.length; ++i){
      int sz = (int)(espc[i] - espc[i-1]);
      if(sz > largestChunkSz) largestChunkSz = sz;
    }
    int ncols = _ncols;
    int colBlockSz= Math.min(ncols,_parms.blockSz);
    while(0 < ncols - colBlockSz && ncols % colBlockSz != 0 && ncols % colBlockSz < (colBlockSz >> 1))
      colBlockSz++;
    int nrowThreads = 1;
    int ncolBlocks = ncols/colBlockSz + (ncols%colBlockSz == 0?0:1);
    while(ncolBlocks*nrowThreads < _parms.min_threads)nrowThreads++;
    Log.info("column block sz = " + colBlockSz + ", nthreads per block = " + nrowThreads + ", shared histo = " + _parms.sharedHisto);
    final int nthreads = nrowThreads;
    ArrayList<ForkJoinTask> tsks = new ArrayList<>();
    for(int i = 0; i < ncols; i += colBlockSz){
      final int colFrom= i;
      final int colTo = Math.min(ncols,colFrom+colBlockSz);
      DHistogram[][] hcs = _hcs.clone();
      for(int j = 0; j < hcs.length; ++j)
        hcs[j] = Arrays.copyOfRange(hcs[j],colFrom,colTo);
      tsks.add(new LocalMR<ComputeHistoThread>(new ComputeHistoThread(hcs,colFrom,colTo,largestChunkSz,_parms.sharedHisto,_parms._unordered),priority(),nthreads));
    }
    ForkJoinTask.invokeAll(tsks);
  }

  // Reduce for both local and remote
  private static void mergeHistos(DHistogram [][] hcs, DHistogram [][] hcs2){
    // Distributed histograms need a little work
    for( int i=0; i< hcs.length; i++ ) {
      DHistogram hs1[] = hcs[i], hs2[] = hcs2[i];
      if( hs1 == null ) hcs[i] = hs2;
      else if( hs2 != null )
        for( int j=0; j<hs1.length; j++ )
          if( hs1[j] == null ) hs1[j] = hs2[j];
          else if( hs2[j] != null )
            hs1[j].add(hs2[j]);
    }
  }

  public static abstract class MRT<T extends MRT<T>> extends Iced<T> {
    void init(){}
    abstract void map(int id);
    void reduce(T t){}
    public MRT<T> makeCopy(){return clone();}
  }
  private class LocalMR<T extends MRT> extends H2O.H2OCountedCompleter<LocalMR> {
    int _lo;
    int _hi;
    boolean _top = true;
    final MRT _mrt;

    AtomicInteger _cidx = new AtomicInteger();
    AtomicInteger _tcnt = new AtomicInteger();



    public LocalMR(MRT mrt, byte priority, int nthreads) { super(priority); _mrt = mrt; _lo = 0; _hi = nthreads;}
    public LocalMR(MRT mrt, byte priority ) { this(mrt,priority,H2O.NUMCPUS);}
    public LocalMR(MRT mrt) { _mrt = mrt; _lo = 0; _hi = H2O.NUMCPUS;}
    public LocalMR(LocalMR src, int lo, int hi) {
      super(src);
      _mrt = src._mrt.makeCopy();
      _lo = lo;
      _hi = hi;
      _cidx = src._cidx;
      _tcnt = src._tcnt;
      _top = false;
    }

    private LocalMR<T> _left;
    private LocalMR<T> _rite;

    volatile boolean completed;

    @Override
    public final void compute2() {
      _tcnt.incrementAndGet();
      if (_hi - _lo >= 2) {
        int mid = _lo + ((_hi - _lo) >> 1);
        addToPendingCount(1);
        H2O.submitTask(_left = new LocalMR(this,_lo,mid));
        if ((mid + 1) < _hi) {
          addToPendingCount(1);
          H2O.submitTask(_rite = new LocalMR(this,mid+1,_hi));
        }
      }
      if(_hi > _lo) {
        _mrt.init();
        for (int i = _cidx.getAndIncrement(); i < _cids.length; i = _cidx.getAndIncrement())
          _mrt.map(i);
      } else throw new RuntimeException("should not get here, lo = " + _lo + ", hi = " + _hi);
      completed = true;
      tryComplete();
    }

    @Override
    public final void onCompletion(CountedCompleter cc) {
      assert cc == this || cc == _left || cc == _rite;
      assert !_top || _tcnt.get() == _hi:"hi = " + _hi + ", tcnt = " + _tcnt.get();
      if(_left != null) { assert _left.completed; _mrt.reduce(_left._mrt); _left = null;}
      if(_rite != null) { assert _rite.completed; _mrt.reduce(_rite._mrt); _rite = null;}
    }
  }

  private class ComputeHistoThread extends MRT<ComputeHistoThread> {
    final int _maxChunkSz;
    final int _colFrom, _colTo;
    boolean _shareHisto = false;
    boolean _unordered = false;
    final DHistogram [][] _lhcs;

    double [] cs = null;
    double [] ws = null;
    double [] ys = null;

    ComputeHistoThread(DHistogram [][] hcs, int colFrom, int colTo, int maxChunkSz, boolean sharedHisto, boolean unordered){
      _lhcs = hcs; _colFrom = colFrom; _colTo = colTo; _maxChunkSz = maxChunkSz;
      _shareHisto = sharedHisto;
      _unordered = unordered;
    }

    @Override
    public ComputeHistoThread makeCopy() {
      for(DHistogram[] dr:_lhcs)
        for(DHistogram d:dr)
          assert _shareHisto ||  d == null || d._w == null;
      ComputeHistoThread res = new ComputeHistoThread(_shareHisto? _lhcs :ArrayUtils.deepClone(_lhcs),_colFrom,_colTo,_maxChunkSz,_shareHisto,_unordered);
      return res;
    }

    void init(){
      cs = MemoryManager.malloc8d(_maxChunkSz);
      ys = MemoryManager.malloc8d(_maxChunkSz);
      ws = MemoryManager.malloc8d(_maxChunkSz);
      // start computing
      if(!_shareHisto) {
        for (int l = _leaf; l < _tree._len; l++) {
          DTree.UndecidedNode udn = _tree.undecided(l);
          DHistogram hs[] = _lhcs[l - _leaf];
          int sCols[] = udn._scoreCols;
          if (sCols != null) { // Sub-selecting just some columns?
            for (int col : sCols) // For tracked cols
              if (_colFrom <= col && col < _colTo) hs[col - _colFrom].init();
          } else {                 // Else all columns
            for (int j = 0; j < hs.length; j++) // For all columns
              if (hs[j] != null)        // Tracking this column?
                hs[j].init();
          }
        }
      }
    }

    @Override
    public void map(int id){
      ScoreBuildHistogram.LocalHisto lh = _shareHisto?new ScoreBuildHistogram.LocalHisto(Math.max(_nbins,_nbins_cats)):null;
      int cidx = _cids[id];
      int [] nh = _nhs[id];
      int [] rs = _rss[id];
      int [] nnids = _unordered?_nnids[id]:null;
      Chunk resChk = _fr2.vec(_workIdx).chunkForChunkIdx(cidx);
      int len = resChk._len;
      resChk.getDoubles(ys, 0, len);
      if(_weightIdx != -1)
        _fr2.vec(_weightIdx).chunkForChunkIdx(cidx).getDoubles(ws, 0, len);
      else
        Arrays.fill(ws,1);
      final int hcslen = _lhcs.length;
      for (int c = _colFrom; c < _colTo; c++) {
        if(_unordered){
          throw H2O.unimpl();
        } else {
          boolean extracted = false;
          for (int n = 0; n < hcslen; n++) {
            int sCols[] = _tree.undecided(n + _leaf)._scoreCols; // Columns to score (null, or a list of selected cols)
            if (sCols == null || ArrayUtils.find(sCols, c) >= 0) {
              if (!extracted) {
                _fr2.vec(c).chunkForChunkIdx(cidx).getDoubles(cs, 0, len);
                extracted = true;
              }
              DHistogram h = _lhcs[n][c - _colFrom];
              if (h == null) continue; // Ignore untracked columns in this split
              if (_shareHisto) {
                lh.resizeIfNeeded(h._w.length);
                h.updateSharedHistosAndReset(lh, ws, cs, ys, rs, nh[n], n == 0 ? 0 : nh[n - 1]);
              } else h.updateHisto(ws, cs, ys, rs, nh[n], n == 0 ? 0 : nh[n - 1]);
            }
          }
        }
      }
    }

    @Override
    public void reduce(ComputeHistoThread cc) {
      if(!_shareHisto) {
        assert _lhcs != cc._lhcs;
        mergeHistos(_lhcs, cc._lhcs);
      } else assert _lhcs == cc._lhcs;
    }
  }
}
