package water.rapids;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import water.AutoBuffer;
import water.DKV;
import water.Futures;
import water.Iced;
import water.Key;
import water.Value;
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
public class Env extends Iced {

  /** The refcnt API.  Looks like a Stack, because lifetimes are stack-like,
   *  but just counts refs by unary stack-slot counting.  */

  // Always alive; supercedes _refcnt
  private HashSet<Vec> _globals = new HashSet<>();
  // Mentioned, but dead at function exit
  private ArrayList<Frame> _refcnt  = new ArrayList<>();
  public int sp() { return _refcnt.size(); }
  public Frame peek(int x) { return _refcnt.get(sp()+x);  }

  // Deletes dead Frames & forces good stack cleanliness at opcode end.  
  // One per Opcode implementation.
  StackHelp stk() { return new StackHelp(); }
  class StackHelp implements Closeable {
    final int _sp = sp();
    // Alive on this function exit (but perhaps not some outer scope)
    private Frame _ret_fr;      // Optionally return a Frame on stack scope exit
    // Push & track.  Called on every Val that spans a (nested) exec call.
    // Used to track Frames with lifetimes spanning other AST executions.
    public Val track(Val v) {
      if( v instanceof ValFrame ) track(((ValFrame)v)._fr);
      return v; 
    }
    public Frame track(Frame fr) {
      _refcnt.add(sp(),fr);
      return fr; 
    }
    // If an opcode is returning a Frame, it must call "returning(frame)" to
    // track the returned Frame.  Otherwise shared input Vecs who's last use is
    // in this opcode will get deleted as the opcode exits - even if they are
    // shared in the returning output Frame.
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
      int sp = sp();
      while( sp > _sp ) {
        Frame fr = _refcnt.remove(--sp);
        for( Vec vec : fr.vecs() )
          if( !inUse(vec) ) {
            if( fs == null ) fs = new Futures();
            vec.remove(fs);
          }
      }
      if( fs != null ) fs.blockForPending();
    }

    // True if this Vec is alive on the current execution stack somewhere, not
    // counting the current stack frame.
    boolean inUse(Vec vec) {
      if( isPreExistingGlobal(vec) ) return true;
      for( int i=0; i<_sp; i++ )
        if( _refcnt.get(i).find(vec) != -1 )
          return true;
      return _ret_fr!=null && _ret_fr.find(vec)!= -1;
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
      if( value.isFrame() )
        return addGlobals((Frame)value.get());
      // Only understand Frames right now
      throw new IllegalArgumentException("DKV name lookup of "+id+" yielded an instance of type "+value.className()+", but only Frame is supported");
    }

    // Now the built-ins
    AST ast = AST.PRIMS.get(id);
    if( ast != null )
      return ast instanceof ASTNum ? ast.exec(this) : new ValFun(ast);

    throw new IllegalArgumentException("Name lookup of '"+id+"' failed");
  }

  ValFrame addGlobals( Frame fr ) {
    _globals.addAll(Arrays.asList(fr.vecs()));
    return new ValFrame(fr);
  }

  /*
   * Utility & Cleanup
   */

  @Override public String toString() {
    String s="{";
    for( int i=0, sp=sp(); i < sp; i++ ) s += peek(-sp+i).toString()+",";
    return s+"}";
  }

  @Override public AutoBuffer write_impl(AutoBuffer ab) {
    ab.put4(_globals.size());   for( Vec vec : _globals ) ab.put(vec._key);
    ab.put4(sp());              for( Frame fr : _refcnt ) ab.put(fr);
    return ab;
  }

  @Override public Env read_impl(AutoBuffer ab) {
    _globals = new HashSet<>();
    int len=ab.get4();
    for( int i=0; i<len; i++ )
      _globals.add((Vec)DKV.getGet((Key)ab.get()));
    _refcnt = new ArrayList<>();
    len=ab.get4();
    for( int i=0; i<len; i++ )
      _refcnt.add((Frame)ab.get());
    return this;
  }
}
