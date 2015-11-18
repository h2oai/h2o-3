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

  // Session holds the ref-cnts across multiple executions.
  final Session _ses;
  Env( Session ses ) { _ses = ses; }

  // Frames that are alive in mid-execution; usually because we have evaluated
  // some first expression and need to hang onto it while evaluating the next
  // expression.
  private ArrayList<Frame> _stk  = new ArrayList<>();
  public int sp() { return _stk.size(); }
  private Frame peek(int x) { return _stk.get(sp()+x);  }

  // Deletes dead Frames & forces good stack cleanliness at opcode end.  One
  // per Opcode implementation.  Track frames that are alive mid-execution, but
  // dead at Opcode end.
  StackHelp stk() { return new StackHelp(); }
  class StackHelp implements Closeable {
    final int _sp = sp();
    // Push & track.  Called on every Val that spans a (nested) exec call.
    // Used to track Frames with lifetimes spanning other AST executions.
    public Val track(Val v) {
      if( v instanceof ValFrame ) track(((ValFrame)v)._fr);
      return v; 
    }
    public Frame track(Frame fr) {
      _stk.add(sp(),new Frame(fr._names,fr.vecs().clone())); // Push and track a defensive copy
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
        Frame fr = _stk.remove(--sp); // Pop and stop tracking
        fs = _ses.downRefCnt(fr,fs);  // Refcnt -1 all Vecs, and delete if zero refs
      }
      if( fs != null ) fs.blockForPending();
    }

    // Pop last element and lower refcnts - but do not delete.  Lifetime is
    // responsibility of the caller.
    Val untrack(Val vfr) {
      if( !vfr.isFrame() ) return vfr;
      Frame fr = vfr.getFrame();
      _ses.addRefCnt(fr,-1);           // Lower counts, but do not delete on zero
      return vfr;
    }

  }

  // If an opcode is returning a Frame, it must call "returning(frame)" to
  // track the returned Frame.  Otherwise shared input Vecs who's last use is
  // in this opcode will get deleted as the opcode exits - even if they are
  // shared in the returning output Frame.
  public <V extends Val> V returning( V val ) {
    if( val instanceof ValFrame )
      _ses.addRefCnt(((ValFrame)val)._fr,1);
    return val;
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

  // Add these Vecs to the global list, and make a new defensive copy of the
  // frame - so we can hack it without changing the global frame view.
  ValFrame addGlobals( Frame fr ) {
    _ses.addGlobals(fr);
    return new ValFrame(new Frame(fr._names.clone(),fr.vecs().clone()));
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
    //ab.put4(_globals.size());   for( Vec vec : _globals ) ab.put(vec._key);
    //ab.put4(sp());              for( Frame fr : _stk ) ab.put(fr);
    //return ab;
    throw water.H2O.unimpl();
  }

  @Override public Env read_impl(AutoBuffer ab) {
    //_globals = new HashSet<>();
    //int len=ab.get4();
    //for( int i=0; i<len; i++ )
    //  _globals.add((Vec)DKV.getGet((Key)ab.get()));
    //_stk = new ArrayList<>();
    //len=ab.get4();
    //for( int i=0; i<len; i++ )
    //  _stk.add((Frame)ab.get());
    //return this;
    throw water.H2O.unimpl();
  }
}
