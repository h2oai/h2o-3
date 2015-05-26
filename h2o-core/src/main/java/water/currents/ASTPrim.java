package water.currents;

import water.H2O;
import water.MRTask;
import water.fvec.*;
import water.parser.ValueString;

abstract class ASTPrim extends AST {
  final ValFun _fun;
  ASTPrim( ) { _fun = new ValFun(this); }
  @Override Val exec( Env env ) { return _fun; }
}

// Subclasses of this class auto-widen between NUM and VEC
abstract class ASTBinOp extends ASTPrim {
  @Override Val apply( Env env, AST asts[] ) {
    try (Env.StackHelp stk = env.stk()) {
        Val left = stk.track(asts[1].exec(env));
        Val rite = stk.track(asts[2].exec(env));
        return prim_apply(left,rite);
      }
  }

  Val prim_apply( Val left, Val rite ) {
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
        if( !vlf.isNumeric() ) throw new IllegalArgumentException("Cannot mix Numeric and non-Numeric types");
        final double drt = ((ValNum)rite)._d;
        return new ValVec(new MRTask() {
            @Override public void map( Chunk clf, NewChunk cres ) {
              for( int i=0; i<clf._len; i++ )
                cres.addNum(op(clf.atd(i),drt));
            }
          }.doAll(1,vlf).outputFrame().vec(0));

      case Env.VEC:         // Vec op Vec
        Vec vrt = ((ValVec)rite)._vec;
        if( vlf.get_type() != vrt.get_type() ) 
          throw new IllegalArgumentException("Cannot mix types "+vlf.get_type_str()+" and "+vrt.get_type_str());
        return new ValVec(new MRTask() {
            @Override public void map( Chunk clf, Chunk crt, NewChunk cres ) {
              for( int i=0; i<clf._len; i++ )
                cres.addNum(op(clf.atd(i),crt.atd(i)));
            }
          }.doAll(1,vlf,vrt).outputFrame().vec(0));

      case Env.STR:
        if( !vlf.isString() ) throw new IllegalArgumentException("Cannot mix String and non-String types");
        final ValueString srt = new ValueString(((ValStr)rite)._str);
        return new ValVec(new MRTask() {
            @Override public void map( Chunk clf, NewChunk cres ) {
              ValueString vstr = new ValueString();
              for( int i=0; i<clf._len; i++ )
                cres.addNum(str_op(clf.atStr(vstr,i),srt));
            }
          }.doAll(1,vlf).outputFrame().vec(0));

      default: throw H2O.fail();
      }
          
    case Env.STR: throw H2O.unimpl();
    default: throw H2O.fail();
    }
  }
  abstract double op( double l, double r );
  double str_op( ValueString l, ValueString r ) { throw H2O.fail(); }
}

// ----------------------------------------------------------------------------
// Expressions that auto-widen between NUM and VEC
class ASTAnd  extends ASTBinOp { String str() { return "&" ; } double op( double l, double r ) { return ASTLAnd.and_op(l,r); } }
class ASTDiv  extends ASTBinOp { String str() { return "/" ; } double op( double l, double r ) { return l/ r; } }
class ASTMul  extends ASTBinOp { String str() { return "*" ; } double op( double l, double r ) { return l* r; } }
class ASTOr   extends ASTBinOp { String str() { return "|" ; } double op( double l, double r ) { return ASTLOr . or_op(l,r); } }
class ASTPlus extends ASTBinOp { String str() { return "+" ; } double op( double l, double r ) { return l+ r; } }
class ASTSub  extends ASTBinOp { String str() { return "-" ; } double op( double l, double r ) { return l- r; } }

class ASTGE   extends ASTBinOp { String str() { return ">="; } double op( double l, double r ) { return l>=r?1:0; } }
class ASTGT   extends ASTBinOp { String str() { return ">" ; } double op( double l, double r ) { return l> r?1:0; } }
class ASTLE   extends ASTBinOp { String str() { return "<="; } double op( double l, double r ) { return l<=r?1:0; } }
class ASTLT   extends ASTBinOp { String str() { return "<" ; } double op( double l, double r ) { return l< r?1:0; } }
class ASTEQ   extends ASTBinOp { String str() { return "=="; } double op( double l, double r ) { return l==r?1:0; } 
  double str_op( ValueString l, ValueString r ) { return l==null ? (r==null?1:0) : (l.equals(r) ? 1 : 0); } }
class ASTNE   extends ASTBinOp { String str() { return "!="; } double op( double l, double r ) { return l!=r?1:0; } 
  double str_op( ValueString l, ValueString r ) { return l==null ? (r==null?0:1) : (l.equals(r) ? 0 : 1); } }

// ----------------------------------------------------------------------------
// Logical-AND.  If the first arg is false, do not execute the 2nd arg.
class ASTLAnd extends ASTBinOp { 
  String str() { return "&&"; } 
  @Override Val apply( Env env, AST asts[] ) {
    try (Env.StackHelp stk = env.stk()) {
        Val left = stk.track(asts[1].exec(env));
        // If the left is zero or NA, do not evaluate the right, just return the left
        if( left.isNum() ) {
          double d = ((ValNum)left)._d;
          if( d==0 || Double.isNaN(d) ) return left;
        }
        Val rite = stk.track(asts[2].exec(env));
        return prim_apply(left,rite);
      }
  }
  // Weird R semantics, zero trumps NA
  double op( double l, double r ) { return and_op(l,r); }
  static double and_op( double l, double r ) {   
    return (l==0||r==0) ? 0 : (Double.isNaN(l) || Double.isNaN(r) ? Double.NaN : 1); 
  } 
}

// Logical-OR.  If the first arg is true, do not execute the 2nd arg.
class ASTLOr extends ASTBinOp { 
  String str() { return "||"; } 
  @Override Val apply( Env env, AST asts[] ) {
    try (Env.StackHelp stk = env.stk()) {
        Val left = stk.track(asts[1].exec(env));
        // If the left is zero or NA, do not evaluate the right, just return the left
        if( left.isNum() ) {
          double d = ((ValNum)left)._d;
          if( d!=0 || Double.isNaN(d) ) return left;
        }
        Val rite = stk.track(asts[2].exec(env));
        return prim_apply(left,rite);
      }
  }
  // Weird R semantics, zero trumps NA
  double op( double l, double r ) { return or_op(l,r); }
  static double or_op( double l, double r ) {   
    return (l!=0||r!=0) ? 1 : (Double.isNaN(l) || Double.isNaN(r) ? Double.NaN : 0); 
  } 
}
