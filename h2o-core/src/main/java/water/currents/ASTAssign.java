package water.currents;

import water.*;
import water.fvec.*;

/** Assign into a row slice */
class ASTAssign extends ASTPrim {
  @Override int nargs() { return 1+4; } // (= dst src col_expr row_expr)
  @Override String str() { return "=" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame dst = stk.track(asts[1].exec(env)).getFrame();
    Val vsrc  = stk.track(asts[2].exec(env));
    ASTNumList cols = check(dst.numCols(), asts[3]);

    // Check for append; add a col of NAs if appending
    if( cols.cnt()==1 && cols.max()-1==dst.numCols() ) {  // -1 since max is exclusive
      dst = new Frame(dst._names.clone(),dst.vecs().clone());
      dst.add(Frame.defaultColName(dst.numCols()), dst.anyVec().makeCon(Double.NaN));
      // DKV update on dst happens in RapidsHandler
    }

    // Slice out cols for mutation
    Frame slice = new ASTColSlice().apply(env,stk,new AST[]{null,new ASTFrame(dst),cols}).getFrame();

    // Assign over the column slice
    if( asts[4] instanceof ASTNum || asts[4] instanceof ASTNumList ) { // Explictly named row assignment
      ASTNumList rows = check( dst.numRows(), asts[4] );
      switch( vsrc.type() ) {
      case Val.NUM:  assign_frame_scalar(slice,rows,vsrc.getNum()  );  break;
      case Val.STR:  assign_frame_scalar(slice,rows,vsrc.getStr()  );  break;
      case Val.FRM:  assign_frame_frame (slice,rows,vsrc.getFrame());  break;
      default:       throw new IllegalArgumentException("Source must be a Frame or Number, but found a "+vsrc.getClass());
      }
    } else {                    // Boolean assignment selection?
      Frame rows = stk.track(asts[4].exec(env)).getFrame();
      switch( vsrc.type() ) {
      case Val.NUM:  assign_frame_scalar(slice,rows,vsrc.getNum()  );  break;
      case Val.STR:  throw H2O.unimpl();
      case Val.FRM:  throw H2O.unimpl();
      default:       throw new IllegalArgumentException("Source must be a Frame or Number, but found a "+vsrc.getClass());
      }
    }
    return new ValFrame(dst);
  }

  private ASTNumList check( long dstX, AST ast ) {
    // Sanity check vs dst.  To simplify logic, jam the 1 col/row case in as a ASTNumList
    ASTNumList dim;
    if( ast instanceof ASTNumList  ) dim = (ASTNumList)ast;
    else if( ast instanceof ASTNum ) dim = new ASTNumList(((ASTNum)ast)._d.getNum());
    else throw new IllegalArgumentException("Requires a number-list, but found a "+ast.getClass());
    // Special for ASTAssign: "empty" really means "all"
    if( dim.isEmpty() ) return new ASTNumList(0,dstX);
    if( !(0 <= dim.min() && dim.max()-1 <  dstX) &&
        !(1 == dim.cnt() && dim.max()-1 == dstX) ) // Special case of append
      throw new IllegalArgumentException("Selection must be an integer from 0 to "+dstX);
    return dim;
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

  // Assign a scalar over some dst rows; optimize for all rows
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
          for( Chunk c : cs )  c.replaceAll(new C0DChunk(src,c._len));
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

  // Assign a scalar over some dst rows; optimize for all rows
  private void assign_frame_scalar(Frame dst, final ASTNumList rows, final String src) {
    // Handle fast small case
    Vec[] dvecs = dst.vecs();
    long nrows = rows.cnt();
    if( nrows==1 ) {
      long drow = (long)rows.expand()[0];
      for( Vec vec : dvecs )
        vec.set(drow, src);
      return;
    }

    // Bulk assign constant (probably the empty string) over a frame
    //if( dst.numRows() == nrows && rows.isDense() ) {
    //  new MRTask(){
    //    @Override public void map(Chunk[] cs) {
    //      for( Chunk c : cs )  c.replaceAll(new C0StrChunk(src,c._len));
    //    }
    //  }.doAll(dst);
    //  return;
    //}

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

  // Boolean assignment with a scalar
  private void assign_frame_scalar(Frame dst, Frame rows, final double src) {
    new MRTask() {
      @Override public void map(Chunk[] cs) {
        Chunk pc = cs[cs.length - 1];
        for (int i = 0; i < pc._len; ++i) {
          if (pc.at8(i) == 1)
            for (int c = 0; c < cs.length - 1; ++c)
              cs[c].set(i, src);
        }
      }
    }.doAll(dst.add(rows));
  }
}

/** Assign a temp.  All such assignments are final (cannot change), but the
 *  temp can be deleted.  Temp is returned for immediate use, and also set in
 *  the DKV.  Must be globally unique in the DKV.  */
class ASTTmpAssign extends ASTPrim {
  @Override int nargs() { return 1+2; } // (tmp= id frame)
  @Override String str() { return "tmp=" ; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    String id = ((ASTId)asts[1])._id;
    Frame src = stk.track(asts[2].exec(env)).getFrame();
    Frame dst = src.deepCopy(id);  // Stomp temp down
    return env.addGlobals(dst);
  }
}
