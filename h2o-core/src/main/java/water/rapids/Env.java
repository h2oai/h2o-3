package water.rapids;

import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.fvec.EnumWrappedVec;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.IcedHashMap;
import water.util.IcedInt;
import water.util.Log;

import java.util.*;

/** Execute a set of instructions in the context of an H2O cloud.
 *
 *  An Env (environment) object is a classic stack of values used during walking of an AST. While walking the syntax tree
 *  new scopes may be encountered, and each new scope will inherit from the caller's scope. All scopes have a common
 *  ancestor as the global scope.
 *
 *  For efficiency, reference counting is employed to recycle objects already in use rather than creating copies upon
 *  copies (a la R). When a Vec is `pushed` on to the stack, its reference count is incremented by 1. When a Vec is
 *  `popped` off of the stack, its reference count is decremented by 1. When the reference count is 0, the Env instance
 *  will dispose of the object. All objects live and die by the Env's that create them. That means that any object not
 *  created by an Env instance shalt not be DKV.removed.
 *
 *  Therefore, the Env class is a stack of values + an API for reference counting.
 */
public class Env extends Iced {

  final static int ID    =0;
  final static int ARY   =1;
  final static int STR   =2;
  final static int NUM   =3;
  final static int FUN   =4;
  final static int SPAN  =5;
  final static int SERIES=6;
  final static int VEC   =8;
  final static int LIST  =9;
  final static int LARY  =10;
  final static int NULL  =99999;

  transient ExecStack _stack;                // The stack
  transient HashMap<Vec,IcedInt> _refcnt;    // Ref Counts for each vector
  transient final public StringBuilder _sb;  // Holder for print results
  transient final HashSet<Key> _locked;      // Vec keys, these shalt not be DKV.removed.
  final SymbolTable  _global;
  SymbolTable  _local;
  final Env _parent;
  final private boolean _isGlobal;

  transient HashSet<ValFrame> _trash;
  transient HashSet<Frame>    _tmpFrames;  // cleanup any tmp frames made by new ASTString

  @Override public AutoBuffer write_impl(AutoBuffer ab) {
    // write _refcnt
    ab.put4(_refcnt.size());
    for (Vec v: _refcnt.keySet()) { ab.put(v); ab.put4(_refcnt.get(v)._val); }
    return ab;
  }

  @Override public Env read_impl(AutoBuffer ab) {
    _refcnt = new HashMap<>();
    _stack = new ExecStack();
    _trash = new HashSet<>();
    int len = ab.get4();
    for (int i = 0; i < len; ++i) {
      _refcnt.put(ab.get(Vec.class), new IcedInt(ab.get4()));
    }
    return this;
  }

  // Top-level Env object: This is the global Env object. To determine if we're in the global scope, _parent == null
  // and _local == null will always be true. The parent of a scope is the calling scope. All scopes inherit from the
  // global scope.
  Env(HashSet<Key> locked) {
    _stack  = new ExecStack();
    _refcnt = new HashMap<>();
    _sb     = new StringBuilder();
    _locked = locked;
    _global = new SymbolTable();
    _local  = null;
    _parent = null;
    _isGlobal = true;
    _trash = new HashSet<>();
    _tmpFrames = new HashSet<>();
  }

  // Capture the current environment & return it (for some closure's future execution).
  // the only thing captured is the reference counts of live Vecs... everything else is new'd
  Env capture() { return new Env(this); }
  private Env(Env e) {
    _stack  = new ExecStack();     // nope want a new stack!
    _refcnt = new HashMap<>();     // new ref counter... however, don't add vecs if they are tracked in some parent scope!
    _sb     = new StringBuilder(); // just for pretty printing in debug mode
    _locked = new HashSet<>();     // brand new set of locked things -- must check up the nest for locked things
    _global = null;                // not a global ST
    _local  = new SymbolTable();   // new symbol table for me!
    _parent = e;                   // ok i wanna know who my parent is, yes. parent has all of the goodies like TRUE, FALSE, E, NA, INF, PI, etc...
    _isGlobal = false;             // am not global, but multiple ways to check this!
    _trash = new HashSet<>();      // don't want my parent scope's trash!
    _tmpFrames = new HashSet<>();
  }

