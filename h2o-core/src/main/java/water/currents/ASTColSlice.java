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

class ASTRowSlice extends ASTPrim {
  @Override int nargs() { return 1+2; }
  @Override String str() { return "rows" ; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame returningFrame;
    long[] rows;
    if( asts[2] instanceof ASTNumList ) {
      final ASTNumList nums = (ASTNumList)asts[2];
      returningFrame = new MRTask(){
        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
          long start = cs[0].start();
          long end   = start + cs[0]._len;
          double min = nums.min(), max = nums.max();
          //     [ start, ...,  end ]     the chunk
          //1 []                          nums out left:  nums.max() < start
          //2                         []  nums out rite:  nums.min() > end
          //3 [ nums ]                    nums run left:  nums.min() < start && nums.max() <= end
          //4          [ nums ]           nums run in  :  start <= nums.min() && nums.max() <= end
          //5                   [ nums ]  nums run rite:  start <= nums.min() && end < nums.max()
          if( !(max<start || min>end) ) {   // not situation 1 or 2 above
            int startOffset = (int) (min > start ? min : start);  // situation 4 and 5 => min > start;
            for(int i=startOffset;i<cs[0]._len;++i) {
              if( nums.has(start+i) ) { // in
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
      rows = new long[]{(long)(((ASTNum)asts[2])._d.getNum())};
      returningFrame = fr.deepSlice(rows,null);
    } else  throw new IllegalArgumentException("Row slicing requires a number-list as the last argument, but found a "+asts[2].getClass());
    return new ValFrame(returningFrame);
  }
}