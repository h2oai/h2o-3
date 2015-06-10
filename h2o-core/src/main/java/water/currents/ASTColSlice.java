package water.currents;

import water.MRTask;
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
      int col = (int)(((ASTNum)asts[2])._d.getNum());
      fr2.add(fr.names()[col],fr.vecs()[col]);
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
    Vec[] dvecs = dst.vecs();

    // Sanity check rows vs dst.  To simplify logic, jam the 1 row case in as a ASTNumList
    ASTNumList nlist;
    if( asts[3] instanceof ASTNumList ) {
      nlist = (ASTNumList)asts[3];
    } else if( (asts[3] instanceof ASTNum) ) {
      nlist = new ASTNumList(asts[2].exec(env).getNum());
    } else throw new IllegalArgumentException("Requires a number-list as the last argument, but found a "+asts[3].getClass());
    if( !(0 <= nlist.min() && nlist.max() <= dst.numRows()) )
      throw new IllegalArgumentException("Row must be an integer from 0 to "+(dst.numRows()-1));
    long nrows = nlist.cnt();

    // Sanity check src vs dst.
    Val vsrc = stk.track(asts[2].exec(env));
    if( vsrc.isFrame() ) {      // Frame vs Frame 
      Frame src = vsrc.getFrame();
      if( dst.numCols() != src.numCols() )
        throw new IllegalArgumentException("Source and destination frames must have the same count and type of columns");
      Vec[] svecs = src.vecs();
      for( int col=0; col<dvecs.length; col++ )
        if( dvecs[col].get_type() != svecs[col].get_type() )
          throw new IllegalArgumentException("Columns must be the same type; column "+col+", \'"+dst._names[col]+"\', is of type "+dvecs[col].get_type_str()+" and the source is "+svecs[col].get_type_str());
      if( src.numRows() != nrows )
        throw new IllegalArgumentException("Requires same count of rows in the number-list ("+nrows+") as in the source ("+src.numRows()+")");

      // Frame fill
      // Handle fast small case
      if( nrows==1 ) {
        replace_row(dvecs,(long)nlist.expand()[0],svecs,0);
        return new ValFrame(dst);
      }
      // Handle large case
      throw water.H2O.unimpl();
    
    } else if( vsrc.isNum() ) { // Frame vs Bare number
      final double d = vsrc.getNum();
      // Number fill
      // Handle fast small case
      if( nrows==1 ) {
        replace_row(dvecs,(long)nlist.expand()[0],d);
        return new ValFrame(dst);
      }

      // Handle large case
      final ASTNumList nums = nlist;
      new MRTask(){
        @Override public void map(Chunk[] cs) {
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
            for(int i=startOffset;i<cs[0]._len;++i)
              if( nums.has(start+i) )
                for(int c=0;c<cs.length;++c)
                  cs[c].set(i,d);
          }
        }
      }.doAll(dst);
      return new ValFrame(dst);
    }

    throw new IllegalArgumentException("Source must be a Frame or Number, but found a "+vsrc.getClass());
  }

  // Replace 1 row in the dest from the src.  All things are known compatible.
  private static void replace_row( Vec[] dvecs, long drow, Vec[] svecs, int srow ) {
    for( int col=0; col<dvecs.length; col++ )
      dvecs[col].set(drow, svecs[col].at(srow));
  }

  // Replace 1 row in the dest from the src.  All things are known compatible.
  private static void replace_row( Vec[] dvecs, long drow, double d ) {
    for( int col=0; col<dvecs.length; col++ )
      dvecs[col].set(drow, d);
  }

}