  // makes a new "global" context -- useful for one off invocations
  static Env make(HashSet<Key> locked) {
    Env env = new Env(locked);
    // some default items in the symbol table
    env.put("TRUE",  Env.NUM, "1"); env.put("T", Env.NUM, "1");
    env.put("FALSE", Env.NUM, "0"); env.put("F", Env.NUM, "0");
    env.put("NA",  Env.NUM, Double.toString(Double.NaN));
    env.put("Inf", Env.NUM, Double.toString(Double.POSITIVE_INFINITY));
    env.put("-Inf",Env.NUM, Double.toString(Double.NEGATIVE_INFINITY));
    env.put("E",Env.NUM, Double.toString(Math.E));
    env.put("PI",Env.NUM, Double.toString(Math.PI));
    return env;
  }

  public boolean isGlobal() { return _isGlobal && _parent == null && _local == null; }

  public static String typeToString(int type) {
    switch(type) {
      case ID: return "ID";
      case ARY: return "ARY";
      case STR: return "STR";
      case NUM: return "NUM";
      case FUN: return "FUN";
      case SPAN: return "SPAN";
      case SERIES: return "SERIES";
      case VEC: return "VEC";
      case NULL: return "NULL";
      default: return  "No type for number: " + type;
    }
  }


  /**
   * The stack API
   */
  public int sp() { return _stack._head + 1; }

  public void push(Val o) {
    if (o instanceof ValFrame) {
      ValFrame f = (ValFrame)o;
      if( !_isGlobal ) {
        assert f._key==null || DKV.get(f._key)!=null;  // have a local Frame we're pushing -- not in the DKV yet!
        _local.put(Key.make().toString(), f._fr, false /* any pushed Val _here_ is no longer isFrame */);
      }
      addRef(f);
    }
    _stack.push(o);
    clean();
  }

  public void pushAry(Frame fr) { push(new ValFrame(fr)); }

  public boolean isEmpty() { return _stack.isEmpty(); }
  public Val peek() { return _stack.peek(); }
  public Val peekAt(int i) { return _stack.peekAt(i); }
  public int peekType() {return _stack.peekType(); }
  public Frame peekAryAt(int i) {
    try {
      return ((ValFrame) _stack.peekAt(i))._fr;
    } catch(ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Bad input: Expected input to be a Frame.");
    }
  }
  public int peekTypeAt(int i) { return _stack.peekTypeAt(i); }
  public boolean isAry() { return peekType() == ARY; }
  public boolean isNum() { return peekType() == NUM; }
  public boolean isStr() { return peekType() == STR; }
  public boolean isId () { return peekType() == ID;  }
  public boolean isFun() { return peekType() == FUN; }
  public boolean isNul() { return peekType() == NULL;}
  public boolean isSpan(){ return peekType() == SPAN;}
  public boolean isSeries(){ return peekType() == SERIES;}


  public Val pop() { return _stack.pop(); }
  public void pop(int n) { for (int i = 0; i < n; ++i) pop(); }
  public void poppush(int n, Val v) { pop(n); push(v);}
  public Frame popAry () { return ((ValFrame)pop())._fr; }
  public double popDbl() { return ((ValNum)pop())._d;    }
  public String popStr() {
    Val v = pop();
    if( v instanceof ValStr ) return ((ValStr)v)._s;
    else if( v instanceof ValFrame ) return ((ValFrame)v)._fr._key.toString();
    else throw new IllegalASTException("shouldn't be here.");
  }
  public ValSeries popSeries() {return (ValSeries)pop(); }
  public ValSpan popSpan() { return (ValSpan)pop();      }
  public Frame peekAry() {return ((ValFrame)peek())._fr; }
  public double peekDbl() {return ((ValNum)peek())._d;   }
  public AST pop2AST() {
    if (isAry()) return new ASTFrame(popAry());
    if (isNum()) return new ASTNum(popDbl());
    if (isStr()) return new ASTString('\"', popStr());
    if (isNul()) {pop(); return new ASTNull(); }
    throw new IllegalArgumentException("Invalid use of pop2AST. Got bad type: "+peekType());
  }

  public void toss(ValFrame f) { _trash.add(f); }
  public synchronized void clean() {
    if( _trash == null ) return;
    for( ValFrame f : _trash )
      if( !f._g ) cleanup(f._fr);
    _trash.clear();
  }

  /**
   *  Reference Counting API
   */
  public void addRef(ValFrame o) { addRef(o._fr); }
  public void addRef(Frame f) { for (Vec v : f.vecs()) addRef(v); }
  public void addRef(Vec v) {
    if( inScope(v) ) {
      IcedInt I = _refcnt.get(v);
      assert I == null || I._val >= 0;
      _refcnt.put(v, new IcedInt(I == null ? 1 : I._val + 1));
      if (v instanceof EnumWrappedVec) {
        Vec mv = ((EnumWrappedVec) v).masterVec();
        IcedInt Imv = _refcnt.get(mv);
        assert Imv == null || Imv._val >= 0;
        _refcnt.put(mv, new IcedInt(Imv == null ? 1 : Imv._val + 1));
      }
    }
  }

