package water.rapids;

import jsr166y.CountedCompleter;
import water.*;
import water.fvec.*;
import water.parser.BufferedString;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Column slice; allows R-like syntax.
 *  Numbers past the largest column are an error.
 *  Negative numbers and number lists are allowed, and represent an *exclusion* list */
class ASTColSlice extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "cols"}; }
  @Override int nargs() { return 1+2; } // (cols src [col_list])
  @Override public String str() { return "cols" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Val v = stk.track(asts[1].exec(env));
    if( v instanceof ValRow ) {
      ValRow vv = (ValRow)v;
      return vv.slice(asts[2].columns(vv._names));
    }
    Frame src = v.getFrame();
    int[] cols = col_select(src.names(),asts[2]);
    Frame dst = new Frame();
    Vec[] vecs = src.vecs();
    for( int col : cols )  dst.add(src._names[col],vecs[col]);
    return new ValFrame(dst);
  }

  // Complex column selector; by list of names or list of numbers or single
  // name or number.  Numbers can be ranges or negative.
  static int[] col_select( String[] names, AST col_selector ) {
    int[] cols = col_selector.columns(names);
    if( cols.length==0 ) return cols; // Empty inclusion list?
    if( cols[0] >= 0 ) { // Positive (inclusion) list
      if( cols[cols.length-1] >= names.length )
        throw new IllegalArgumentException("Column must be an integer from 0 to "+(names.length-1));
      return cols;
    }

    // Negative (exclusion) list; convert to positive inclusion list
    int[] pos = new int[names.length];
    for( int col : cols ) // more or less a radix sort, filtering down to cols to ignore
      if( 0 <= -col-1 && -col-1 < names.length ) 
        pos[-col-1] = -1;
    int j=0;
    for( int i=0; i<names.length; i++ )  if( pos[i] == 0 ) pos[j++] = i;
    return Arrays.copyOfRange(pos,0,j);
  }

}

/** Column slice; allows python-like syntax.
 *  Numbers past last column are allowed and ignored in NumLists, but throw an
 *  error for single numbers.  Negative numbers have the number of columns
 *  added to them, before being checked for range.
 */
class ASTColPySlice extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "cols"}; }
  @Override int nargs() { return 1+2; } // (cols_py src [col_list])
  @Override public String str() { return "cols_py" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Val v = stk.track(asts[1].exec(env));
    if( v instanceof ValRow ) {
      ValRow vv = (ValRow)v;
      return vv.slice(asts[2].columns(vv._names));
    }
    Frame fr = v.getFrame();
    int[] cols = asts[2].columns(fr.names());

    Frame fr2 = new Frame();
    if( cols.length==0 )        // Empty inclusion list?
      return new ValFrame(fr2);
    if( cols[0] < 0 )           // Negative cols have number of cols added
      for( int i=0; i<cols.length; i++ )
        cols[i] += fr.numCols();
    if( asts[2] instanceof ASTNum && // Singletons must be in-range
        (cols[0] < 0 || cols[0] >= fr.numCols()) )
      throw new IllegalArgumentException("Column must be an integer from 0 to "+(fr.numCols()-1));
    for( int col : cols )       // For all included columns
      if( col >= 0 && col < fr.numCols() ) // Ignoring out-of-range ones
        fr2.add(fr.names()[col],fr.vecs()[col]);
    return new ValFrame(fr2);
  }
}

