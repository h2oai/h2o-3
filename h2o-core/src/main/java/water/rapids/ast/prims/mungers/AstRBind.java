package water.rapids.ast.prims.mungers;

import jsr166y.CountedCompleter;
import water.DKV;
import water.H2O;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * rbind: bind rows together into a new frame
 */
public class AstRBind extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"..."};
  }

  @Override
  public int nargs() {
    return -1;
  } // variable number of args

  @Override
  public String str() {
    return "rbind";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {

    // Execute all args.  Find a canonical frame; all Frames must look like this one.
    // Each argument turns into either a Frame (whose rows are entirely
    // inlined) or a scalar (which is replicated across as a single row).
    Frame fr = null; // Canonical Frame; all frames have the same column count, types and names
    int nchks = 0;     // Total chunks
    Val vals[] = new Val[asts.length]; // Computed AstRoot results
    for (int i = 1; i < asts.length; i++) {
      vals[i] = stk.track(asts[i].exec(env));
      if (vals[i].isFrame()) {
        fr = vals[i].getFrame();
        nchks += fr.anyVec().nChunks(); // Total chunks
      } else nchks++;  // One chunk per scalar
    }
    // No Frame, just a pile-o-scalars?
    Vec zz = null;              // The zero-length vec for the zero-frame frame
    if (fr == null) {            // Zero-length, 1-column, default name
      fr = new Frame(new String[]{Frame.defaultColName(0)}, new Vec[]{zz = Vec.makeZero(0)});
      if (asts.length == 1) return new ValFrame(fr);
    }

    // Verify all Frames are the same columns, names, and types.  Domains can vary, and will be the union
    final Frame frs[] = new Frame[asts.length]; // Input frame
    final byte[] types = fr.types();  // Column types
    final long[] espc = new long[nchks + 1]; // Compute a new layout!
    int coffset = 0;

    Frame[] tmp_frs = new Frame[asts.length];
    for (int i = 1; i < asts.length; i++) {
      Val val = vals[i];        // Save values computed for pass 2
      Frame fr0 = val.isFrame() ? val.getFrame()
          // Scalar: auto-expand into a 1-row frame
          : (tmp_frs[i] = new Frame(fr._names, Vec.makeCons(val.getNum(), 1L, fr.numCols())));

      // Check that all frames are compatible
      if (fr.numCols() != fr0.numCols())
        throw new IllegalArgumentException("rbind frames must have all the same columns, found " + fr.numCols() + " and " + fr0.numCols() + " columns.");
      if (!Arrays.deepEquals(fr._names, fr0._names))
        throw new IllegalArgumentException("rbind frames must have all the same column names, found " + Arrays.toString(fr._names) + " and " + Arrays.toString(fr0._names));
      if (!Arrays.equals(types, fr0.types()))
        throw new IllegalArgumentException("rbind frames must have all the same column types, found " + Arrays.toString(types) + " and " + Arrays.toString(fr0.types()));

      frs[i] = fr0;     // Save frame

      // Roll up the ESPC row counts
      long roffset = espc[coffset];
      long[] espc2 = fr0.anyVec().espc();
      for (int j = 1; j < espc2.length; j++) // Roll up the row counts
        espc[coffset + j] = (roffset + espc2[j]);
      coffset += espc2.length - 1; // Chunk offset
    }
    if (zz != null) zz.remove();

    // build up the new domains for each vec
    HashMap<String, Integer>[] dmap = new HashMap[types.length];
    String[][] domains = new String[types.length][];
    int[][][] cmaps = new int[types.length][][];
    for (int k = 0; k < types.length; ++k) {
      dmap[k] = new HashMap<>();
      int c = 0;
      byte t = types[k];
      if (t == Vec.T_CAT) {
        int[][] maps = new int[frs.length][];
        for (int i = 1; i < frs.length; i++) {
          maps[i] = new int[frs[i].vec(k).domain().length];
          for (int j = 0; j < maps[i].length; j++) {
            String s = frs[i].vec(k).domain()[j];
            if (!dmap[k].containsKey(s)) dmap[k].put(s, maps[i][j] = c++);
            else maps[i][j] = dmap[k].get(s);
          }
        }
        cmaps[k] = maps;
      } else {
        cmaps[k] = new int[frs.length][];
      }
      domains[k] = c == 0 ? null : new String[c];
      for (Map.Entry<String, Integer> e : dmap[k].entrySet())
        domains[k][e.getValue()] = e.getKey();
    }

    // Now make Keys for the new Vecs
    Key<Vec>[] keys = new Vec.VectorGroup().addVecs(fr.numCols());
    Vec[] vecs = new Vec[fr.numCols()];
    int rowLayout = Vec.ESPC.rowLayout(keys[0], espc);
    for (int i = 0; i < vecs.length; i++)
      vecs[i] = new Vec(keys[i], rowLayout, domains[i], types[i]);


    // Do the row-binds column-by-column.
    // Switch to F/J thread for continuations
    AstRBind.ParallelRbinds t;
    H2O.submitTask(t = new AstRBind.ParallelRbinds(frs, espc, vecs, cmaps)).join();
    for (Frame tfr : tmp_frs) if (tfr != null) tfr.delete();
    return new ValFrame(new Frame(fr.names(), t._vecs));
  }


  // Helper class to allow parallel column binds, up to MAXP in parallel at any
  // point in time.  TODO: Not sure why this is here, should just spam F/J with
  // all columns, even up to 100,000's should be fine.
  private static class ParallelRbinds extends H2O.H2OCountedCompleter {
    private final AtomicInteger _ctr; // Concurrency control
    private static int MAXP = 100;    // Max number of concurrent columns
    private Frame[] _frs;             // All frame args
    private int[][][] _cmaps;         // Individual cmaps per each set of vecs to rbind
    private long[] _espc;             // Rolled-up final ESPC

    private Vec[] _vecs;        // Output

    ParallelRbinds(Frame[] frs, long[] espc, Vec[] vecs, int[][][] cmaps) {
      _frs = frs;
      _espc = espc;
      _vecs = vecs;
      _cmaps = cmaps;
      _ctr = new AtomicInteger(MAXP - 1);
    }

    @Override
    public void compute2() {
      final int ncols = _frs[1].numCols();
      addToPendingCount(ncols - 1);
      for (int i = 0; i < Math.min(MAXP, ncols); ++i) forkVecTask(i);
    }

    // An RBindTask for each column
    private void forkVecTask(final int colnum) {
      Vec[] vecs = new Vec[_frs.length]; // Source Vecs
      for (int i = 1; i < _frs.length; i++)
        vecs[i] = _frs[i].vec(colnum);
      new AstRBind.RbindTask(new AstRBind.ParallelRbinds.Callback(), vecs, _vecs[colnum], _espc, _cmaps[colnum]).fork();
    }

    private class Callback extends H2O.H2OCallback {
      Callback() { super(AstRBind.ParallelRbinds.this); }

      @Override
      public void callback(H2O.H2OCountedCompleter h2OCountedCompleter) {
        int i = _ctr.incrementAndGet();
        if (i < _vecs.length)
          forkVecTask(i);
      }
    }
  }

  // RBind a single column across all vals
  private static class RbindTask extends H2O.H2OCountedCompleter<AstRBind.RbindTask> {
    final Vec[] _vecs;          // Input vecs to be row-bound
    final Vec _v;               // Result vec
    final long[] _espc;         // Result layout
    int[][] _cmaps;             // categorical mapping array

    RbindTask(H2O.H2OCountedCompleter cc, Vec[] vecs, Vec v, long[] espc, int[][] cmaps) {
      super(cc);
      _vecs = vecs;
      _v = v;
      _espc = espc;
      _cmaps = cmaps;
    }

    @Override
    public void compute2() {
      addToPendingCount(_vecs.length - 1 - 1);
      int offset = 0;
      for (int i = 1; i < _vecs.length; i++) {
        new AstRBind.RbindMRTask(this, _cmaps[i], _v, offset).dfork(_vecs[i]);
        offset += _vecs[i].nChunks();
      }
    }

    @Override
    public void onCompletion(CountedCompleter cc) {
      DKV.put(_v);
    }
  }

  private static class RbindMRTask extends MRTask<AstRBind.RbindMRTask> {
    private final int[] _cmap;
    private final int _chunkOffset;
    private final Vec _v;

    RbindMRTask(H2O.H2OCountedCompleter hc, int[] cmap, Vec v, int offset) {
      super(hc);
      _cmap = cmap;
      _v = v;
      _chunkOffset = offset;
    }

    @Override
    public void map(Chunk cs) {
      int idx = _chunkOffset + cs.cidx();
      Key ckey = Vec.chunkKey(_v._key, idx);
      if (_cmap != null) {
        assert !cs.hasFloat() : "Input chunk (" + cs.getClass() + ") has float, but is expected to be categorical";
        NewChunk nc = new NewChunk(_v, idx);
        // loop over rows and update ints for new domain mapping according to vecs[c].domain()
        for (int r = 0; r < cs._len; ++r) {
          if (cs.isNA(r)) nc.addNA();
          else nc.addNum(_cmap[(int) cs.at8(r)], 0);
        }
        nc.close(_fs);
      } else {
        DKV.put(ckey, cs.deepCopy(), _fs, true);
      }
    }
  }

}
