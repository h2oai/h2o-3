package water.currents;

import java.util.Random;
import water.H2O;
import water.MRTask;
import water.fvec.*;

/**
 * Subclasses auto-widen between scalars and Frames, and have exactly one argument
 */
abstract class ASTUniOp extends ASTPrim {
  @Override int nargs() { return 1+1; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Val val = stk.track(asts[1].exec(env));
    switch( val.type() ) {
    case Val.NUM: return new ValNum(op(val.getNum()));
    case Val.FRM: 
      Frame fr = val.getFrame();
      return new ValFrame(new MRTask() {
          @Override public void map( Chunk cs[], NewChunk ncs[] ) {
            for( int col=0; col<cs.length; col++ ) {
              Chunk c = cs[col];
              NewChunk nc = ncs[col];
              for( int i=0; i<c._len; i++ )
                nc.addNum(op(c.atd(i)));
            }
          }
        }.doAll(fr.numCols(),fr).outputFrame());
    case Val.STR: throw H2O.unimpl();
    default: throw H2O.fail();
    }
  }
  abstract double op( double d );
}

class ASTACos  extends ASTUniOp { String str() { return "acos" ; } double op(double d) { return Math.acos (d); } }
class ASTAbs   extends ASTUniOp { String str() { return "abs"  ; } double op(double d) { return Math.abs  (d); } }
class ASTCeiling extends ASTUniOp{String str() { return "ceiling";}double op(double d) { return Math.ceil (d); } }
class ASTCos   extends ASTUniOp { String str() { return "cos"  ; } double op(double d) { return Math.cos  (d); } }
class ASTCosh  extends ASTUniOp { String str() { return "cosh" ; } double op(double d) { return Math.cosh (d); } }
class ASTExp   extends ASTUniOp { String str() { return "exp"  ; } double op(double d) { return Math.exp  (d); } }
class ASTFloor extends ASTUniOp { String str() { return "floor"; } double op(double d) { return Math.floor(d); } }
class ASTLog   extends ASTUniOp { String str() { return "log"  ; } double op(double d) { return Math.log  (d); } }
class ASTNot   extends ASTUniOp { String str() { return "!!"   ; } double op(double d) { return d==0?1:0; } }
class ASTSin   extends ASTUniOp { String str() { return "sin"  ; } double op(double d) { return Math.sin  (d); } }
class ASTSqrt  extends ASTUniOp { String str() { return "sqrt" ; } double op(double d) { return Math.sqrt (d); } }
class ASTTan   extends ASTUniOp { String str() { return "tan"  ; } double op(double d) { return Math.tan  (d); } }
class ASTTanh  extends ASTUniOp { String str() { return "tanh" ; } double op(double d) { return Math.tanh (d); } }
class ASTTrunc extends ASTUniOp { String str() { return "trunc"; } double op(double d) { return d>=0?Math.floor(d):Math.ceil(d);}}

// Split out in it's own function, instead of Yet Another UniOp, because it
// needs a "is.NA" check instead of just using the Double.isNaN hack... because
// it works on UUID and String columns.
class ASTIsNA  extends ASTPrim { 
  @Override String str() { return "is.na"; } 
  @Override int nargs() { return 1+1; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Val val = stk.track(asts[1].exec(env));
    switch( val.type() ) {
    case Val.NUM: return new ValNum(op(val.getNum()));
    case Val.FRM: 
      Frame fr = val.getFrame();
      return new ValFrame(new MRTask() {
          @Override public void map( Chunk cs[], NewChunk ncs[] ) {
            for( int col=0; col<cs.length; col++ ) {
              Chunk c = cs[col];
              NewChunk nc = ncs[col];
              for( int i=0; i<c._len; i++ )
                nc.addNum(c.isNA(i) ? 1 : 0);
            }
          }
        }.doAll(fr.numCols(),fr).outputFrame());
    case Val.STR: return new ValNum(val.getStr()==null ? 1 : 0);
    default: throw H2O.fail();
    }
  }
  double op(double d) { return Double.isNaN(d)?1:0; } 
}

class ASTRunif extends ASTPrim {
  @Override int nargs() { return 1+2; } // (h2o.runif frame seed)
  @Override String str() { return "h2o.runif"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr  = stk.track(asts[1].exec(env)).getFrame();
    long seed = (long)asts[2].exec(env).getNum();
    if( seed == -1 ) seed = new Random().nextLong();
    return new ValFrame(new Frame(new String[]{"rnd"}, new Vec[]{fr.anyVec().makeRand(seed)}));
  }
}