  // Vecs are only tracked in the scope that they are created in.
  // thou shalt not kill vecs belonging to some parent scope
  // => don't bother reference counting them here since the counts are always > 0
  private boolean inScope(Vec v) {
    // recurse up the parent scopes,
    // if all null, then return true (means we have a new vec to reference count in THIS scope)
    // if the vec is in some parent scope, return false -- will not be adding reference counts today!
    Env e = _parent;
    boolean in=false; // assume not in parent.
    while( e!=null ) {
      in |= e._refcnt.containsKey(v); // _refcnt is always non-null
      e = e._parent;
    }
    return _refcnt.containsKey(v) || !in;
  }

  // check that this scope and no parent scope has a "lock" on this key.
  // "locked" means shalt not be DKV removed.
  public boolean hasLock(Key k) {
    // recurse up the parent scopes,
    // return true if any scope has a lock
    // otherwise return false.
    boolean locked=_locked!=null&&_locked.contains(k);
    if( _parent != null ) locked |= _parent.hasLock(k);
    return locked;
  }

  public void lock(Key k) { _locked.add(k); }
  public void lock(Frame fr) { for (Vec v : fr.vecs()) lock(v); lock(fr._key); }
  public void lock(Vec v) { lock(v._key); }
  public void lock(String k) { lock(Key.make(k)); }
  public Key  lock() { Key k=Key.make(); _locked.add(k); return k; }  // return a new key that is locked

  private void subRef(Val o) {
    assert o instanceof ValFrame;
    boolean delete=true;
    Frame f = ((ValFrame)o)._fr;

    // subRef vecs that may (not) exist in DKV
    for( Vec v : f.vecs() ) delete &= subRef(v);

    // delete a Frame if delete is true
    if( delete )
      if( f._key != null && !hasLock(f._key) )
        f.delete();
  }

  // subRef on a single vec, walk all Env objects to decide whether to kill the Vec or not.
  //
  // return false if the vec is not deleted via removeVec
  // return true otherwise
  public boolean subRef(Vec v) {
    if( v == null ) return false;
    boolean delete;

    if( _refcnt.get(v) == null ) return false;
    if( hasLock(v._key) ) return false;
    int cnt = _refcnt.get(v)._val - 1;
    if( cnt > 0 ) {
      _refcnt.put(v, new IcedInt(cnt));
      delete = false;
    } else {
      // safe to remove!;
      extinguishCounts(v);
      delete = true;
    }
    if (delete) removeVec(v, null);
    return delete;
  }
  public void subRef(Frame f) { for (Vec v : f.vecs()) subRef(v); }

  static Futures removeVec(Vec v, Futures fs) {
    if (fs == null) {
      fs = new Futures();
      Keyed.remove(v._key, fs);
      fs.blockForPending();
      return null;
    } else {
      Keyed.remove(v._key, fs);
      return fs;
    }
  }

  private void extinguishCounts(Object o) {
    if (o instanceof Vec) { _refcnt.remove(o);}
    else  for(Vec v: ((Frame) o).vecs()) _refcnt.remove(v);
  }

  /*
   * Utility & Cleanup
   */

  // Done writing into all things.  Allow rollups.
  public void postWrite() {
    Futures fs = new Futures();
    for( Vec vec : _refcnt.keySet() )
      vec.postWrite(fs);
    fs.blockForPending();
  }

  public void remove_and_unlock() {
    while(!_stack.isEmpty()) {
      int type = peekType();
      switch(type) {
        case ARY: remove(peek(), false); break;
        default : pop(); break;
      }
    }

    Futures fs = new Futures();
    for (Vec v : _refcnt.keySet()) {
      if (_refcnt.get(v)._val == 0) {
        removeVec(v, fs);
      }
    }
    fs.blockForPending();

    for(Frame f: _tmpFrames) {if(f._key!=null) DKV.remove(f._key); } // top level removal only (Vecs may be live still)
  }

  public void unlock() {
    while(!_stack.isEmpty()) {
      if (_stack.peekType() == ARY) {
        Frame fr = ((ValFrame)_stack.pop())._fr;
        if (fr._lockers != null && lockerKeysNotNull(fr)) fr.unlock_all();
      } else _stack.pop();
    }
  }

