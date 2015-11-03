package water.rapids;

import hex.Model;
import water.*;
import water.fvec.*;

/** Assign a whole frame over a global.  Copy-On-Write optimizations make this cheap. */
class ASTAssign extends ASTPrim {
  @Override public String[] args() { return new String[]{"id", "frame"}; }
  @Override int nargs() { return 1+2; } // (assign id frame)
  @Override public String str() { return "assign" ; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Key id = Key.make( asts[1].str() );
    Frame src = stk.track(asts[2].exec(env)).getFrame();
    return new ValFrame(env._ses.assign(id,src)); // New global Frame over shared Vecs
  }
}

/** Rectangular assign into a row and column slice.  The destination must
 *  already exist.  The output is conceptually a new copy of the data, with a
 *  fresh Frame.  Copy-On-Write optimizations lower the cost to be proportional
 *  to the over-written sections.  */
class ASTRectangleAssign extends ASTPrim {
  @Override public String[] args() { return new String[]{"dst", "src", "col_expr", "row_expr"}; }
  @Override int nargs() { return 5; } // (:= dst src col_expr row_expr)
  @Override public String str() { return ":=" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame dst = stk.track(asts[1].exec(env)).getFrame();
    Val vsrc  = stk.track(asts[2].exec(env));
    ASTNumList cols_numlist = check(dst.numCols(), asts[3], dst);
    int[] cols = ASTColSlice.col_select(dst.names(),cols_numlist);

    // Any COW optimized path changes Vecs in dst._vecs, and so needs a
    // defensive copy.  Any update-in-place path updates Chunks instead of
    // dst._vecs, and does not need a defensive copy.  To make life easier,
    // just make the copy now.
    dst = new Frame(dst._names,dst.vecs().clone());

    // Assign over the column slice
    if( asts[4] instanceof ASTNum || asts[4] instanceof ASTNumList ) { // Explictly named row assignment
      ASTNumList rows = asts[4] instanceof ASTNum ? new ASTNumList(((ASTNum)asts[4])._v.getNum()) : ((ASTNumList)asts[4]);
      if( rows.isEmpty() ) rows = new ASTNumList(0,dst.numRows()); // Empty rows is really: all rows
      switch( vsrc.type() ) {
      case Val.NUM:  assign_frame_scalar(dst,cols,rows,vsrc.getNum()  ,env._ses);  break;
      case Val.STR:  assign_frame_scalar(dst,cols,rows,vsrc.getStr()  ,env._ses);  break;
      case Val.FRM:  assign_frame_frame (dst,cols,rows,vsrc.getFrame(),env._ses);  break;
      default:       throw new IllegalArgumentException("Source must be a Frame or Number, but found a "+vsrc.getClass());
      }
    } else {                    // Boolean assignment selection?
      Frame rows = stk.track(asts[4].exec(env)).getFrame();
      switch( vsrc.type() ) {
      case Val.NUM:  assign_frame_scalar(dst,cols,rows,vsrc.getNum()  ,env._ses);  break;
      case Val.STR:  throw H2O.unimpl();
      case Val.FRM:  throw H2O.unimpl();
      default:       throw new IllegalArgumentException("Source must be a Frame or Number, but found a "+vsrc.getClass());
      }
    }
    return new ValFrame(dst);
  }

  private ASTNumList check( long dstX, AST ast, Frame dst ) {
    // Sanity check vs dst.  To simplify logic, jam the 1 col/row case in as a ASTNumList
    ASTNumList dim = new ASTNumList(ast.columns(dst.names()));
    // Special for ASTAssign: "empty" really means "all"
    if( dim.isEmpty() ) return new ASTNumList(0,dstX);
    if( !(0 <= dim.min() && dim.max()-1 <  dstX) &&
            !(1 == dim.cnt() && dim.max()-1 == dstX) ) // Special case of append
      throw new IllegalArgumentException("Selection must be an integer from 0 to "+dstX);
    return dim;
  }

  // Build the destination slice for mutation
  private Frame slice( Frame fr, int[] cols ) {
    Frame slice = new Frame();
    Vec[] vecs = fr.vecs();
    for( int col : cols )  slice.add(fr._names[col],vecs[col]);
    return slice;
  }

