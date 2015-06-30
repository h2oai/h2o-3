package water.currents;

import java.util.ArrayList;
import java.util.Arrays;
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
 *  in use rather than creating copies upon copies (a la R).  When a Frame is
 *  `pushed` on to the stack, its reference count is incremented by 1.  When a
 *  Frame is `popped` off of the stack, its reference count is decremented by
 *  1.  When the reference count is 0, the Env instance will dispose of the
 *  object.  All objects live and die by the Env's that create them.  That
 *  means that any object not created by an Env instance shalt not be
 *  DKV.removed.
 *
 *  Therefore, the Env class is a stack of values + an API for reference counting.
 */
public class Env {

  /**
   * The refcnt API.  Looks like a Stack, because lifetimes are stack-like, but
   * just counts refs by unary stack-slot counting.
   */
  private final ArrayList<Frame> _refcnt = new ArrayList<>();
  private final HashSet<Vec> _globals = new HashSet<>();
  public int sp() { return _refcnt.size(); }
  public Frame peek(int x) { return _refcnt.get(sp()+x);  }

  // Deletes dead Frames & forces good stack cleanliness at opcode end.  
  // One per Opcode implementation.
  StackHelp stk() { return new StackHelp(); }
  class StackHelp implements AutoCloseable {
    final int _sp = sp();
    // Push & track.  Called on every Val that spans a (nested) exec call.
    // Used to track Frames with lifetimes spanning other AST executions.
    public Val track(Val v) {
      if( v instanceof ValFrame )
        _refcnt.add(sp(),((ValFrame)v)._fr);
      return v; 
    }
    // If an opcode is returning a Frame, it must call "returning(frame)" to
    // track the returned Frame.  Otherwise shared input Vecs who's last use is
    // in this opcode will get deleted as the opcode exits - even if they are
    // shared in the returning output Frame.
    private Frame _ret_fr;      // Optionally return a Frame on stack scope exit
    public <V extends Val> V returning( V fr ) {
      if( fr instanceof ValFrame )
        _ret_fr = ((ValFrame)fr)._fr;
      return fr;
    }

    // Pop-all and remove dead.  If a Frame was not "tracked" above, then if it
    // goes dead it will leak on function exit.  If a Frame is returned from a
    // function and not declared "returning", any Vecs it shares with Frames
    // that are dying in this opcode will be deleted out from under it.
    @Override public void close() {
      Futures fs = null;
      int i, sp = sp();
      while( sp > _sp ) {
        Frame fr = _refcnt.remove(--sp);
        for( Vec vec : fr.vecs() ) {
          if( _globals.contains(vec) ) continue;
          for( i=0; i<_sp; i++ )
            if( _refcnt.get(i).find(vec) != -1 )
              break;
          if( i==_sp && (_ret_fr==null || _ret_fr.find(vec)== -1) ) {
            if( fs == null ) fs = new Futures();
            vec.remove(fs);
          }
        }
      }
      if( fs != null ) fs.blockForPending();
    }
  }

  // Produce "value" semantics for all top-level Frame returns - which means a
  // true data copy is made of every top-level Vec return.  See if this Vec
  // exists in some pre-existing global.
  boolean isPreExistingGlobal( Vec vec ) {
    return _globals.contains(vec);
  }


  // ----
  // Variable lookup

  ASTFun _scope;                // Current lexical scope lookup

  Val lookup( String id ) {
    // Lexically scoped functions first
    
    Val val = _scope==null ? null : _scope.lookup(id);
    if( val != null ) return val;

    // Now the DKV
    Value value = DKV.get(Key.make(id));
    if( value != null ) {
      if( value.isFrame() ) {
        Frame fr = value.get();
        assert fr._key.toString().equals(id);
        _globals.addAll(Arrays.asList(fr.vecs()));
        return new ValFrame(fr);
      }
      // Only understand Frames right now
      throw new IllegalArgumentException("DKV name lookup of "+id+" yielded an instance of type "+value.className()+", but only Frame is supported");
    }

    // Now the built-ins
    AST ast = AST.PRIMS.get(id);
    if( ast != null )
      return ast instanceof ASTNum ? ast.exec(this) : new ValFun(ast);

    throw new IllegalArgumentException("Name lookup of '"+id+"' failed");
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
//    for (Frame v: _refcnt.keySet()) { ab.putStr(v._key.toString()); ab.put4(_refcnt.get(v)._val); }
//    return ab;
//  }
//
//  @Override public Env read_impl(AutoBuffer ab) {
//    _stack = new ExecStack();
//    _refcnt = new HashMap<>();
//    int len = ab.get4();
//    for (int i = 0; i < len; ++i)
//      _refcnt.put((Frame)DKV.getGet(ab.getStr()), new IcedInt(ab.get4()));
//    return this;
//  }
}