  void remove(Object o, boolean popped) {
    assert o instanceof ValFrame || o instanceof Frame || o == null;
    if (o == null) return;
    if (o instanceof ValFrame) remove_and_unlock(((ValFrame)o)._fr);
    else remove_and_unlock((Frame)o);
    if(!popped) pop();
  }

  void cleanup(Frame f) {
    if( f == null ) return;
    if (f._lockers != null && lockerKeysNotNull(f)) f.unlock_all();
    subRef(new ValFrame(f));
  }

  // nuking the current scope. Frames in the symbol table get nuked.
  // reference counter also gets a once-over
  //
  // what happens when there's a result being returned to parent scope?
  // must transfer reference counts for that result back to the parent.
  // only ever have a SINGLE result on the stack ... multiple results is an error.
  void popScope() {
    if( _parent==null ) throw new IllegalArgumentException("Cannot pop the parent scope!");

    Key k = isAry()?peekAry()._key:null;   // shouldn't be null...

    // kill any local frames...
    Set<String> local = _local._table.keySet();
    for( String name : local ) {
      Frame f;
      if( _local.getType(name)==LARY ) {
        f=_local.getFrame(name)._fr;
        if( isAry() && f==peekAry() ) continue;
        else cleanup(f);
      }
    }
    _local.clear();
    for( Key key: _locked ) {
      if( k!=null && k==key ) continue;
      if( isAry() && Arrays.asList(peekAry().keys()).contains(key)) continue;
//      Keyed.remove(key);
    }
    _locked.clear();
    for( Vec v: _refcnt.keySet()) {
      if( _refcnt.get(v)._val==0 && !hasLock(v._key) ) Keyed.remove(v._key); // no lock and zero counts, nuke it.
    }
  }

  private void remove_and_unlock(Frame fr) {
    extinguishCounts(fr);
    if (fr._lockers != null && lockerKeysNotNull(fr)) fr.unlock_all();
    if( anyLocked(fr) ) return;
    fr.delete();
  }

  private boolean lockerKeysNotNull(Frame f) {
    for (Key k : f._lockers)
      if (k == null) return false;
    return true;
  }

  private boolean anyLocked(Frame fr) {
    if( hasLock(fr._key) ) return true;
    for (Vec v : fr.vecs())
      if( hasLock(v._key) ) return true;
    return false;
  }

  public String toString(int i) {
    int type = peekTypeAt(i);
    Object o = peekAt(i);
    switch(type) {
      case ARY:  return ((ValFrame)o)._fr.numRows()+"x"+((ValFrame)o)._fr.numCols();
      case NUM:  return Double.toString(((ValNum)o)._d);
      case STR:  return ((ValStr)o)._s;
      case ID :  return ((ValId)o)._id;
      case SERIES: return o.toString();
      case SPAN: return o.toString();
      case NULL: return "null";
      default: throw H2O.unimpl("Bad value on the stack: " + type);
    }
  }

  @Override public String toString() {
    int sp = sp();
    String s="{";
    for( int i=-sp+1; i <= 0; i++ ) s += toString(i)+",";
    return s+"}";
  }

  /** Stack interface for the ExecStack
   *
   * Allowed Objects:
   *   -Strings
   *   -Frames
   *   -Vecs
   *   -doubles, ints, floats, etc.
   */
  private interface Stack {
    Val     peek();
    Val     peekAt(int i);
    Val     pop();
    void    push(Val t);
    boolean isEmpty();
    int     size();
    int     peekType();
    int     peekTypeAt(int i);
  }

  private class ExecStack implements Stack {
    private final ArrayList<Val> _stack;
    private int _head;

    private ExecStack() {
      _stack = new ArrayList<>();
      _head  = -1;
    }

    /**
     * Peek the top of the stack
     * @return the Object at the `_head` of the stack
     */
    @Override public Val peek() {
      if (isEmpty()) return null;
      return _stack.get(_head);
    }

    /**
     * Peek the stack at position passed in (does error checking on the position)
     * @param i The position at which to peek the stack
     * @return the Object at position `i`.
     */
    @Override public Val peekAt(int i) {

      // Another check just in case assertions aren't on.
      if (i <= 0) {
        i = _head + i;
        if (i < 0) throw new IllegalArgumentException("Trying to peekAt a negative position in the stack: "+i);
      }

      // The stack may be empty
      if (isEmpty()) return null;

      // The requested index may be greater than _head (points to the top of the stack)
      if (i > _head) {
        Log.warn("peekAt("+i+"): i is greater than the top of the stack: "+_head+"<"+i);
        return null;
      }

      // The requested index may be greater than the size of the stack (size() == _head if not empty),
      // and it's good to check anyways for debugging and extra logging. This will also assert that the _head and
      // stack sizes are aligned.
      if (i > size()) {
        Log.warn("peekAt("+i+"): i is greater than the size of the stack: "+size()+"<"+i);
        return null;
      }

      // Return the Val at position i
      return _stack.get(i);
    }

