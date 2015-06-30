package water.currents;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

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
      return new ValFrame(new MRTask() {
          @Override public void map( Chunk chk, NewChunk cres ) {
            for( int i=0; i<chk._len; i++ )
              cres.addNum(op(chk.atd(i)));
          }
        }.doAll(1,val.getFrame()).outputFrame());
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
class ASTSin   extends ASTUniOp { String str() { return "sin"  ; } double op(double d) { return Math.sin  (d); } }
class ASTSqrt  extends ASTUniOp { String str() { return "sqrt" ; } double op(double d) { return Math.sqrt (d); } }
class ASTTan   extends ASTUniOp { String str() { return "tan"  ; } double op(double d) { return Math.tan  (d); } }
class ASTTanh  extends ASTUniOp { String str() { return "tanh" ; } double op(double d) { return Math.tanh (d); } }
class ASTTrunc extends ASTUniOp { String str() { return "trunc"; } double op(double d) { return d>=0?Math.floor(d):Math.ceil(d);}}
