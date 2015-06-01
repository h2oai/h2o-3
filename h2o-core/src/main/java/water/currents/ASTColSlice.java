package water.currents;

import water.fvec.*;

/** Column slice */
class ASTColSlice extends ASTPrim {
  @Override int nargs() { return 1+2; }
  @Override String str() { return "cols" ; }
  @Override Val apply( Env env, AST asts[] ) {
    try (Env.StackHelp stk = env.stk()) {
        Val vfr  = stk.track(asts[1].exec(env));
        Frame fr = vfr.getFrame();
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

        return stk.returning(new ValFrame(fr2));
      }
  }
}

class ASTRowSlice extends ASTPrim {
  @Override int nargs() { return 1+2; }
  @Override String str() { return "rows" ; }
  @Override Val apply( Env env, AST asts[] ) {
    try (Env.StackHelp stk = env.stk()) {
        Val vfr  = stk.track(asts[1].exec(env));
        Frame fr = vfr.getFrame();
        if( !(asts[2] instanceof ASTNumList) ) 
          throw new IllegalArgumentException("Row slicing requires a number-list as the last argument, but found a "+asts[2].getClass());
        ASTNumList rowlist = (ASTNumList)asts[2];

        double[] drows = rowlist.expand();
        long[] rows = new long[drows.length];
        for( int i=0; i<drows.length; i++ ) {
          long row = (long)drows[i];
          if( row!=drows[i] || row < 0 || row >= fr.numRows() ) 
            throw new IllegalArgumentException("Row must be an integer from 0 to "+(fr.numRows()-1));
          rows[i] = row;
        }

        return stk.returning(new ValFrame(fr.deepSlice(rows,null)));
      }
  }
}

