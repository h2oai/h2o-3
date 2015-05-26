package water.currents;

import java.util.ArrayList;
import java.util.HashMap;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.IcedInt;

/** Execute a set of instructions in the context of an H2O cloud.
 *
 *  An Env (environment) object is a classic stack of values used during
 *  execution of an AST.  
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
  final static int NULL  =0;    // missing
  final static int NUM   =1;    // scalar
  final static int STR   =2;    // string scalar
  final static int VEC   =3;    // Vec, not a Frame
  final static int FUN   =4;    // Function
  final static String[] TYPE_NAMES = new String[] { "null", "num", "str", "vec", "fun" };

  /**
   * The RefCnt API
   */
  // Vecs on the stack, or held on to in the local names space are refcnt'd.
  // When counts go to zero, they get deleted - when op_end() is called.
  private final HashMap<Vec,IcedInt> _refcnt = new HashMap<>(); // Ref Counts for each vector

  // Add a refcnt
  private Vec addRef( Vec vec ) {
    IcedInt I = _refcnt.get(vec);
    assert I == null || I._val >= 0;
    _refcnt.put(vec, new IcedInt(I == null ? 1 : I._val + 1));
    return vec;                 // Flow coding
  }

  // Lower a refcnt - does NOT delete on zero counts
  private int subRef( Vec vec ) {
    int cnt = _refcnt.get(vec)._val - 1;
    assert cnt >= 0;
    _refcnt.put(vec, new IcedInt(cnt));
    return cnt;                 // Flow coding
  }
  
  /**
   * The stack API
   */
  private final ArrayList<Val> _stack = new ArrayList<>();
  public int sp() { return _stack.size(); }
  public boolean isEmpty() { return _stack.isEmpty(); }
  public Val peek(int x) { return _stack.get(sp()+x);  }
  public Val peek() { return peek(-1); }
  // ?!?!?! No push & pop here!!!!
  // Use an instance of StackHelp to do push & pop

  // One per Opcode implementation.  Forces good stack cleanliness at opcode end.
  StackHelp stk() { return new StackHelp(); }
  class StackHelp implements AutoCloseable {
    // A set of Vecs, whose refcnts hit zero recently
    private ArrayList<Vec> _mayBeDead = null;
    // Push, raise refcnts
    public Val push(Val v) {
      if( v.isVec() )
        addRef(((ValVec)v)._vec);
      _stack.add(sp(),v);
      return v; 
    }

    // Pop, lower refcnts
    public Val pop() { 
      Val v = _stack.remove(sp()-1);
      if( v.isVec() ) {
        Vec vec = ((ValVec)v)._vec;
        if( subRef(vec) == 0 ) {
          if( _mayBeDead==null ) _mayBeDead = new ArrayList<>();
          _mayBeDead.add(vec); // If refcnt goes zero, even temporarily, record it
        }
      }
      return v;
    }

    // Remove all Vecs who's refcnt goes to zero
    @Override public void close() {
      if( _mayBeDead == null ) return;
      Futures fs = null;
      for( Vec vec : _mayBeDead ) {
        int cnt = _refcnt.get(vec)._val;
        if( cnt <=0 ) {
          if( fs == null ) fs = new Futures();
          vec.remove(fs);
          _refcnt.remove(vec);
        }
      }
      if( fs != null ) fs.blockForPending();
    }
  }

  // Variable lookup
  Val lookup( String id ) {
    // Lexically scoped functions first

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
  Val exec(Env env) { return this; }
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
  final String _s;
  ValStr(String s) { _s = s; }
  @Override public String toString() { return _s; }
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
