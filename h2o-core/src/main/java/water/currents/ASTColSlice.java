package water.currents;

import java.util.Arrays;

import water.*;
import water.fvec.*;

/** Column slice */
class ASTColSlice extends ASTPrim {
  @Override int nargs() { return 1+2; }
  @Override String str() { return "cols" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame fr2 = new Frame();
    if( asts[2] instanceof ASTNumList ) {
      // Work down the list of columns, picking out the keepers
      for( double dcol : ((ASTNumList)asts[2]).expand() ) {
        int col = (int)dcol;
        if( col!=dcol || col < 0 || col >= fr.numCols() ) 
          throw new IllegalArgumentException("Column must be an integer from 0 to "+(fr.numCols()-1));
        fr2.add(fr.names()[col],fr.vecs()[col]);
      }
    } else if( (asts[2] instanceof ASTNum) ) {
      int col = (int) (((ASTNum) asts[2])._d.getNum());
      fr2.add(fr.names()[col], fr.vecs()[col]);

    } else if( (asts[2] instanceof ASTStr) ) {
      int col = fr.find(asts[2].str());
      if( col == -1 ) 
        throw new IllegalArgumentException("No column named '"+asts[2].str()+"' in Frame");
      fr2.add(fr.names()[col], fr.vecs()[col]);
    } else
      throw new IllegalArgumentException("Column slicing requires a number-list as the last argument, but found a "+asts[2].getClass());
    
    return new ValFrame(fr2);
  }
}

