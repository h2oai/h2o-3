package water.rapids;

import water.*;
import water.fvec.*;
import water.parser.BufferedString;
import water.util.VecUtils;

import java.util.*;

/** Column slice; allows R-like syntax.
 *  Numbers past the largest column are an error.
 *  Negative numbers and number lists are allowed, and represent an *exclusion* list */
class ASTColSlice extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "cols"}; }
  @Override int nargs() { return 1+2; } // (cols src [col_list])
  @Override public String str() { return "cols" ; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Val v = stk.track(asts[1].exec(env));
    if( v instanceof ValRow ) {
      ValRow vv = (ValRow)v;
      return vv.slice(asts[2].columns(vv._names));
    }
    Frame src = v.getFrame();
    int[] cols = col_select(src._names.getNames(),asts[2]);
    VecAry vecs = src.vecs(cols);
    String[] names = src._names.getNames(cols);
    return new ValFrame(new Frame(null,names,vecs));
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
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Val v = stk.track(asts[1].exec(env));
    if( v instanceof ValRow ) {
      ValRow vv = (ValRow)v;
      return vv.slice(asts[2].columns(vv._names));
    }
    Frame fr = v.getFrame();
    int[] cols = asts[2].columns(fr._names.getNames());

    if( cols.length==0 )        // Empty inclusion list?
      return new ValFrame(new Frame());
    int [] cols2 = new int[cols.length];
    if( cols[0] < 0 )           // Negative cols have number of cols added
      for( int i=0; i<cols.length; i++ )
        cols[i] += fr.numCols();
    if( asts[2] instanceof ASTNum && // Singletons must be in-range
        (cols[0] < 0 || cols[0] >= fr.numCols()) )
      throw new IllegalArgumentException("Column must be an integer from 0 to "+(fr.numCols()-1));
    int j = 0;
    int n = fr.numCols();
    for( int i=0; i<cols.length; i++ )
      if(0 <= cols[i] && cols[i] < n)
        cols2[j++] = cols[i];
    if(j < cols2.length) cols2 = Arrays.copyOf(cols2,j);
    return new ValFrame(new Frame(fr._names.getNames(cols2),fr.vecs(cols2)));
  }
}

/** Row Slice */
class ASTRowSlice extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "rows"}; }
  @Override int nargs() { return 1+2; } // (rows src [row_list])
  @Override public String str() { return "rows" ; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame returningFrame;
    long nrows = fr.numRows();
    if( asts[2] instanceof ASTNumList ) {
      final ASTNumList nums = (ASTNumList)asts[2];

      if( !nums._isSort && !nums.isEmpty() && nums._bases[0] >= 0)
        throw new IllegalArgumentException("H2O does not currently reorder rows, please sort your row selection first");

      long[] rows = (nums._isList || nums.min()<0) ? nums.expand8Sort() : null;
      if( rows!=null ) {
        if (rows.length == 0) {      // Empty inclusion list?
        } else if (rows[0] >= 0) { // Positive (inclusion) list
          if (rows[rows.length - 1] > nrows)
            throw new IllegalArgumentException("Row must be an integer from 0 to " + (nrows - 1));
        } else {                  // Negative (exclusion) list
          if (rows[rows.length - 1] >= 0)
            throw new IllegalArgumentException("Cannot mix negative and postive row selection");
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
        @Override public void map(Chunks cs, Chunks.AppendableChunks ncs) {
          if( nums.cnt()==0 ) return;
          if( ls != null && ls.length == 0 ) return;
          long start = cs.start();
          long end   = start + cs.numRows();
          long min = ls==null?(long)nums.min():ls[0], max = ls==null?(long)nums.max()-1:ls[ls.length-1]; // exclusive max to inclusive max when stride == 1
          BufferedString bs = new BufferedString();
          //     [ start, ...,  end ]     the chunk
          //1 []                          nums out left:  nums.max() < start
          //2                         []  nums out rite:  nums.min() > end
          //3 [ nums ]                    nums run left:  nums.min() < start && nums.max() <= end
          //4          [ nums ]           nums run in  :  start <= nums.min() && nums.max() <= end
          //5                   [ nums ]  nums run rite:  start <= nums.min() && end < nums.max()
          if( !(max<start || min>end) ) {   // not situation 1 or 2 above
            long startOffset = (min > start ? min : start);  // situation 4 and 5 => min > start;
            for( int i=(int)(startOffset-start); i<cs.numRows(); ++i) {
              if( (ls==null && nums.has(start+i)) || (ls!=null && Arrays.binarySearch(ls,start+i) >= 0 )) {
                for(int c=0;c<cs.numCols();++c) {
                  if(cs.isNA(i,c)) ncs.getChunk(c).addNA();
                  else if(_vecs.type(c) == Vec.T_STR) ncs.getChunk(c).addStr(cs.atStr(bs,i,c));
                  else if( _vecs.type(c) == Vec.T_UUID  ) ncs.getChunk(c).addUUID(cs.at16l(i,c),cs.at16h(i,c));
                  else ncs.getChunk(c).addNum(cs.atd(i,c));
                }
              }
            }
          }
        }
      }.doAll(fr.vecs().types(), fr.vecs()).outputFrame(fr._names,fr.vecs().domains());

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
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols()!=1 || fr.numRows()!=1 ) return new ValFrame(fr); // did not flatten
    VecAry vec = fr.vecs();
    Chunk c = vec.getChunk(0,0);
    switch (vec.type(0)) {
      case Vec.T_BAD:
      case Vec.T_NUM:  return new ValNum(c.atd_impl(0));
      case Vec.T_TIME: return new ValNum(c.at8_impl(0));
      case Vec.T_STR:  return new ValStr(c.atStr_impl(new BufferedString(),0).toString());
      case Vec.T_CAT:  return new ValStr(vec.domain(0)[c.at4_impl(0)]);
      default: throw H2O.unimpl("The type of vector: " + vec.typesStr()[0] + " is not supported by " + str());
    }
  }
}

