package water.currents;

import water.H2O;
import water.MRTask;
import water.fvec.Vec;
import water.fvec.Chunk;
import water.fvec.NewChunk;

abstract class ASTPrim extends AST {
  ASTPrim() { super(null); }
}

abstract class ASTBinOp extends ASTPrim {
  @Override Val exec( Env env ) {
    try( Env.StackHelp stk = env.stk()) {
        Val rite = stk.pop();
        Val left = stk.pop();

        switch( left.type() ) {
        case Env.NUM: 
          final double dlf = ((ValNum)left)._d;

          switch( rite.type() ) {
          case Env.NUM:         // scalar op scalar
            final double drt = ((ValNum)rite)._d;
            return new ValNum(op(dlf,drt));

          case Env.VEC:         // scalar op Vec
            Vec vrt = ((ValVec)rite)._vec;
            return new ValVec(new MRTask() {
                @Override public void map( Chunk crt, NewChunk cres ) {
                  for( int i=0; i<crt._len; i++ )
                    cres.addNum(op(dlf,crt.atd(i)));
                }
              }.doAll(1,vrt).outputFrame().vec(0));

          case Env.STR: throw H2O.unimpl();
          default: throw H2O.fail();
          }

        case Env.VEC: 
          Vec vlf = ((ValVec)left)._vec;

          switch( rite.type() ) {
          case Env.NUM:         // Vec op scalar
            final double drt = ((ValNum)rite)._d;
            return new ValVec(new MRTask() {
                @Override public void map( Chunk clf, NewChunk cres ) {
                  for( int i=0; i<clf._len; i++ )
                    cres.addNum(op(clf.atd(i),drt));
                }
              }.doAll(1,vlf).outputFrame().vec(0));

          case Env.VEC:         // Vec op Vec
            Vec vrt = ((ValVec)rite)._vec;
            return new ValVec(new MRTask() {
                @Override public void map( Chunk clf, Chunk crt, NewChunk cres ) {
                  for( int i=0; i<clf._len; i++ )
                    cres.addNum(op(clf.atd(i),crt.atd(i)));
                }
              }.doAll(1,vlf,vrt).outputFrame().vec(0));

          case Env.STR: throw H2O.unimpl();
          default: throw H2O.fail();
          }
          
        case Env.STR: throw H2O.unimpl();
        default: throw H2O.fail();
        }
      }
  }
  abstract protected double op( double left, double rite );
}

// 
class ASTPlus extends ASTBinOp {
  static { init(new ASTPlus()); }
  @Override public String toString() { return "+"; }
  @Override protected double op( double left, double rite ) { return left+rite; }
}
