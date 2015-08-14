package water.currents;

import java.util.Arrays;

import water.*;
import water.fvec.*;

/** Column slice */
class ASTColSlice extends ASTPrim {
  @Override int nargs() { return 1+2; } // (cols src [col_list])
  @Override String str() { return "cols" ; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame fr2 = new Frame();
    if( asts[2] instanceof ASTNumList ) {
      // Work down the list of columns, picking out the keepers
      ASTNumList nums =(ASTNumList)asts[2];
      if( nums.min() >= 0 ) {   // Positive (inclusion) list
        for( double dcol : nums.expand() ) {
          int col = (int)dcol;
          if( col!=dcol || col < 0 || col >= fr.numCols() ) 
            throw new IllegalArgumentException("Column must be an integer from 0 to "+(fr.numCols()-1));
          fr2.add(fr.names()[col],fr.vecs()[col]);
        }
      } else {                  // Negative (exclusion) list
        fr2 = new Frame(fr);    // All of them at first
        // This loop depends on ASTNumList return values in sorted order
        for( double dcol : nums.expand() ) {
          int col = (int)dcol;
          if( col!=dcol || col < -fr.numCols() || col >= 0 ) 
            throw new IllegalArgumentException("Column must be an integer from "+(-fr.numCols())+" to -1");
          fr2.remove(-col-1);   // Remove named column
        }
      }
    } else if( (asts[2] instanceof ASTNum) ) {
      int col = (int) (((ASTNum) asts[2])._d.getNum());
      if( col < 0 ) fr2.add(fr).remove(-1*col-1);  // neg index is 1-based; e.g., -1 => -1*-1 - 1 = 0
      else fr2.add(fr.names()[col], fr.vecs()[col]);

    } else if( (asts[2] instanceof ASTStr) ) {
      int col = fr.find(asts[2].str());
      if (col == -1)
        throw new IllegalArgumentException("No column named '" + asts[2].str() + "' in Frame");
      fr2.add(fr.names()[col], fr.vecs()[col]);
    } else if( (asts[2] instanceof ASTStrList) ) {
      ASTStrList strs = (ASTStrList)asts[2];
      for( String scol:strs._strs ) {
        int col = fr.find(scol);
        if (col == -1)
          throw new IllegalArgumentException("No column named '" + scol + "' in Frame");
        fr2.add(scol, fr.vecs()[col]);
      }
    } else
      throw new IllegalArgumentException("Column slicing requires a number-list as the last argument, but found a "+asts[2].getClass());
    
    return new ValFrame(fr2);
  }
}