    /**
     * Peek the type of the object at the top of the stack. Does not pop!
     * @return an int representing the type of the object at the top of the stack
     */
    @Override public int peekType() { return getType(peek()); }

    /**
     * Peek the tpe of the object at position `i` in the stack. Does not pop!
     * @param i The position at which to peek the stack
     * @return an int representing the type of the object at position `i` in the stack.
     */
    @Override public int peekTypeAt(int i) { return getType(peekAt(i)); }

    private int getType(Val o) {
      if( o instanceof ValNull || o==null  )    return NULL;
      if( o instanceof ValId     )    return ID;
      if( o instanceof ValFrame  )    return ARY;
      if( o instanceof ValStr    )    return STR;
      if( o instanceof ValNum    )    return NUM;
      if( o instanceof ValSpan   )    return SPAN;
      if( o instanceof ValSeries )    return SERIES;
      if( o instanceof ValLongList)   return LIST;
      if( o instanceof ValStringList) return LIST;
      if( o instanceof ValDoubleList) return LIST;
      throw H2O.unimpl("Got a bad type on the ExecStack: Object class: "+ o.getClass()+". Not a Frame, String, Double, Fun, Span, or Series");
    }

    /**
     * Is the stack empty?
     * @return true if empty, false otherwise
     */
    @Override public boolean isEmpty() { return _head == -1; }

    /**
     * Get the size of the stack.
     * @return the number of Objects sitting in the stack. Assert that the number of Objects is aligned with `_head`.
     */
    @Override public int size() {
      if (!isEmpty()) {
        // Choice to add _head + 1, but when asserts stacksize - 1 because want to know where the _head is at!
        assert _stack.size() == _head + 1 : "The stack size and the pointer to the top are out of alignment! Stack size: " + (_stack.size() - 1) + ", _head: " + _head;
        return _stack.size();
      }
      return -1;
    }

    /**
     * Pop one of the top of the stack.
     * @return the Object sitting at the top of the stack
     */
    @Override public Val pop() {
      if (isEmpty()) return null;
      Val o = peek();
      if( o instanceof ValStr ) {
        AST f = staticLookup( new ASTString('\"', ((ValStr)o)._s));
        if( f instanceof ASTFrame) {
          _stack.remove(_head--);
          f.exec(Env.this);
          return pop();
        }
      }
      _stack.remove(_head--);
      if (o instanceof ValFrame) toss((ValFrame)o);
      return o;
    }

    /**
     * Pop all of the values off the stack.
     * @return void
     */
    public void popAll() {
      if (isEmpty()) return;
      while(size() != -1) {
        Val v = pop();
        if (v instanceof ValFrame) ((ValFrame)v)._fr.unlock_all();
      }
    }

    /**
     * Push an Object onto the stack
     * @param t is the Val to be pushed onto the stack.
     */
    @Override public void push(Val t) {
      _head++;
      _stack.add(_head, t);
    }
  }

  /**
   * Map identifiers to types & values.
   *
   * <NAME, <ATTRIBUTES>> is the pair this class works with.
   */
  class SymbolTable<T extends Iced> extends Iced<T> {
    IcedHashMap<String, T> _table;
    public SymbolTable() { _table = new IcedHashMap<>(); }
    void clear() { _table.clear(); }
    public void put(String name, int type, String value) {
      if (_table.containsKey(name)) {
        write(name, type);
        write(name, value);
      } else {
        SymbolAttributes attributes = new SymbolAttributes(type, value);
        _table.put(name, (T)attributes);
      }
    }
    public void put(String name, Frame localFrame, boolean isFrame) {
      ASTFrame fr = new ASTFrame(localFrame);
      fr.isFrame = isFrame;  // was it a Vec or a Frame?
      _table.put(name, (T)fr);
    }
    public T get(String name) {
      if( !_table.containsKey(name) ) return null;
      return _table.get(name);
    }
    public int getType(String name) {
      if (!_table.containsKey(name)) return NULL;
      T V = get(name);
      if( V instanceof ASTFrame ) return LARY;  // special local array, all others come from the DKV!
      else return ((SymbolAttributes)V)._type;
    }
    public String getValue(String name) {  // caller knows exactly the type.
      if( !_table.containsKey(name) ) return null;
      T V = get(name);
      try {
        return ((SymbolAttributes) V)._value;
      } catch( ClassCastException e ) {
        throw new ClassCastException("API Error in Symbol Attributes. Caller expected SymbolAttributes but got " + V.getClass());
      }
    }
    public ASTFrame getFrame(String name) {
      if( !_table.containsKey(name) ) return null;
      T V = get(name);
      try {
        return (ASTFrame)V;
      } catch( ClassCastException e ) {
        throw new ClassCastException("API Error in Symbol Attributes. Caller expected Frame but got " + V.getClass());
      }
    }

