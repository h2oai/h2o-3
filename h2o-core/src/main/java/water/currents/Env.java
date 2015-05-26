package water.currents;

import java.util.ArrayList;
import java.util.HashSet;

import water.*;
import water.fvec.Frame;
import water.fvec.Vec;

/** Execute a set of instructions in the context of an H2O cloud.
 *
 *  An Env (environment) object is a classic stack of values used during
 *  execution of an AST.  The stack is hidden in the normal Java execution
 *  stack and is not explicit.
 *
 *  For efficiency, reference counting is employed to recycle objects already
 *  in use rather than creating copies upon copies (a la R).  When a Vec is
 *  `pushed` on to the stack, its reference count is incremented by 1.  When a
 *  Vec is `popped` off of the stack, its reference count is decremented by 1.
 *  When the reference count is 0, the Env instance will dispose of the object.
 *  All objects live and die by the Env's that create them.  That means that
 *  any object not created by an Env instance shalt not be DKV.removed.
 *
 *  Therefore, the Env class is a stack of values + an API for reference counting.
 */
public class Env {

  // Things on the execution stack
  final static int NUM   =1;    // scalar
  final static int STR   =2;    // string scalar
  final static int VEC   =3;    // Vec, not a Frame
  final static int FUN   =4;    // Function

  /**
   * The refcnt API.  Looks like a Stack, because lifetimes are stack-like, but
   * just counts refs by unary stack-slot counting.
   */
  private final ArrayList<Vec> _refcnt = new ArrayList<>();
  private final HashSet<Vec> _globals = new HashSet<>();
  public int sp() { return _refcnt.size(); }
  public Vec peek(int x) { return _refcnt.get(sp()+x);  }

  // Deletes dead Vecs & forces good stack cleanliness at opcode end.  
  // One per Opcode implementation.
  StackHelp stk() { return new StackHelp(); }
  class StackHelp implements AutoCloseable {
    final int _sp = sp();
    // Push & track
    public Val track(Val v) {
      if( v instanceof ValVec )
        _refcnt.add(sp(),((ValVec)v)._vec);
      return v; 
    }
    // Pop-all and remove dead
    @Override public void close() {
      Futures fs = null;
      int i, sp = sp();
      while( sp > _sp ) {
        Vec vec = _refcnt.remove(--sp);
        if( _globals.contains(vec) ) continue;
        for( i=0; i<sp; i++ )
          if( _refcnt.get(i)==vec )
            break;
        if( i==sp ) {
          if( fs == null ) fs = new Futures();
          vec.remove(fs);
        }
      }
      if( fs != null ) fs.blockForPending();
    }
  }

  // ----
  // Variable lookup
  Val lookup( String id ) {
    // Lexically scoped functions first
    
    // Not currently implemented

    // Now the DKV
    Value value = DKV.get(Key.make(id));
    if( value != null ) {
      Vec vec = null;
      if( value.isFrame() ) {
        Frame fr = value.get();
        if( fr.numCols()==1 ) vec = fr.anyVec();
      } else if( value.isVec() ) {
        vec = value.get();
      }
      if( vec != null ) {
        //if( vec.length()==1 ) {
        //  if( vec.isString() ) return new ValStr(vec.atStr(0));
        //  else if( vec.isNumeric() ) return new ValNum(vec.at(0));
        //}
        _globals.add(vec);
        return new ValVec(vec);
      }
      // Only understand Vecs right now
      throw new IllegalArgumentException("DKV name lookup of "+id+" yielded an instance of type "+value.className()+", but only Vec is supported");
    }

    // Now the built-ins
    AST ast = AST.PRIMS.get(id);
    if( ast != null ) return new ValFun(ast);

    throw new IllegalArgumentException("Name lookup of "+id+" failed");
  }

  /*
   * Utility & Cleanup
   */

  @Override public String toString() {
    String s="{";
    for( int i=0, sp=sp(); i < sp; i++ ) s += peek(-sp+i).toString()+",";
    return s+"}";
  }

//  @Override public AutoBuffer write_impl(AutoBuffer ab) {
//    // write _refcnt
//    ab.put4(_refcnt.size());
//    for (Vec v: _refcnt.keySet()) { ab.putStr(v._key.toString()); ab.put4(_refcnt.get(v)._val); }
//    return ab;
//  }
//
//  @Override public Env read_impl(AutoBuffer ab) {
//    _stack = new ExecStack();
//    _refcnt = new HashMap<>();
//    int len = ab.get4();
//    for (int i = 0; i < len; ++i)
//      _refcnt.put((Vec)DKV.getGet(ab.getStr()), new IcedInt(ab.get4()));
//    return this;
//  }
}

abstract class Val {
  abstract int type();
  boolean isNum() { return false; }
  boolean isStr() { return false; }
  boolean isVec() { return false; }
  boolean isFun() { return false; }
}

class ValNum extends Val {
  final double _d;
  ValNum(double d) { _d = d; }
  @Override public String toString() { return ""+_d; }
  @Override int type () { return Env.NUM; }
  @Override boolean isNum() { return true; }
}

class ValStr extends Val {
  final String _str;
  ValStr(String str) { _str = str; }
  @Override public String toString() { return _str; }
  @Override int type () { return Env.STR; }
  @Override boolean isStr() { return true; }
}

class ValVec extends Val {
  final Vec _vec;
  ValVec(Vec vec) { _vec = vec; }
  @Override public String toString() { return _vec.toString(); }
  @Override int type () { return Env.VEC; }
  @Override boolean isVec() { return true; }
}

class ValFun extends Val {
  final AST _ast;
  ValFun(AST ast) { _ast = ast; }
  @Override public String toString() { return _ast.toString(); }
  @Override int type () { return Env.FUN; }
  @Override boolean isFun() { return true; }
}