/** Row Slice */
class ASTRowSlice extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "rows"}; }
  @Override int nargs() { return 1+2; } // (rows src [row_list])
  @Override public String str() { return "rows" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame returningFrame;
    long nrows = fr.numRows();
    if( asts[2] instanceof ASTNumList ) {
      final ASTNumList nums = (ASTNumList)asts[2];
      long[] rows = nums._isList?nums.expand8Sort():null;
      if( rows!=null ) {
        if (rows.length == 0) {      // Empty inclusion list?
        } else if (rows[0] >= 0) { // Positive (inclusion) list
          if (rows[rows.length - 1] > nrows)
            throw new IllegalArgumentException("Row must be an integer from 0 to " + (nrows - 1));
        } else {                  // Negative (exclusion) list
          // Invert the list to make a positive list, ignoring out-of-bounds values
          BitSet bs = new BitSet((int) nrows);
          for (int i = 0; i < rows.length; i++) {
            int idx = (int) (-rows[i] - 1); // The positive index
            if (idx >= 0 && idx < nrows)
              bs.set(idx);        // Set column to EXCLUDE
          }
          rows = new long[(int) nrows - bs.cardinality()];
          for (int i = bs.nextClearBit(0), j = 0; i < nrows; i = bs.nextClearBit(i + 1))
            rows[j++] = i;
        }
      }
      final long[] ls = rows;

      returningFrame = new MRTask(){
        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
          if( nums.cnt()==0 ) return;
          long start = cs[0].start();
          long end   = start + cs[0]._len;
          long min = ls==null?(long)nums.min():ls[0], max = ls==null?(long)nums.max()-1:ls[ls.length-1]; // exclusive max to inclusive max when stride == 1
          //     [ start, ...,  end ]     the chunk
          //1 []                          nums out left:  nums.max() < start
          //2                         []  nums out rite:  nums.min() > end
          //3 [ nums ]                    nums run left:  nums.min() < start && nums.max() <= end
          //4          [ nums ]           nums run in  :  start <= nums.min() && nums.max() <= end
          //5                   [ nums ]  nums run rite:  start <= nums.min() && end < nums.max()
          if( !(max<start || min>end) ) {   // not situation 1 or 2 above
            long startOffset = (min > start ? min : start);  // situation 4 and 5 => min > start;
            for( int i=(int)(startOffset-start); i<cs[0]._len; ++i) {
              if( (ls==null && nums.has(start+i)) || (ls!=null && Arrays.binarySearch(ls,start+i) >= 0 )) {
                for(int c=0;c<cs.length;++c) {
                  if(      cs[c] instanceof CStrChunk ) ncs[c].addStr(cs[c], i);
                  else if( cs[c] instanceof C16Chunk  ) ncs[c].addUUID(cs[c],i);
                  else if( cs[c].isNA(i)              ) ncs[c].addNA();
                  else                                  ncs[c].addNum(cs[c].atd(i));
                }
              }
            }
          }
        }
      }.doAll(fr.types(), fr).outputFrame(fr.names(),fr.domains());
    } else if( (asts[2] instanceof ASTNum) ) {
      long[] rows = new long[]{(long)(((ASTNum)asts[2])._v.getNum())};
      returningFrame = fr.deepSlice(rows,null);
    } else if( (asts[2] instanceof ASTExec) || (asts[2] instanceof ASTId) ) {
      Frame predVec = stk.track(asts[2].exec(env)).getFrame();
      if( predVec.numCols() != 1 ) throw new IllegalArgumentException("Conditional Row Slicing Expression evaluated to " + predVec.numCols() + " columns.  Must be a boolean Vec.");
      returningFrame = fr.deepSlice(predVec,null);
    } else
      throw new IllegalArgumentException("Row slicing requires a number-list as the last argument, but found a "+asts[2].getClass());
    return new ValFrame(returningFrame);
  }
}

class ASTFlatten extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1+1; } // (flatten fr)
  @Override public String str() { return "flatten"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols()!=1 || fr.numRows()!=1 ) return new ValFrame(fr); // did not flatten
    Vec vec = fr.anyVec();
    if( vec.isNumeric() || vec.isBad() ) return new ValNum(vec.at(0));
    return new ValStr( vec.isString() ? vec.atStr(new BufferedString(),0).toString() : vec.factor(vec.at8(0)));
  }
}

class ASTFilterNACols extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "fraction"}; }
  @Override int nargs() { return 1+2; } // (filterNACols frame frac)
  @Override
  public String str() { return "filterNACols"; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    double frac = asts[2].exec(env).getNum();
    double nrow = fr.numRows()*frac;
    Vec vecs[] = fr.vecs();
    long[] idxs = new long[fr.numCols()];
    int j=0;
    for( int i=0; i<idxs.length; i++ )
      if( vecs[i].naCnt() < nrow )
        idxs[j++] = i;
    Vec vec = Vec.makeVec(Arrays.copyOf(idxs,j),null,Vec.VectorGroup.VG_LEN1.addVec());
    return new ValFrame(new Frame(vec));
  }
}

/** cbind: bind columns together into a new frame */
class ASTCBind extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"..."}; }
  @Override int nargs() { return -1; } // variable number of args
  @Override
  public String str() { return "cbind" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {

    // Compute the variable args.  Find the common row count
    Val vals[] = new Val[asts.length];
    Vec vec = null;
    for( int i=1; i<asts.length; i++ ) {
      vals[i] = stk.track(asts[i].exec(env));
      if( vals[i].isFrame() ) {
        Vec anyvec = vals[i].getFrame().anyVec();
        if( anyvec == null ) continue; // Ignore the empty frame
        if( vec == null ) vec = anyvec;
        else if( vec.length() != anyvec.length() ) 
          throw new IllegalArgumentException("cbind frames must have all the same rows, found "+vec.length()+" and "+anyvec.length()+" rows.");
      }
    }
    boolean clean = false;
    if( vec == null ) { vec = Vec.makeZero(1); clean = true; } // Default to length 1

    // Populate the new Frame
    Frame fr = new Frame();
    for( int i=1; i<asts.length; i++ ) {
      switch( vals[i].type() ) {
      case Val.FRM:  
        fr.add(fr.makeCompatible(vals[i].getFrame()));
        break;
      case Val.FUN:  throw H2O.unimpl();
      case Val.STR:  throw H2O.unimpl();
      case Val.NUM:  
        // Auto-expand scalars to fill every row
        double d = vals[i].getNum();
        fr.add(Double.toString(d),vec.makeCon(d));
        break;
      default: throw H2O.unimpl();
      }
    }
    if( clean ) vec.remove();

    return new ValFrame(fr);
  }
}