  // Rectangular array copy from src into dst
  private void assign_frame_frame(Frame dst, int[] cols, ASTNumList rows, Frame src, Session ses) {
    // Sanity check
    if( cols.length != src.numCols() )
      throw new IllegalArgumentException("Source and destination frames must have the same count of columns");
    long nrows = rows.cnt();
    if( src.numRows() != nrows )
      throw new IllegalArgumentException("Requires same count of rows in the number-list ("+nrows+") as in the source ("+src.numRows()+")");

    // Whole-column assignment?  Directly reuse columns: Copy-On-Write
    // optimization happens here on the apply() exit.
    if( dst.numRows() == nrows && rows.isDense() ) {
      for( int i=0; i<cols.length; i++ )
        dst.replace(cols[i],src.vecs()[i]);
      if( dst._key != null ) DKV.put(dst);
      return;
    }

    // Partial update; needs to preserve type, and may need to copy to support
    // copy-on-write
    Vec[] dvecs = dst.vecs();
    Vec[] svecs = src.vecs();
    for( int col=0; col<cols.length; col++ )
      if( dvecs[cols[col]].get_type() != svecs[col].get_type() )
        throw new IllegalArgumentException("Columns must be the same type; column "+col+", \'"+dst._names[cols[col]]+"\', is of type "+dvecs[cols[col]].get_type_str()+" and the source is "+svecs[col].get_type_str());

    // Frame fill
    // Handle fast small case
    if( nrows<= 1 || (cols.length*nrows)<=1000 ) { // Go parallel for more than 1000 random updates
      // Copy dst columns as-needed to allow update-in-place
      dvecs = ses.copyOnWrite(dst,cols); // Update dst columns
      long[] rownums = rows.expand8();   // Just these rows
      for( int col=0; col<svecs.length; col++ )
        for( int ridx=0; ridx<rownums.length; ridx++ )
          dvecs[cols[col]].set(rownums[ridx], svecs[col].at(ridx));
      return;
    }
    // Handle large case
    //throw H2O.unimpl();
  }

  // Assign a scalar over some dst rows; optimize for all rows
  private void assign_frame_scalar(Frame dst, int[] cols, final ASTNumList rows, final double src, Session ses) {

    // Handle fast small case
    long nrows = rows.cnt();
    if( nrows==1 ) {
      Vec[] vecs = ses.copyOnWrite(dst,cols);
      long drow = (long)rows.expand()[0];
      for( int i=0; i<cols.length; i++ )
        vecs[cols[i]].set(drow, src);
      return;
    }
    
    // Bulk assign constant (probably zero) over a frame.  Directly set
    // columns: Copy-On-Write optimization happens here on the apply() exit.
    if( dst.numRows() == nrows && rows.isDense() ) {
      Vec vsrc = dst.anyVec().makeCon(src);
      for( int i=0; i<cols.length; i++ )
        dst.replace(cols[i],vsrc);
      if( dst._key != null ) DKV.put(dst);
      return;
    }
    
    throw H2O.unimpl();       // Check for needing to copy before updating
    // Handle large case
    //new MRTask(){
    //  @Override public void map(Chunk[] cs) {
    //    long start = cs[0].start();
    //    long end   = start + cs[0]._len;
    //    double min = rows.min(), max = rows.max()-1; // exclusive max to inclusive max when stride == 1
    //    //     [ start, ...,  end ]     the chunk
    //    //1 []                          rows out left:  rows.max() < start
    //    //2                         []  rows out rite:  rows.min() > end
    //    //3 [ rows ]                    rows run left:  rows.min() < start && rows.max() <= end
    //    //4          [ rows ]           rows run in  :  start <= rows.min() && rows.max() <= end
    //    //5                   [ rows ]  rows run rite:  start <= rows.min() && end < rows.max()
    //    if( !(max<start || min>end) ) {   // not situation 1 or 2 above
    //      int startOffset = (int) (min > start ? min : start);  // situation 4 and 5 => min > start;
    //      for(int i=startOffset;i<cs[0]._len;++i)
    //        if( rows.has(start+i) )
    //          for( Chunk chk : cs )
    //            chk.set(i,src);
    //    }
    //  }
    //}.doAll(dst);
  }

  // Assign a scalar over some dst rows; optimize for all rows
  private void assign_frame_scalar(Frame dst, int[] cols, final ASTNumList rows, final String src, Session ses) {
    // Check for needing to copy before updating
    throw H2O.unimpl();
    //// Handle fast small case
    //Vec[] dvecs = dst.vecs();
    //long nrows = rows.cnt();
    //if( nrows==1 ) {
    //  long drow = (long)rows.expand()[0];
    //  for( Vec vec : dvecs )
    //    vec.set(drow, src);
    //  return;
    //}
    //
    //// Bulk assign constant (probably the empty string) over a frame
    ////if( dst.numRows() == nrows && rows.isDense() ) {
    ////  new MRTask(){
    ////    @Override public void map(Chunk[] cs) {
    ////      for( Chunk c : cs )  c.replaceAll(new C0StrChunk(src,c._len));
    ////    }
    ////  }.doAll(dst);
    ////  return;
    ////}
    //
    //// Handle large case
    //new MRTask(){
    //  @Override public void map(Chunk[] cs) {
    //    long start = cs[0].start();
    //    long end   = start + cs[0]._len;
    //    double min = rows.min(), max = rows.max()-1; // exclusive max to inclusive max when stride == 1
    //    //     [ start, ...,  end ]     the chunk
    //    //1 []                          rows out left:  rows.max() < start
    //    //2                         []  rows out rite:  rows.min() > end
    //    //3 [ rows ]                    rows run left:  rows.min() < start && rows.max() <= end
    //    //4          [ rows ]           rows run in  :  start <= rows.min() && rows.max() <= end
    //    //5                   [ rows ]  rows run rite:  start <= rows.min() && end < rows.max()
    //    if( !(max<start || min>end) ) {   // not situation 1 or 2 above
    //      int startOffset = (int) (min > start ? min : start);  // situation 4 and 5 => min > start;
    //      for(int i=startOffset;i<cs[0]._len;++i)
    //        if( rows.has(start+i) )
    //          for( Chunk chk : cs )
    //            chk.set(i,src);
    //    }
    //  }
    //}.doAll(dst);
  }