    private void write(String name, int type) {
      SymbolAttributes attrs = (SymbolAttributes)get(name);
      attrs.write(type);
    }
    private void write(String name, String value) {
      SymbolAttributes attrs = (SymbolAttributes)get(name);
      attrs.write(value);
    }
  }
  private class SymbolAttributes extends Iced {
    private int _type;
    private String _value;
    SymbolAttributes(int type, String value) { _type = type; _value = value; }
    public void write(int type)     { this._type  = type; }
    public void write(String value) { this._value = value;}
  }
  SymbolTable newTable() { return new SymbolTable(); }

  /**
   *  The symbol table interface.
   *
   *  Overwrite existing values in writable tables.
   */
  void put(String name, int type, String value) {
    if( _isGlobal ) _global.put(name,type,value);
    else            _local.put(name,type,value);
  }
  void put(String name, Frame f, boolean isFrame) {
    if( _isGlobal ) _global.put(name,f,isFrame);
    else            _local.put(name,f,isFrame);
  }
  void put(String name, Frame f) { put(name,f,true);}

  int getType(String name, boolean search_global) {
    if (name == null || name.equals("")) throw new IllegalArgumentException("Tried to lookup on a missing name. Are there free floating `%` in your AST?");
    int res = NULL;

    // Check the local_frames first
    // then back out and look around symbol table for keys that MUST exist in DKV;
    // frames in _local_frames do NOT exist in DKV.
    if (_local != null) res = _local.getType(name);

    // Check the local scope first if not null
    if (res == NULL && _local != null) res = _local.getType(name);

    // Didn't find it? Try the global scope next, if we haven't already
    if (res == NULL && search_global && _global != null) res = _global.getType(name);

    // Still didn't find it? Try the KV store next, if we haven't already
    if (res == NULL && search_global) res = kvLookup(name);

    // Still didn't find it? Start looking up the parent scopes.
    if (res == NULL && _parent != null) res = _parent.getType(name, false); // false -> don't keep looking in the global env.

    // Fail if the variable does not exist in any table!
    if (res == NULL) throw new H2OIllegalArgumentException("Failed lookup of variable: " + name, "Failed lookup of variable: " + name + " in: " + this);
    return res;
  }

  private static int kvLookup(String name) {
    Value v = DKV.get(Key.make(name));
    if (v == null) return NULL;
    if (v.get() instanceof Frame) return ARY;
    if (v.get() instanceof Vec)   return ARY;
    else throw new IllegalArgumentException("Unexpected type: " + v.getClass());
  }

  // if calling getValue, then already know that a Frame result isn't at hand!!!
  String getValue(String name, boolean search_global) {
    String res = null;

    // Check the local scope first if not null
    if (_local != null) res = _local.getValue(name);

    // Didn't find it? Try the global scope next, if we haven't already
    if (res == null && search_global) res = _global.getValue(name);

    // Still didn't find it? Start looking up the parent scopes.
    if (res == null && _parent!=null) res = _parent.getValue(name, false); // false -> don't keep looking in the global env.

    // Fail if the variable does not exist in any table!
    return res;
  }

  ASTFrame getFrame(String name, boolean search_global) {
    ASTFrame res=null;

    // check local scope first
    if( _local != null ) res = _local.getFrame(name);

    // if searching global, then look in DKV, _global may have transient Frames sitting inside the table, must check there next if not found here.
    if( res==null && search_global /* search DKV in this case.*/ ) {
      Value v = DKV.get(Key.make(name));
      if( v==null || v.get()==null ) res=null;
      else                           res=new ASTFrame(name);
    }

    // no dice in the DKV, try a transient Frame in the global symbol table (only if we're top level...
    if( res==null && _global!=null ) { res = _global.getFrame(name); }

    // still not found... search up parent scopes
    if( res==null && _parent!=null ) res = _parent.getFrame(name, false);

    // return null if failed to find and error out further up
    return res;
  }