/** rbind: bind rows together into a new frame */
class ASTRBind extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"..."}; }
  @Override int nargs() { return -1; } // variable number of args
  @Override
  public String str() { return "rbind" ; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {

    // Execute all args.  Find a canonical frame; all Frames must look like this one.
    // Each argument turns into either a Frame (whose rows are entirely
    // inlined) or a scalar (which is replicated across as a single row). 
    Frame fr = null; // Canonical Frame; all frames have the same column count, types and names
    int nchks=0;     // Total chunks
    Val vals[] = new Val[asts.length]; // Computed AST results
    for( int i=1; i<asts.length; i++ ) {
      vals[i] = stk.track(asts[i].exec(env));
      if( vals[i].isFrame() ) {
        fr = vals[i].getFrame();
        nchks += fr.anyVec().nChunks(); // Total chunks
      } else nchks++;  // One chunk per scalar
    }
    // No Frame, just a pile-o-scalars?
    Vec zz = null;              // The zero-length vec for the zero-frame frame
    if( fr==null ) {            // Zero-length, 1-column, default name
      fr = new Frame(new String[]{Frame.defaultColName(0)}, new Vec[]{zz=Vec.makeZero(0)});
      if( asts.length == 1 ) return new ValFrame(fr);
    }

    // Verify all Frames are the same columns, names, and types.  Domains can vary, and will be the union
    final Frame frs[] = new Frame[asts.length]; // Input frame
    final byte[] types = fr.types();  // Column types
    final long[] espc = new long[nchks+1]; // Compute a new layout!
    int coffset = 0;

    Frame[] tmp_frs = new Frame[asts.length];
    for( int i=1; i<asts.length; i++ ) {
      Val val = vals[i];        // Save values computed for pass 2
      Frame fr0 = val.isFrame() ? val.getFrame() 
        // Scalar: auto-expand into a 1-row frame
        : (tmp_frs[i] = new Frame(fr._names,Vec.makeCons(val.getNum(),1L,fr.numCols())));

      // Check that all frames are compatible
      if( fr.numCols() != fr0.numCols() ) 
        throw new IllegalArgumentException("rbind frames must have all the same columns, found "+fr.numCols()+" and "+fr0.numCols()+" columns.");
      if( !Arrays.deepEquals(fr._names,fr0._names) )
        throw new IllegalArgumentException("rbind frames must have all the same column names, found "+Arrays.toString(fr._names)+" and "+Arrays.toString(fr0._names));
      if( !Arrays.equals(types,fr0.types()) )
        throw new IllegalArgumentException("rbind frames must have all the same column types, found "+Arrays.toString(types)+" and "+Arrays.toString(fr0.types()));

      frs[i] = fr0;     // Save frame

      // Roll up the ESPC row counts
      long roffset = espc[coffset];
      long[] espc2 = fr0.anyVec().espc();
      for( int j=1; j < espc2.length; j++ ) // Roll up the row counts
        espc[coffset + j] = (roffset+espc2[j]);
      coffset += espc2.length-1; // Chunk offset
    }
    if( zz != null ) zz.remove();

    // build up the new domains for each vec
    HashMap<String, Integer>[] dmap = new HashMap[types.length];
    String[][] domains = new String[types.length][];
    int[][][] cmaps = new int[types.length][][];
    for(int k=0;k<types.length;++k) {
      dmap[k] = new HashMap<>();
      int c = 0;
      byte t = types[k];
      if( t == Vec.T_CAT ) {
        int[][] maps = new int[frs.length][];
        for(int i=1; i < frs.length; i++) {
          maps[i] = new int[frs[i].vec(k).domain().length];
          for(int j=0; j < maps[i].length; j++ ) {
            String s = frs[i].vec(k).domain()[j];
            if( !dmap[k].containsKey(s)) dmap[k].put(s, maps[i][j]=c++);
            else                         maps[i][j] = dmap[k].get(s);
          }
        }
        cmaps[k] = maps;
      } else {
        cmaps[k] = new int[frs.length][];
      }
      domains[k] = c==0?null:new String[c];
      for( Map.Entry<String, Integer> e : dmap[k].entrySet())
        domains[k][e.getValue()] = e.getKey();
    }

    // Now make Keys for the new Vecs
    Key<Vec>[] keys = fr.anyVec().group().addVecs(fr.numCols());
    Vec[] vecs = new Vec[fr.numCols()];
    int rowLayout = Vec.ESPC.rowLayout(keys[0],espc);
    for( int i=0; i<vecs.length; i++ )
      vecs[i] = new Vec( keys[i], rowLayout, domains[i], types[i]);


    // Do the row-binds column-by-column.
    // Switch to F/J thread for continuations
    ParallelRbinds t;
    H2O.submitTask(t =new ParallelRbinds(frs,espc,vecs,cmaps)).join();
    for( Frame tfr : tmp_frs ) if( tfr != null ) tfr.delete();
    return new ValFrame(new Frame(fr.names(), t._vecs));
  }


  // Helper class to allow parallel column binds, up to MAXP in parallel at any
  // point in time.  TODO: Not sure why this is here, should just spam F/J with
  // all columns, even up to 100,000's should be fine.
  private static class ParallelRbinds extends H2O.H2OCountedCompleter{
    private final AtomicInteger _ctr; // Concurrency control
    private static int MAXP = 100;    // Max number of concurrent columns
    private Frame[] _frs;             // All frame args
    private int[][][] _cmaps;         // Individual cmaps per each set of vecs to rbind
    private long[] _espc;             // Rolled-up final ESPC

    private Vec[] _vecs;        // Output
    ParallelRbinds( Frame[] frs, long[] espc, Vec[] vecs, int[][][] cmaps) { _frs = frs; _espc = espc; _vecs = vecs; _cmaps=cmaps;_ctr = new AtomicInteger(MAXP-1); }

    @Override protected void compute2() {
      final int ncols = _frs[1].numCols();
      addToPendingCount(ncols-1);
      for (int i=0; i < Math.min(MAXP, ncols); ++i) forkVecTask(i);
    }

    // An RBindTask for each column
    private void forkVecTask(final int colnum) {
      Vec[] vecs = new Vec[_frs.length]; // Source Vecs
      for( int i = 1; i < _frs.length; i++ )
        vecs[i] = _frs[i].vec(colnum);
      new RbindTask(new Callback(), vecs, _vecs[colnum], _espc, _cmaps[colnum]).fork();
    }

    private class Callback extends H2O.H2OCallback {
      public Callback(){super(ParallelRbinds.this);}
      @Override public void callback(H2O.H2OCountedCompleter h2OCountedCompleter) {
        int i = _ctr.incrementAndGet();
        if(i < _vecs.length)
          forkVecTask(i);
      }
    }
  }

  // RBind a single column across all vals
  private static class RbindTask extends H2O.H2OCountedCompleter<RbindTask> {
    final Vec[] _vecs;          // Input vecs to be row-bound
    final Vec _v;               // Result vec
    final long[] _espc;         // Result layout
    int[][] _cmaps;             // categorical mapping array

    RbindTask(H2O.H2OCountedCompleter cc, Vec[] vecs, Vec v, long[] espc, int[][] cmaps) { super(cc); _vecs = vecs; _v = v; _espc = espc; _cmaps=cmaps; }
    @Override protected void compute2() {
      addToPendingCount(_vecs.length-1-1);
      int offset=0;
      for( int i=1; i<_vecs.length; i++ ) {
        new RbindMRTask(this, _cmaps[i], _v, offset).asyncExec(_vecs[i]);
        offset += _vecs[i].nChunks();
      }
    }
    @Override public void onCompletion(CountedCompleter cc) {
      DKV.put(_v);
    }
  }

  private static class RbindMRTask extends MRTask<RbindMRTask> {
    private final int[] _cmap;
    private final int _chunkOffset;
    private final Vec _v;
    RbindMRTask(H2O.H2OCountedCompleter hc, int[] cmap, Vec v, int offset) { super(hc); _cmap = cmap; _v = v; _chunkOffset = offset;}

    @Override public void map(Chunk cs) {
      int idx = _chunkOffset+cs.cidx();
      Key ckey = Vec.chunkKey(_v._key, idx);
      if (_cmap != null) {
        assert !cs.hasFloat(): "Input chunk ("+cs.getClass()+") has float, but is expected to be categorical";
        NewChunk nc = new NewChunk(_v, idx);
        // loop over rows and update ints for new domain mapping according to vecs[c].domain()
        for (int r=0;r < cs._len;++r) {
          if (cs.isNA(r)) nc.addNA();
          else nc.addNum(_cmap[(int)cs.at8(r)], 0);
        }
        nc.close(_fs);
      } else {
        DKV.put(ckey, cs.deepCopy(), _fs, true);
      }
    }
  }

}