/** Row Slice */
class ASTRowSlice extends ASTPrim {
  @Override int nargs() { return 1+2; } // (rows src [row_list])
  @Override String str() { return "rows" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame returningFrame;
    if( asts[2] instanceof ASTNumList ) {
      final ASTNumList nums = (ASTNumList)asts[2];
      returningFrame = new MRTask(){
        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
          if( nums.isEmpty() ) return;
          long start = cs[0].start();
          long end   = start + cs[0]._len;
          double min = nums.min(), max = nums.max()-1; // exclusive max to inclusive max when stride == 1
          //     [ start, ...,  end ]     the chunk
          //1 []                          nums out left:  nums.max() < start
          //2                         []  nums out rite:  nums.min() > end
          //3 [ nums ]                    nums run left:  nums.min() < start && nums.max() <= end
          //4          [ nums ]           nums run in  :  start <= nums.min() && nums.max() <= end
          //5                   [ nums ]  nums run rite:  start <= nums.min() && end < nums.max()
          if( !(max<start || min>end) ) {   // not situation 1 or 2 above
            long startOffset = (min > start ? (long)min : start);  // situation 4 and 5 => min > start;
            for( int i=(int)(startOffset-start); i<cs[0]._len; ++i) {
              if( nums.has(start+i) ) {
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
      }.doAll(fr.numCols(), fr).outputFrame(fr.names(),fr.domains());
    } else if( (asts[2] instanceof ASTNum) ) {
      long[] rows = new long[]{(long)(((ASTNum)asts[2])._d.getNum())};
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
  @Override int nargs() { return 1+1; } // (flatten fr)
  @Override String str() { return "flatten"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols()==1 && fr.numRows()==1 ) {
      if( fr.anyVec().isNumeric() || fr.anyVec().isBad() ) return new ValNum(fr.anyVec().at(0));
      return new ValStr(fr.domains()[0][(int) fr.anyVec().at8(0)]);
    }
    throw new IllegalArgumentException("May only flatten 1x1 Frames to scalars.");
  }
}

class ASTFilterNACols extends ASTPrim {
  @Override int nargs() { return 1+2; } // (filterNACols frame frac)
  @Override String str() { return "filterNACols"; }
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
  @Override int nargs() { return -1; } // variable number of args
  @Override String str() { return "cbind" ; }
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
  @Override int nargs() { return -1; } // variable number of args
  @Override String str() { return "rbind" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {

    // Each argument turns into either a Frame (whose rows are entirely
    // inlined) or a scalar (which is replicated across as a single row).
    Val  vals[] = new Val   [asts.length];
    Key  kfrs[] = new Key   [asts.length];
    double ds[] = new double[asts.length];
    Arrays.fill(ds,Double.NaN); // Error check

    // Compute the variable args.  Find the common column count; compute final chunks and types and names

    Key[] tkeys = new Key[asts.length]; // Temp keys, for key-less frames
    Frame fr = null; // Canonical Frame; all frames have the same column count, types and names
    byte[] types = null;          // Column types
    Vec.VectorGroup group = null; // Some example vector group
    int nchks=0;                  // Total chunks
    for( int i=1; i<asts.length; i++ ) {
      Val val = stk.track(asts[i].exec(env));
      vals[i] = val;            // Save values computed for pass 2
      if( val.isFrame() ) {     // Frames vs Scalars
        Frame fr0 = val.getFrame();
        kfrs[i] = fr0._key;
        if( fr0._key == null ) // Unkeyed frame?  Key it, and record a temp key
          DKV.put(new Frame(kfrs[i]=tkeys[i]=Key.make(), fr0.names(),fr0.vecs()));

        // Check that all frames are compatible
        if( fr == null ) { fr = fr0; types = fr0.types(); }
        if( fr.numCols() != fr0.numCols() ) 
          throw new IllegalArgumentException("rbind frames must have all the same columns, found "+fr.numCols()+" and "+fr0.numCols()+" columns.");
        if( !Arrays.deepEquals(fr._names,fr0._names) )
          throw new IllegalArgumentException("rbind frames must have all the same column names, found "+Arrays.toString(fr._names)+" and "+Arrays.toString(fr0._names));
        if( !Arrays.equals(types,fr0.types()) )
          throw new IllegalArgumentException("rbind frames must have all the same column types, found "+Arrays.toString(types)+" and "+Arrays.toString(fr0.types()));

        nchks += fr0.anyVec().nChunks(); // Total chunks
        // Capture any exciting group; the new frame will use this group
        Vec.VectorGroup gp1 = fr0.anyVec().group();
        if( group == null || group == Vec.VectorGroup.VG_LEN1 ) group = gp1;

      } else {
        ds[i] = val.getNum();   // Expand scalars into all columns, 1 row
      }
    }

    // Compute ESPC, rollup the row counts 
    long[] espc = new long[nchks+1];
    int coffset = 0;
    for( int i=0; i< vals.length; ++i) {
      long roffset = espc[coffset];
      if( vals[i].isFrame() ) { // Frame?
        long[] espc2 = vals[i].getFrame().anyVec().get_espc();
        for( int j=1; j < espc2.length; j++ ) // Roll up the row counts
          espc[coffset + j] = roffset+ espc2[j];
        coffset += espc2.length;
      } else {                     // Scalar?  Then 1 row
        espc[coffset] = roffset+1; // One row, no more chunks
      }
    }

    // Build empty result vectors of the proper type and new row counts.
    Key[] keys = group.addVecs(fr.numCols());
    String[] names = fr==null ? new String[]{Frame.defaultColName(0)} : fr._names;
    String[][] domains = fr.domains();
    Vec[] vecs = new Vec[keys.length];
    for( int i=0; i<vecs.length; ++i )
      vecs[i] = new Vec( keys[i], espc, domains[i], types[i] );

    // Build a frame with all the empty vecs (still no chunks)
    Key tmp_key = Key.make();
    Frame res = new Frame( tmp_key, names, vecs );
    DKV.put(res);               // Publish for MRTask

    // Do the massive parallel rbind, filling in chunks
...borken....
    new RbindTask(kfrs,ds,espc).doAll(res);

    // Cleanup keys
    DKV.remove(tmp_key);        // Just the junk frame; leave the vecs
    for( Key tkey : tkeys ) DKV.remove(tkey);

    return new ValFrame(new Frame(names, vecs));
  }

  // RBind a single column across all vals
  private static class RbindTask extends MRTask<RbindTask> {
    final Key[] _kfrs;
    final double[] _ds;
    final long[] _espc;

    RbindTask(Key[] kfrs, double[] ds, long[] espc) { _kfrs = kfrs; _ds = ds; _espc = espc; }



  }

}