  // Boolean assignment with a scalar
  private void assign_frame_scalar(Frame dst, int[] cols, Frame rows, final double src, Session ses) {
    // Check for needing to copy before updating
    throw H2O.unimpl();
    //new MRTask() {
    //  @Override public void map(Chunk[] cs) {
    //    Chunk pc = cs[cs.length - 1];
    //    for (int i = 0; i < pc._len; ++i) {
    //      if (pc.at8(i) == 1)
    //        for (int c = 0; c < cs.length - 1; ++c)
    //          cs[c].set(i, src);
    //    }
    //  }
    //}.doAll(dst.add(rows));
  }
}

class ASTAppend extends ASTPrim {
  @Override public String[] args() { return new String[]{"dst", "src", "colName"}; }
  @Override int nargs() { return 1+3; } // (append dst src "colName")
  @Override public String str() { return "append"; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame dst = stk.track(asts[1].exec(env)).getFrame();
    Val vsrc  = stk.track(asts[2].exec(env));
    String newColName =   asts[3].exec(env).getStr();
    
    Vec vec = dst.anyVec();
    switch( vsrc.type() ) {
    case Val.NUM: vec = vec.makeCon(vsrc.getNum()); break;
    case Val.STR: throw H2O.unimpl();
    case Val.FRM: 
      if( vsrc.getFrame().numCols() != 1 ) throw new IllegalArgumentException("Can only append one column");
      vec = vsrc.getFrame().anyVec();   
      break;
    default:  throw new IllegalArgumentException("Source must be a Frame or Number, but found a "+vsrc.getClass());
    }
    dst = new Frame(dst._names.clone(),dst.vecs().clone());
    dst.add(newColName, vec);
    return new ValFrame(dst);
  }
}

/** Assign a temp.  All such assignments are final (cannot change), but the
 *  temp can be deleted.  Temp is returned for immediate use, and also set in
 *  the DKV.  Must be globally unique in the DKV.  */
class ASTTmpAssign extends ASTPrim {
  @Override public String[] args() { return new String[]{"id", "frame"}; }
  @Override int nargs() { return 1+2; } // (tmp= id frame)
  @Override public String str() { return "tmp=" ; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Key id = Key.make( asts[1].str() );
    if( DKV.get(id) != null ) throw new IllegalArgumentException("Temp ID "+id+" already exists");
    Frame src = stk.track(asts[2].exec(env)).getFrame();
    Frame dst = new Frame(id,src._names,src.vecs());
    return new ValFrame(env._ses.track_tmp(dst)); // Track new session-wide ID
  }
}

/** Remove an by ID.  Removing a Frame updates refcnts.  Returns 1 for
 *  removing, 0 if id does not exist. */
class ASTRm extends ASTPrim {
  @Override public String[] args() { return new String[]{"id"}; }
  @Override int nargs() { return 1+1; } // (rm id)
  @Override public String str() { return "rm" ; }
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Key id = Key.make( asts[1].str() );
    Value val = DKV.get(id);
    if( val == null ) return new ValNum(0);
    if( val.isFrame() ) env._ses.remove(val.<Frame>get()); // Remove unshared Vecs
    else                Keyed.remove(id);           // Normal (e.g. Model) remove
    return new ValNum(1);
  }
}

class ASTRename extends ASTPrim {
  @Override public String[] args() { return new String[]{"oldId", "newId"}; }
  @Override int nargs() { return 1+2; } // (rename oldId newId)
  @Override public String str() { return "rename" ; }
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Key oldKey = Key.make(asts[1].exec(env).getStr());
    Key newKey = Key.make(asts[2].exec(env).getStr());
    Iced o = DKV.remove(oldKey).get();
    if( o instanceof Frame )     DKV.put(newKey, new Frame(newKey, ((Frame)o)._names, ((Frame)o).vecs()));
    else if( o instanceof Model) {
      ((Model) o)._key = newKey;
      DKV.put(newKey, o);
    }
    else throw new IllegalArgumentException("Trying to rename Value of type " + o.getClass());
    return new ValNum(Double.NaN);
  }
}
