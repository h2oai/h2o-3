package hex.tree;

import hex.genmodel.utils.DistributionFamily;
import jsr166y.CountedCompleter;
import water.*;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.IcedBitSet;
import water.util.VecUtils;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by tomas on 10/28/16.
 *
 * Score and Build Histogram.
 *
 * This is an updated version ditching histogram sharing (still optional) to improve perfomance on multi-cpu systems (witnessed speedup of up to 4x).
 *
 * NOTE: unlike standard MRTask, launch via dfork2 instead of doAll/dfork. Has custom 2-phase local mapreduce task.
 *
 * <p>Fuse 2 conceptual passes into one (MRTask):
 *
 * <dl>
 *
 * <dt>Pass 1:</dt><dd>Score a prior partially-built tree model, and make new Node assignments to
 * every row.  This involves pulling out the current assigned DecidedNode,
 * "scoring" the row against that Node's decision criteria, and assigning the
 * row to a new child UndecidedNode (and giving it an improved prediction).</dd>
 *
 * <dt>Pass 2:</dt><dd>Build new summary DHistograms on the new child UndecidedNodes
 * every row got assigned into.  Collect counts, mean, variance, min,
 * max per bin, per column.</dd>
 * </dl>
 *
 * The 2 passes are executed (locally) in sequence.
 *
 * <p>The result is a set of DHistogram arrays; one DHistogram array for each
 * unique 'leaf' in the tree being histogramed in parallel.  These have node
 * ID's (nids) from 'leaf' to 'tree._len'.  Each DHistogram array is for all
 * the columns in that 'leaf'.
 *
 * <p>The other result is a prediction "score" for the whole dataset, based on
 * the previous passes' DHistograms.
 *
 *
 * No CAS update:
 *
 * Sharing the histograms proved to be a performance problem on larger multi-cpu machines with many running threads, CAS was the bottleneck.
 *
 * To remove the CAS while minimizing the memory overhead (private copies of histograms), phase 2 is paralellized both over columns (primary) and rows (secondary).
 * Parallelization over different columns precedes paralellization within each column to reduce number of extra histogram copies made.
 *
 * Expected number of per-column tasks running in parallel (and hence histogram copies) is given by
 *
 *    exp(nthreads-pre-column) = max(1,H2O.NUMCPUS - num_cols)
 *
 */
public class ScoreBuildHistogram2 extends ScoreBuildHistogram {
  transient int []   _cids;
  transient Chunk[][] _chks;
  transient double [][] _ys;
  transient double [][] _ws;
  transient int [][] _nhs;
  transient int [][] _rss;
  Frame _fr2;
  final int _numLeafs;
  final IcedBitSet _activeCols;

  public ScoreBuildHistogram2(H2O.H2OCountedCompleter cc, int k, int ncols, int nbins, int nbins_cats, DTree tree, int leaf, DHistogram[][] hcs, DistributionFamily family, int weightIdx, int workIdx, int nidIdxs) {
    super(cc, k, ncols, nbins, nbins_cats, tree, leaf, hcs, family, weightIdx, workIdx, nidIdxs);
    _numLeafs = _hcs.length;

    int hcslen = _hcs.length;
    IcedBitSet activeCols = new IcedBitSet(ncols);
    for (int n = 0; n < hcslen; n++) {
      int [] acs = _tree.undecided(n + _leaf)._scoreCols;
      if(acs != null) {
        for (int c : acs) // Columns to score (null, or a list of selected cols)
          activeCols.set(c);
      } else {
        activeCols = null;
        break;
      }
    }
    _activeCols = activeCols;
    _hcs = ArrayUtils.transpose(_hcs);
  }

  @Override
  public ScoreBuildHistogram dfork2(byte[] types, Frame fr, boolean run_local) {
    _fr2 = fr;
    dfork((Key[])null);
    return this;
  }

  @Override public void map(Chunk [] chks){
    // Even though this is an MRTask over a Frame, map(Chunk [] chks) should not be called for this task.
    //  Instead, we do a custom 2-stage local pass (launched from setupLocal) using LocalMR.
    //
    // There are 2 reasons for that:
    //    a) We have 2 local passes. 1st pass scores the trees and sorts rows, 2nd pass starts after the 1st pass is done and computes the histogram.
    //       Conceptually two tasks but since we do not need global result we want to do the two passes inside of 1 task - no need to insert extra communication overhead here.
    //    b) To reduce the memory overhead in pass 2(in case we're making private DHistogram copies).
    //       There is a private copy made for each task. MRTask forks one task per one line of chunks and we do not want to make too many copies.
    //       By reusing the same DHisto for multiple chunks we save memory and calls to reduce.
    //
    throw H2O.unimpl();
  }