  AST lookup(water.rapids.ASTId id) {
    switch(getType(id.value(), true)) {
      case NUM: return new ASTNum(Double.valueOf(getValue(id.value(), true)));
      case ARY: return new ASTFrame(id.value());
      case LARY:return getFrame(id.value(), false); // pull the local frame out
      case STR: return id.value().equals("()") ? new ASTNull() : new ASTString('\"', id.value());
      default: throw H2O.unimpl("Could not find appropriate type for identifier "+id);
    }
  }

  boolean tryLookup(water.rapids.ASTId id) {
    try {
      lookup(id);
    } catch(Exception e) {
      return false;
    }
    return true;
  }

  // Optimistically lookup strings in the K/V.  On hit, return the found Key as a Frame.
  // On a miss, return the String/Id.
  static AST staticLookup(water.rapids.ASTId id)      { return kvLookup(id.value())==ARY ? new ASTFrame(id.value()) : id; }
  static AST staticLookup(water.rapids.ASTString str) { return kvLookup(str.value())==ARY ? new ASTFrame(str.value()) : str; }
}

abstract class Val extends Iced {
  abstract String value();
  abstract int type();
}

class ValFrame extends Val {
  final String _key;
  final Frame _fr;
  boolean _isVec;
  boolean _g; // are any vecs in the frame in the DKV ?
  ValFrame(Frame fr) { _key = null; _fr = fr; }
  ValFrame(Frame fr, boolean g) { this(fr); _g = g;}
  ValFrame(Frame fr, boolean isVec, boolean g) { _key = null; _fr = fr; _isVec = isVec; _g = g; }
  ValFrame(String key) {
    Key<Frame> k = Key.make(key);
    if (DKV.get(k) == null) throw new H2OKeyNotFoundArgumentException(key);
    _key = key;
    _fr = k.get();
    _g = true;
  }
  @Override public String toString() { return "Frame with key " + _key + ". Frame: :" +_fr.toString(); }
  @Override int type () { return Env.ARY; }
  @Override String value() { return _key; }
}

class ValNum extends Val {
  final double _d;
  ValNum(double d) { _d = d; }
  @Override public String toString() { return ""+_d; }
  @Override int type () { return Env.NUM; }
  @Override String value() { return ""+_d; }
}

class ValStr extends Val {
  final String _s;
  ValStr(String s) { _s = s; }
  @Override public String toString() { return _s; }
  @Override int type () { return Env.STR; }
  @Override String value() { return _s; }
}

class ValSpan extends Val {
  final long _min;       double _max;
  final ASTNum _ast_min; final ASTNum _ast_max;
  boolean _isCol; boolean _isRow;
  ValSpan(ASTNum min, ASTNum max) { _ast_min = min; _ast_max = max; _min = (long)min._d; _max = max._d; }
  boolean contains(long a) {
    if (all_neg()) return _max <= a && a <= _min;
    return _min <= a && a <= _max;
  }
  boolean isColSelector() { return _isCol; }
  boolean isRowSelector() { return _isRow; }
  void setSlice(boolean row, boolean col) { _isRow = row; _isCol = col; }
  @Override String value() { return null; }
  @Override int type() { return Env.SPAN; }
  @Override public String toString() { return _min + ":" + _max; }

  long[] toArray() {
    long[] res = new long[Math.abs((int)_max) - Math.abs((int)_min) + 1];
    long min = _min;
    for (int i = 0; i < res.length; ++i) res[i] = min++;
    Arrays.sort(res);
    return res;
  }

  boolean isValid() { return (_min < 0 && _max < 0) || (_min >= 0 && _max >= 0); }

  boolean all_neg() { return _min < 0; }
  boolean all_pos() { return !all_neg(); }
}

//TODO: add in a boolean field for exclusion
class ValSeries extends Val {
  final long[] _idxs;
  final ASTSpan[] _spans;
  final double[] _d;
  boolean _isCol;
  boolean _isRow;
  int[] _order;

  ValSeries(long[] idxs, ASTSpan[] spans) {
    _idxs = idxs; _d=null;
    if( _idxs!=null ) Arrays.sort(_idxs);
    _spans = spans;
  }
  ValSeries(long[] idxs, double[] d, ASTSpan[] spans) {
    _idxs = idxs; _d=d;
    if( _idxs!=null ) Arrays.sort(_idxs);
    _spans = spans;
  }