class ASTFilterNACols extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "fraction"}; }
  @Override int nargs() { return 1+2; } // (filterNACols frame frac)
  @Override
  public String str() { return "filterNACols"; }
  @Override
  public ValNums apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    double frac = asts[2].exec(env).getNum();
    double nrow = fr.numRows()*frac;
    VecAry vecs = fr.vecs();
    ArrayList<Double> idxs = new ArrayList<>();
    for( double i=0; i<fr.numCols(); i++ )
      if( vecs.naCnt((int)i) < nrow )
        idxs.add(i);
    double[] include_cols = new double[idxs.size()];
    int i=0;
    for(double d: idxs)
      include_cols[i++] = d;
    return new ValNums(include_cols);
  }
}

/** cbind: bind columns together into a new frame */
class ASTCBind extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"..."}; }
  @Override int nargs() { return -1; } // variable number of args
  @Override
  public String str() { return "cbind" ; }
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {

    // Compute the variable args.  Find the common row count
    Val vals[] = new Val[asts.length];
    Frame fr = null;
    for( int i=1; i<asts.length; i++ ) {
      vals[i] = stk.track(asts[i].exec(env));
      if( vals[i].isFrame() ) {
        VecAry anyvec = vals[i].getFrame().vecs();
        if( anyvec == null || anyvec.len() == 0) continue; // Ignore the empty frame
        if( fr == null ) fr = new Frame(vals[i].getFrame());
        else if( fr.vecs().numRows() != anyvec.numRows() )
          throw new IllegalArgumentException("cbind frames must have all the same rows, found "+fr.vecs().numRows()+" and "+anyvec.numRows()+" rows.");
      }
    }
    boolean clean = false;
    if( fr == null ) { fr = new Frame(); clean = true; } // Default to length 1
    // Populate the new Framer

    for( int i=1; i<asts.length; i++ ) {
      switch( vals[i].type() ) {
      case Val.FRM:
        Frame fr2 = vals[i].getFrame();
        if(fr2 != fr)
          fr.add(fr2._names.getNames(),fr.vecs().makeCompatible(fr2.vecs(),false));
        break;
      case Val.FUN:  throw H2O.unimpl();
      case Val.STR:  throw H2O.unimpl();
      case Val.NUM:  
        // Auto-expand scalars to fill every row
        double d = vals[i].getNum();
        fr.add(Double.toString(d),fr.vecs().makeCons(d));
        break;
      default: throw H2O.unimpl();
      }
    }
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
  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AST asts[]) {

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
        nchks += fr.vecs().nChunks(); // Total chunks
      } else nchks++;  // One chunk per scalar
    }
    // No Frame, just a pile-o-scalars?
    Vec zz = null;              // The zero-length vec for the zero-frame frame
    if( fr==null ) {            // Zero-length, 1-column, default name
      fr = new Frame(zz= VecUtils.makeZero(0));
      if( asts.length == 1 ) return new ValFrame(fr);
    }

    // Verify all Frames are the same columns, names, and types.  Domains can vary, and will be the union
    final Frame frs[] = new Frame[asts.length]; // Input frame
    final byte[] types = fr.vecs().types();  // Column types
    final long[] espc = new long[nchks+1]; // Compute a new layout!
    int coffset = 0;


    Frame[] tmp_frs = new Frame[asts.length];
    for( int i=1; i<asts.length; i++ ) {
      Val val = vals[i];        // Save values computed for pass 2
      Frame fr0 = val.isFrame() ? val.getFrame()
        // Scalar: auto-expand into a 1-row frame
        : (tmp_frs[i] = new Frame(null,fr._names, new VecAry(VecUtils.makeCons(val.getNum(),1L,fr.numCols()))));
      // Check that all frames are compatible
      if( fr.numCols() != fr0.numCols() ) 
        throw new IllegalArgumentException("rbind frames must have all the same columns, found "+fr.numCols()+" and "+fr0.numCols()+" columns.");
      if( !fr._names.equals(fr0._names) )
        throw new IllegalArgumentException("rbind frames must have all the same column names, found "+ fr._names + " and "+ fr0._names);
      if( !Arrays.equals(types,fr0.vecs().types()) )
        throw new IllegalArgumentException("rbind frames must have all the same column types, found "+fr.vecs().typesStr() + " and "+ fr0.vecs().typesStr());
      frs[i] = fr0;     // Save frame
      // Roll up the ESPC row counts
      long roffset = espc[coffset];
      long[] espc2 = fr0.vecs().espc();
      for( int j=1; j < espc2.length; j++ ) // Roll up the row counts
        espc[coffset + j] = (roffset+espc2[j]);
      coffset += espc2.length-1; // Chunk offset
    }
    if( zz != null ) zz.remove();

    // build up the new domains for each vec
    HashMap<String, Integer>[] dmap = new HashMap[types.length];
    String[][] domains = new String[types.length][];
    final int[][][] cmaps = new int[types.length][][];
    for(int k=0;k<types.length;++k) {
      dmap[k] = new HashMap<>();
      int c = 0;
      byte t = types[k];
      if( t == Vec.T_CAT ) {
        int[][] maps = new int[frs.length][];
        for(int i=1; i < frs.length; i++) {
          String [] dom = frs[i].vecs().domain(k);
          maps[i] = new int[dom.length];
          for(int j=0; j < maps[i].length; j++ ) {
            String s = dom[j];
            if( !dmap[k].containsKey(s)) dmap[k].put(s, maps[i][j]=c++);
            else                         maps[i][j] = dmap[k].get(s);
          }
        }
        cmaps[k] = maps;
      } else {
        cmaps[k] = null;
      }
      domains[k] = c==0?null:new String[c];
      for( Map.Entry<String, Integer> e : dmap[k].entrySet())
        domains[k][e.getValue()] = e.getKey();
    }

    Key<Vec> key = fr.vecs().group().addVec();
    int rowLayout = Vec.ESPC.rowLayout(key,espc);
    // TODO: make result one block?
    final Vec v = new Vec(key,rowLayout,fr.vecs().types(),fr.vecs().domains());
    Futures fs = new Futures();
    int coff = 0;
    final int [] coffs = new int[tmp_frs.length+1];
    // Actual Rbind
    // do it in two steps (easier)
    // 1) rbind the data as is, basically just re-insert cloned chunks under different keys
    for(int i = 1; i < frs.length; ++i) {
      final int off = coff;
      fs.add(new MRTask() {
        @Override
        public void map(Chunks chks) {
          Chunks res = new Chunks(chks.getChunks().clone());
          Chunk [] chkary = res.getChunks();
          for(int i = 0; i < chks.numCols(); ++i)
            chkary[i] = chks.getChunk(i).deepCopy();
          DKV.put(v.chunkKey(off+chks.cidx()), res, _fs, true);
        }
      }.dfork(frs[i].vecs()));
      coffs[i+1] = (coff += tmp_frs[i].vecs().nChunks());
    }
    fs.blockForPending();
    DKV.put(v);
    // 2) the data is in the DKV, now fix the categoricals
    new MRTask(){
      @Override
      public void map(Chunks chks) {
        int cidx = chks.cidx();
        int mapId = 0;
        while(cidx < coffs[mapId+1]) mapId++;
        int [][] map = cmaps[mapId];
        for(int i = 0; i < chks.numCols(); ++i) {
          if(map[i] != null) {
            for(int j = 0; j < chks.numRows(); ++j)
              chks.set(j,i,map[i][chks.at4(j,i)]);
          }
        }
      }
    }.doAll(fr.vecs());
    for(int i = 0; i < tmp_frs.length; ++i)
      if(tmp_frs[i] != null) tmp_frs[i].delete();
    return new ValFrame(new Frame(null,fr._names,new VecAry(v)));
  }

}