  // Pass 1: Score a prior partially-built tree model, and make new Node
  // assignments to every row.  This involves pulling out the current
  // assigned DecidedNode, "scoring" the row against that Node's decision
  // criteria, and assigning the row to a new child UndecidedNode (and
  // giving it an improved prediction).
  // Pass 1: Score a prior partially-built tree model, and make new Node
  // assignments to every row.  This involves pulling out the current
  // assigned DecidedNode, "scoring" the row against that Node's decision
  // criteria, and assigning the row to a new child UndecidedNode (and
  // giving it an improved prediction).
  protected int[] score_decide(Chunk chks[], int nnids[]) {
    int [] res = nnids.clone();
    for( int row=0; row<nnids.length; row++ ) { // Over all rows
      int nid = nnids[row];          // Get Node to decide from
      if( isDecidedRow(nid)) {               // already done
        res[row] -= _leaf;
        continue;
      }
      // Score row against current decisions & assign new split
      boolean oob = isOOBRow(nid);
      if( oob ) nid = oob2Nid(nid); // sampled away - we track the position in the tree
      DTree.DecidedNode dn = _tree.decided(nid);
      if( dn._split == null ) { // Might have a leftover non-split
        if( DTree.isRootNode(dn) ) { res[row] = nid - _leaf; continue; }
        nid = dn._pid;             // Use the parent split decision then
        int xnid = oob ? nid2Oob(nid) : nid;
        nnids[row] = xnid;
        res[row] = xnid - _leaf;
        dn = _tree.decided(nid); // Parent steers us
      }
      assert !isDecidedRow(nid);
      nid = dn.getChildNodeID(chks,row); // Move down the tree 1 level
      if( !isDecidedRow(nid) ) {
        if( oob ) nid = nid2Oob(nid); // Re-apply OOB encoding
        nnids[row] = nid;
      }
      res[row] = nid-_leaf;
    }
    return res;
  }

  @Override
  public void setupLocal() {
    addToPendingCount(1);
    // Init all the internal tree fields after shipping over the wire
    _tree.init_tree();
    Vec v = _fr2.anyVec();
    assert(v!=null);
    _cids = VecUtils.getLocalChunkIds(v);
    _chks = new Chunk[_cids.length][_fr2.numCols()];
    _ys = new double[_cids.length][];
    _ws = new double[_cids.length][];
    _nhs = new int[_cids.length][];
    _rss = new int[_cids.length][];
    long [] espc = v.espc();
    int largestChunkSz = 0;
    for(int i = 1; i < espc.length; ++i){
      int sz = (int)(espc[i] - espc[i-1]);
      if(sz > largestChunkSz) largestChunkSz = sz;
    }
    final int fLargestChunkSz = largestChunkSz;
    if(_weightIdx == -1){
      double [] ws = new double[largestChunkSz];
      Arrays.fill(ws,1);
      Arrays.fill(_ws,ws);
    }
    final AtomicInteger cidx = new AtomicInteger(0);
    // First do the phase 1 on all local data
    new LocalMR(new MrFun(){
      // more or less copied from ScoreBuildHistogram
      private void map(int id, Chunk [] chks) {
        final C4VolatileChunk nids = (C4VolatileChunk) chks[_nidIdx];
        // Pass 1: Score a prior partially-built tree model, and make new Node
        // assignments to every row.  This involves pulling out the current
        // assigned DecidedNode, "scoring" the row against that Node's decision
        // criteria, and assigning the row to a new child UndecidedNode (and
        // giving it an improved prediction).
        int [] nnids;
        if( _leaf > 0)            // Prior pass exists?
          nnids = score_decide(chks,nids.getValues());
        else {                     // Just flag all the NA rows
          nnids = new int[nids._len];
          int [] is = nids.getValues();
          for (int row = 0; row < nids._len; row++) {
            if (isDecidedRow(is[row]))
              nnids[row] = DECIDED_ROW;
          }
        }
        // Pass 2: accumulate all rows, cols into histograms
        // Sort the rows by NID, so we visit all the same NIDs in a row
        // Find the count of unique NIDs in this chunk
        int nh[] = (_nhs[id] = new int[_numLeafs + 1]);
        for (int i : nnids)
          if (i >= 0)
            nh[i + 1]++;
        // Rollup the histogram of rows-per-NID in this chunk
        for (int i = 0; i <_numLeafs; i++) nh[i + 1] += nh[i];
        // Splat the rows into NID-groups
        int rows[] = (_rss[id] = new int[nnids.length]);
        for (int row = 0; row < nnids.length; row++)
          if (nnids[row] >= 0)
            rows[nh[nnids[row]]++] = row;

      }
      @Override
      protected void map(int id) {
        Vec[] vecs = _fr2.vecs();
        for(id = cidx.getAndIncrement(); id < _cids.length; id = cidx.getAndIncrement()) {
          int cidx = _cids[id];
          Chunk [] chks = _chks[id];
          for (int i = 0; i < chks.length; ++i)
            chks[i] = vecs[i].chunkForChunkIdx(cidx);
          map(id,chks);
          chks[_nidIdx].close(cidx,_fs);
          Chunk resChk = chks[_workIdx];
          int len = resChk.len();
          if(resChk instanceof C8DVolatileChunk){
            _ys[id] = ((C8DVolatileChunk)resChk).getValues();
          } else _ys[id] = resChk.getDoubles(MemoryManager.malloc8d(len), 0, len);
          if(_weightIdx != -1){
            _ws[id] = chks[_weightIdx].getDoubles(MemoryManager.malloc8d(len), 0, len);
          }
        }
      }
    },new H2O.H2OCountedCompleter(this){
      public void onCompletion(CountedCompleter cc){
        final int ncols = _ncols;
        final int [] active_cols = _activeCols == null?null:new int[Math.max(1,_activeCols.cardinality())];
        int nactive_cols = active_cols == null?ncols:active_cols.length;
        final int numWrks = _hcs.length*nactive_cols < 16*1024?H2O.NUMCPUS:Math.min(H2O.NUMCPUS,Math.max(4*H2O.NUMCPUS/nactive_cols,1));
        final int rem = H2O.NUMCPUS-numWrks*ncols;
        ScoreBuildHistogram2.this.addToPendingCount(1+nactive_cols);
        if(active_cols != null) {
          int j = 0;
          for (int i = 0; i < ncols; ++i)
            if (_activeCols.contains(i))
              active_cols[j++] = i;
        }
        // MRTask (over columns) launching MrTasks (over number of workers) for each column.
        // We want FJ to start processing all the columns before parallelizing within column to reduce memory overhead.
        // (running single column in n threads means n-copies of the histogram)
        // This is how it works:
        //    1) Outer MRTask walks down it's tree, forking tasks with exponentially decreasing number of columns until reaching its left most leaf for columns 0.
        //       At this point, the local fjq for this thread has a task for processing half of columns at the bottom, followed by task for 1/4 of columns and so on.
        //       Other threads start stealing work from the bottom.
        //    2) forks the leaf task and (because its polling from the top) executes the LocalMr for the column 0.
        // This way we should have columns as equally distributed as possible without resorting to shared priority queue
        new LocalMR(new MrFun() {
          @Override
          protected void map(int c) {
            c = active_cols == null?c:active_cols[c];
            new LocalMR(new ComputeHistoThread(_hcs.length == 0?new DHistogram[0]:_hcs[c],c,fLargestChunkSz,new AtomicInteger()),numWrks + (c < rem?1:0),ScoreBuildHistogram2.this).fork();
          }
        },nactive_cols,ScoreBuildHistogram2.this).fork();
      }
    }).fork();
  }