  boolean contains(final long a) {
    if (_spans != null)
      for (ASTSpan s : _spans)
        if (s.contains(a))
          return true;
    return _idxs != null && Arrays.binarySearch(_idxs, a) >= 0;
  }

  boolean isColSelector() { return _isCol; }
  boolean isRowSelector() { return _isRow; }

  void setSlice(boolean row, boolean col) {
    _isRow = row;
    _isCol = col;
  }

  @Override String value() { return null; }
  @Override int type() { return Env.SERIES; }
  @Override public String toString() {
    String res = "c(";
    if (_spans != null) {
      for (ASTSpan s : _spans) {
        res += s.toString();
        res += ",";
      }
      if (_idxs == null) res = res.substring(0, res.length() - 1); // remove last comma?
    }
    if (_idxs != null) {
      if( _idxs.length > 20) res += "many ";
      else {
        for (long l : _idxs) {
          res += l;
          res += ",";
        }
      }
      res = res.substring(0, res.length() - 1); // remove last comma.
    }
    res += ")";
    return res;
  }

  long[] toArray() {
    int res_length = 0;
    if (_spans != null) for (ASTSpan s : _spans) res_length += Math.abs((int) s._max) - Math.abs((int) s._min) + 1;
    if (_idxs != null) res_length += _idxs.length;
    long[] res = new long[res_length];
    int cur = 0;
    if (_spans != null) {
      for (ASTSpan s : _spans) {
        long[] l = s.toArray();
        for (long aL : l) res[cur++] = aL;
      }
    }
    if (_idxs != null) {
      for (long _idx : _idxs) res[cur++] = _idx;
    }
    Arrays.sort(res);
    if (all_neg()) reverse(res);
    return res;
  }

  private static void reverse(long[] r) {
    Long[] l =  new Long[r.length];
    for (int i = 0 ; i < l.length; ++i) l[i] = r[i];
    List<Long> list = Arrays.asList(l);
    Collections.reverse(list);
    for (int i = 0; i < list.size(); ++i) r[i] = list.get(i);
  }

  boolean isValid() {
//    long[] ary = toArray();
//    boolean all_neg = false, all_pos = false, first=true;
//    for (long l : ary) {
//      if (first) { if (l < 0) all_neg = true; else all_pos = true; first = false; }
//      if (all_neg && l >= 0) return false;
//      if (all_pos && l < 0) return false;
//    }
    return true;
  }

  boolean isNum() {
    boolean ret = false;
    if (_idxs != null && _idxs.length > 0)
      if (_idxs.length == 1) ret = true;
    if (_spans != null && _spans.length > 0)
      for (ASTSpan s : _spans) ret &= s.isNum();
    return ret;
  }

  long toNum() {
    if (_idxs != null && _idxs.length > 0) return _idxs[0];
    if (_spans != null && _spans.length > 0) return _spans[0].toNum();
    throw new IllegalArgumentException("Could not convert ASTSeries to a single number.");
  }

  boolean all_neg() { return (_idxs != null && _idxs.length > 0) ?_idxs[0] < 0 : _spans[0].all_neg(); }
  boolean all_pos() { return !all_neg(); }
}

class ValNull extends Val {
  ValNull() {}
  @Override String value() { return null; }
  @Override int type() { return Env.NULL; }
}

class ValId extends Val {
  final String _id;
  final char _type; // either '$' or '!'
  ValId(char type, String id) { _type = type; _id = id; }
  @Override public String toString() { return _type+_id; }
  @Override int type() { return Env.ID; }
  @Override String value() { return _id; }
  boolean isSet() { return _type == '!'; }
  boolean isLookup() { return _type == '$'; }
  boolean isValid() { return isSet() || isLookup(); }
}

class ValDoubleList extends Val {
  final double[] _d;
  final ASTSpan[] _spans;
  ValDoubleList(double[] d, ASTSpan[] spans) { _d=d; _spans=spans; }
  @Override public String toString() { return null; }
  @Override int type() { return Env.LIST; }
  @Override String value() { return null; }
}

class ValLongList extends Val {
  final long[] _l;
  final ASTSpan[] _spans;
  ValLongList(long[] l, ASTSpan[] spans) { _l=l; _spans=spans; }
  @Override public String toString() { return null; }
  @Override int type() { return Env.LIST; }
  @Override String value() { return null; }
}
class ValStringList extends Val {
  final String[] _s;
  ValStringList(String[] s) { _s=s; }
  @Override public String toString() { return null; }
  @Override int type() { return Env.LIST; }
  @Override String value() { return null; }
}