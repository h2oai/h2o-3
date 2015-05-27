package water.currents;

import water.H2O;
import water.MRTask;
import water.fvec.*;
import water.parser.ValueString;

/** Column slice */
class ASTColSlice extends ASTPrim {
  @Override int nargs() { return 1+2; }
  @Override String str() { return "cols" ; }
  @Override Val apply( Env env, AST asts[] ) {
    try (Env.StackHelp stk = env.stk()) {
        Val vfr  = stk.track(asts[1].exec(env));
        Frame fr = vfr.getFrame();
        if( !(asts[2] instanceof ASTNumList) ) 
          throw new IllegalArgumentException("Column slicing requires a number-list as the last argument, but found a "+asts[2].getClass());
        ASTNumList cols = (ASTNumList)asts[2];

        // Work down the list of columns, picking out the keepers
        Frame fr2 = new Frame();
        double[] bases  = cols._bases  ;
        double[] strides= cols._strides;
        long  [] cnts   = cols._cnts   ;
        for( int i=0; i<bases.length; i++ ) {
          for( double d = bases[i]; d<bases[i]+cnts[i]*strides[i]; d+=strides[i] ) {
            int col = (int)d;
            if( col!=d || d < 0 || d >= fr.numCols() ) 
              throw new IllegalArgumentException("Column must be an integer from 0 to "+(fr.numCols()-1));
            fr2.add(fr.names()[col],fr.vecs()[col]);
          }
        }

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

        double[] bases  = rowlist._bases  ;
        double[] strides= rowlist._strides;
        long  [] cnts   = rowlist._cnts   ;
        // Count total row indices
        int nrows=0, r=0;
        for( int i=0; i<bases.length; i++ ) nrows += cnts[i];
        // Fill in row indices
        long[] rows = new long[(int)nrows];
        for( int i=0; i<bases.length; i++ ) {
          for( double d = bases[i]; d<bases[i]+cnts[i]*strides[i]; d+=strides[i] ) {
            long row = (long)d;
            if( row!=d || d < 0 || d >= fr.numRows() ) 
              throw new IllegalArgumentException("Row must be an integer from 0 to "+(fr.numRows()-1));
            rows[r++] = row;
          }
        }

        return stk.returning(new ValFrame(fr.deepSlice(rows,null)));
      }
  }
}