/** Row Slice */
class ASTRowSlice extends ASTPrim {
  @Override int nargs() { return 1+2; } // (rows dest [numlist])
  @Override String str() { return "rows" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame returningFrame;
    if( asts[2] instanceof ASTNumList ) {
      final ASTNumList nums = (ASTNumList)asts[2];
      returningFrame = new MRTask(){
        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
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
            int startOffset = (int) (min > start ? min : start);  // situation 4 and 5 => min > start;
            for(int i=startOffset;i<cs[0]._len;++i) {
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
    } else if( (asts[2] instanceof ASTExec) ) {
      Frame predVec = stk.track(asts[2].exec(env)).getFrame();
      if( predVec.numCols() != 1 ) throw new IllegalArgumentException("Conditional Row Slicing Expression evaluated to " + predVec.numCols() + " columns.  Must be a boolean Vec.");
      returningFrame = fr.deepSlice(predVec,null);
    } else
      throw new IllegalArgumentException("Row slicing requires a number-list as the last argument, but found a "+asts[2].getClass());
    return new ValFrame(returningFrame);
  }
}

/** Assign into a row slice */
class ASTRowSliceAssign extends ASTPrim {
  @Override int nargs() { return 1+3; } // (rows= dst src [numlist])
  @Override String str() { return "rows=" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame dst = stk.track(asts[1].exec(env)).getFrame();

    // Sanity check rows vs dst.  To simplify logic, jam the 1 row case in as a ASTNumList
    ASTNumList rows;
    if( asts[3] instanceof ASTNumList ) {
      rows = (ASTNumList)asts[3];
    } else if( (asts[3] instanceof ASTNum) ) {
      rows = new ASTNumList(asts[2].exec(env).getNum());
    } else throw new IllegalArgumentException("Requires a number-list as the last argument, but found a "+asts[3].getClass());
    if( !(0 <= rows.min() && rows.max() <= dst.numRows()) )
      throw new IllegalArgumentException("Row must be an integer from 0 to "+(dst.numRows()-1));

    // Sanity check src vs dst; then assign into dst.
    Val vsrc = stk.track(asts[2].exec(env));
    switch( vsrc.type() ) {
    case Val.NUM:  assign_frame_scalar(dst,rows,vsrc.getNum()  );  break;
    case Val.FRM:  assign_frame_frame (dst,rows,vsrc.getFrame());  break;
    default:       throw new IllegalArgumentException("Source must be a Frame or Number, but found a "+vsrc.getClass());
    }
    return new ValFrame(dst);
  }

  // Rectangular array copy from src into dst
  private void assign_frame_frame(Frame dst, ASTNumList rows, Frame src) {
    // Sanity check
    if( dst.numCols() != src.numCols() )
      throw new IllegalArgumentException("Source and destination frames must have the same count of columns");
    long nrows = rows.cnt();
    if( src.numRows() != nrows )
      throw new IllegalArgumentException("Requires same count of rows in the number-list ("+nrows+") as in the source ("+src.numRows()+")");

    // Trivial all rows case.  Bulk copy all rows.
    // TODO: COW optimization
    Vec[] dvecs = dst.vecs();
    Vec[] svecs = src.vecs();
    if( dst.numRows() == nrows && rows.isDense() ) {
      new MRTask(){
        @Override public void map(Chunk[] cs) {
          int len = cs.length>>1;
          for( int i=0; i<len; i++ ) {
            Chunk cdst = cs[i    ];
            Chunk csrc = cs[i+len];
            cdst.replaceAll(csrc.deepCopy());
          }
        }
      }.doAll(new Frame().add(dst).add(src));
      // Now update all the header info; enums & types
      Futures fs = new Futures();
      for( int col=0; col<dvecs.length; col++ )
        dvecs[col].copyMeta(svecs[col],fs);
      fs.blockForPending();
      if( dst._key != null ) throw H2O.unimpl(); // modified 'dst' need to update DKV?
      return;
    }

    // Partial update; needs to preserve type
    for( int col=0; col<dvecs.length; col++ )
      if( dvecs[col].get_type() != svecs[col].get_type() )
        throw new IllegalArgumentException("Columns must be the same type; column "+col+", \'"+dst._names[col]+"\', is of type "+dvecs[col].get_type_str()+" and the source is "+svecs[col].get_type_str());
    
    // Frame fill
    // Handle fast small case
    if( nrows==1 ) {
      long drow = (long)rows.expand()[0];
      for( int col=0; col<dvecs.length; col++ )
        dvecs[col].set(drow, svecs[col].at(0));
      return;
    }
    // Handle large case
    throw H2O.unimpl();
  }


  private void assign_frame_scalar(Frame dst, final ASTNumList rows, final double src) {

    // Handle fast small case
    Vec[] dvecs = dst.vecs();
    long nrows = rows.cnt();
    if( nrows==1 ) {
      long drow = (long)rows.expand()[0];
      for( Vec vec : dvecs )
        vec.set(drow, src);
      return;
    }

    // Bulk assign constant (probably zero) over a frame
    if( dst.numRows() == nrows && rows.isDense() ) {
      new MRTask(){
        @Override public void map(Chunk[] cs) {
          for( int i=0; i<cs.length; i++ )
            cs[i].replaceAll(new C0DChunk(src,cs[i]._len));
        }
      }.doAll(dst);
      return;
    }

    // Handle large case
    new MRTask(){
      @Override public void map(Chunk[] cs) {
        long start = cs[0].start();
        long end   = start + cs[0]._len;
        double min = rows.min(), max = rows.max()-1; // exclusive max to inclusive max when stride == 1
        //     [ start, ...,  end ]     the chunk
        //1 []                          rows out left:  rows.max() < start
        //2                         []  rows out rite:  rows.min() > end
        //3 [ rows ]                    rows run left:  rows.min() < start && rows.max() <= end
        //4          [ rows ]           rows run in  :  start <= rows.min() && rows.max() <= end
        //5                   [ rows ]  rows run rite:  start <= rows.min() && end < rows.max()
        if( !(max<start || min>end) ) {   // not situation 1 or 2 above
          int startOffset = (int) (min > start ? min : start);  // situation 4 and 5 => min > start;
          for(int i=startOffset;i<cs[0]._len;++i)
            if( rows.has(start+i) )
              for( Chunk chk : cs )
                chk.set(i,src);
        }
      }
    }.doAll(dst);
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
        fr.add(vals[i].getFrame());
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

    // Compute the variable args.  Find the common column count
    Val vals[] = new Val[asts.length];
    Frame fr = null;
    long numRows = 0;
    for( int i=1; i<asts.length; i++ ) {
      vals[i] = stk.track(asts[i].exec(env));
      if( vals[i].isFrame() ) {
        Frame fr0 = vals[i].getFrame();
        numRows += fr0.numRows();
        if( fr == null ) fr = fr0;
        else if( fr.numCols() != fr0.numCols() ) 
          throw new IllegalArgumentException("rbind frames must have all the same columns, found "+fr.numCols()+" and "+fr0.numCols()+" columns.");
        else if( !Arrays.deepEquals(fr._names,fr0._names) )
          throw new IllegalArgumentException("rbind frames must have all the same column names, found "+Arrays.toString(fr._names)+" and "+Arrays.toString(fr0._names));
      } else numRows++;         // Expand scalars into all columns, 1 row
    }
    int numCols = fr==null ? 1 : fr.numCols();

    // Populate the new Frame
    Vec[] vecs = new Vec[numCols];
    for( int c=0; c<numCols; c++ ) vecs[c] = Vec.makeZero(numRows);
    Frame res = new Frame(fr==null ? new String[]{Frame.defaultColName(0)} : fr._names, vecs);
    for( int i=1; i<asts.length; i++ ) {
      switch( vals[i].type() ) {
      case Val.FRM:  throw H2O.unimpl();
      case Val.FUN:  throw H2O.unimpl();
      case Val.STR:  throw H2O.unimpl();
      case Val.NUM:  
        // Auto-expand scalars to fill every column
        for( int c=0; c<numCols; c++ ) {
          if( stk.inUse(res.vec(c)) ) throw H2O.unimpl(); // Copy on write
          res.vec(c).set(i-1,vals[i].getNum());
        }
        break;
      default: throw H2O.unimpl();
      }
    }

    return new ValFrame(res);
  }
}
