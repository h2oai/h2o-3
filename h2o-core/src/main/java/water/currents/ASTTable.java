package water.currents;

import water.fvec.Vec;
import water.fvec.Frame;

/** Variance between columns of a frame */
class ASTTable extends ASTPrim {
  @Override int nargs() { return -1; } // (table X)  or (table X Y)
  @Override public String str() { return "table"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr1 = stk.track(asts[1].exec(env)).getFrame();
    Frame fr2 = asts.length==3 ? stk.track(asts[2].exec(env)).getFrame() : null;
    if( !(asts.length == 2 || asts.length == 3) || (fr1.numCols() + (fr2==null ? 0 : fr2.numCols())) > 2 )
      throw new IllegalArgumentException("table expects one or two columns");

    Vec vec1 = fr1.vec(0);
    if( fr2==null && fr1.numCols()==1 ) return one_col(vec1);

    Vec vec2 = fr1.numCols()==2 ? fr1.vec(1) : fr2.vec(0);
    return two_col(vec1,vec2);
  }

  private ValFrame one_col( Vec v1 ) {
    if( v1.isInt() ) {
      throw water.H2O.unimpl();


      //Vec vec = Vec.makeVec(dom,null,Vec.VectorGroup.VG_LEN1.addVec());
      //return new ValFrame(new Frame(new String[]{"Counts"},new Vec[]{vec}));
    }

    throw water.H2O.unimpl();
  }

  private ValFrame two_col( Vec v1, Vec v2 ) {
    throw water.H2O.unimpl();
  }
}