  private static void mergeHistos(DHistogram [] hcs, DHistogram [] hcs2){
    // Distributed histograms need a little work
    for( int i=0; i< hcs.length; i++ ) {
      DHistogram hs1 = hcs[i], hs2 = hcs2[i];
      if( hs1 == null ) hcs[i] = hs2;
      else if( hs2 != null )
        hs1.add(hs2);
    }
  }

  private class ComputeHistoThread extends MrFun<ComputeHistoThread> {
    final int _maxChunkSz;
    final int _col;
    final DHistogram [] _lh;

    AtomicInteger _cidx;
    private boolean _done;

    public boolean isDone(){return _done || (_done = _cidx.get() >= _cids.length);}

    ComputeHistoThread(DHistogram [] hcs, int col, int maxChunkSz,AtomicInteger cidx){
      _lh = hcs; _col = col; _maxChunkSz = maxChunkSz;
      _cidx = cidx;
    }

    @Override
    public ComputeHistoThread makeCopy() {
      return new ComputeHistoThread(ArrayUtils.deepClone(_lh),_col,_maxChunkSz,_cidx);
    }

    @Override
    protected void map(int id){
      double [] cs = null;
      for(int i = _cidx.getAndIncrement(); i < _cids.length; i = _cidx.getAndIncrement()) {
        if(cs == null) cs = MemoryManager.malloc8d(_maxChunkSz);
        computeChunk(i,cs,_ws[i]);
      }
    }

    private void computeChunk(int id, double [] cs, double [] ws){
      int [] nh = _nhs[id];
      int [] rs = _rss[id];
      Chunk resChk = _chks[id][_workIdx];
      int len = resChk._len;
      double [] ys = ScoreBuildHistogram2.this._ys[id];
      if(_weightIdx != -1) _chks[id][_weightIdx].getDoubles(ws, 0, len);
      final int hcslen = _lh.length;
      boolean extracted = false;
      for (int n = 0; n < hcslen; n++) {
        int sCols[] = _tree.undecided(n + _leaf)._scoreCols; // Columns to score (null, or a list of selected cols)
        if (sCols == null || ArrayUtils.find(sCols, _col) >= 0) {
          DHistogram h = _lh[n];
          int hi = nh[n];
          int lo = (n == 0 ? 0 : nh[n - 1]);
          if (hi == lo || h == null) continue; // Ignore untracked columns in this split
          if (h._vals == null) h.init();
          if (!extracted) {
            _chks[id][_col].getDoubles(cs,0,len);
            extracted = true;
          }
          h.updateHisto(ws, cs, ys, rs, hi, lo);
        }
      }
    }

    @Override
    protected void reduce(ComputeHistoThread cc) {
      assert _lh != cc._lh;
      mergeHistos(_lh, cc._lh);
    }
  }

  @Override public void postGlobal(){
    _hcs = ArrayUtils.transpose(_hcs);
    for(DHistogram [] ary:_hcs)
      for(DHistogram dh:ary) {
        if(dh == null) continue;
        dh.reducePrecision();
      }
  }
}
