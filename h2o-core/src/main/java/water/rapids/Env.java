package water.rapids;

import water.*;
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
  final static int NULL  =99999;
  final static int LARY  =99;  // special value for arrays in _local_array

  transient final ExecStack _stack;                      // The stack
  final IcedHashMap<Vec,IcedInt> _refcnt;      // Ref Counts for each vector
  transient final public StringBuilder _sb;    // Holder for print results
  transient final HashSet<Key> _locked;        // Vec keys, these shalt not be DKV.removed.
  transient final HashSet<Key> _global_frames; // Frame keys in the *global* scope
  transient final HashSet<Key> _local_frames;  // Frame keys in the *local* scope
  transient final HashSet<Key> _local_locked;  // Locked frames in the *local* scope
  transient final SymbolTable _global;
  transient final SymbolTable _local;
  final Env _parent;
  final private boolean _isGlobal;

  // Top-level Env object: This is the global Env object. To determine if we're in the global scope, _parent == null
  // and _local == null will always be true. The parent of a scope is the calling scope. All scopes inherit from the
  // global scope.
  Env(HashSet<Key> locked) {
    _stack  = new ExecStack();
    _refcnt = new IcedHashMap<>();
    _sb     = new StringBuilder();
    _locked = locked;
    _global = new SymbolTable();
    _global_frames = new HashSet<>();
    _local_frames = null;
    _local  = null;
    _local_locked = null;
    _parent = null;
    _isGlobal = true;
  }

  // Capture the current environment & return it (for some closure's future execution).
  Env capture() { return new Env(this); }
  private Env(Env e) {
    _stack  = e._stack;
    _refcnt = e._refcnt; // same ref cnter
    _sb     = null;
    _global_frames = e._global_frames;
    _local_frames = new HashSet<>();
    _locked = e._locked;
    _local_locked = new HashSet<>();
    _global = e._global;
    _local  = new SymbolTable();
    _parent = e;
    _isGlobal = false;
  }

  SymbolTable newTable() { return new SymbolTable(); }

  public boolean isGlobal() { return _isGlobal; }

  /**
   * The stack API
   */
  public int sp() { return _stack._head + 1; }

  public void push(Val o) {
    if (o instanceof ValFrame) {
      if (_local != null) {
        String key = Key.make().toString();
        _local._local_frames.put(key, ((ValFrame)o)._fr);
      }
    }
    if (o instanceof ValFrame) { addRef(o); }
    _stack.push(o);
  }

  public Val pop() {
    Val o = _stack.pop();
    if (o instanceof ValFrame) { subRef(o); }
    return o;
  }

  // pop w/o lowering ref counts
  public Val pop0() { return _stack.pop(); }
  // push w/0 raising ref counts
  public void push0(Val v) { _stack.push(v); }

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
  public boolean isSpan(){ return peekType() == SPAN;}
  public boolean isSeries(){ return peekType() == SERIES;}

  public Frame popAry () { return ((ValFrame)pop())._fr; }
  public double popDbl() { return ((ValNum)pop())._d;    }
  public String popStr() { return ((ValStr)pop())._s;    }
  public ValSeries popSeries() {return (ValSeries)pop(); }
  public ValSpan popSpan() { return (ValSpan)pop();      }
  public Frame peekAry() {return ((ValFrame)peek())._fr; }
  public double peekDbl() {return ((ValNum)peek())._d;   }
  public Frame pop0Ary() { return ((ValFrame)pop0())._fr;  }
  public void push0Ary(Frame fr) { push0(new ValFrame(fr)); }
  //TODO: func

  /**
   *  Reference Counting API
   *
   *  All of these methods should be private and void.
   */
  private void addRef(Val o) {
    assert o instanceof ValFrame;
    for (Vec v : ((ValFrame) o)._fr.vecs()) addRef(v);
  }

  private void subRef(Val o) {
    assert o instanceof ValFrame;
    boolean delete = true;
    if (((ValFrame) o)._fr != null && _locked.contains(((ValFrame) o)._fr._key)) {
      for (Vec v: ((ValFrame) o)._fr.vecs()) subRefLocked(v);
    }
    for(Vec v: ((ValFrame) o)._fr.vecs()) delete &= subRef(v);
    if (delete) {
      Key k = ((ValFrame)o)._fr._key;
      if (k != null && !_locked.contains(k))  ((ValFrame)o)._fr.delete();
    }
  }

  private void addRef(Vec v) {
    IcedInt I = _refcnt.get(v);
    assert I==null || I._val>=0;
    _refcnt.put(v,new IcedInt(I==null?1:I._val+1));
    //TODO: Does masterVec() need to become public?
//      if (v.masterVec()!=null) addRef(vec.masterVec());
  }

  private boolean subRef(Vec v) {
    if (v == null) return false;
    if (_refcnt.get(v) == null && !_locked.contains(v._key))  {
      if (_local_locked != null && _local_locked.contains(v._key)) return false;
      for (Key kg : _global_frames) {
        if ( Arrays.asList(((Frame)DKV.get(kg).get()).keys()).contains(v._key)) {
          return false;
        }
      }
      removeVec(v, null);
      return true;
    }
    if (_refcnt.get(v) == null) { return false; }
    int cnt = _refcnt.get(v)._val - 1;
    if (cnt <= 0 && !_locked.contains(v._key) && DKV.get(v._key) != null) {
      for (Key kg : _global_frames) {
        if ( Arrays.asList(((Frame)DKV.get(kg).get()).keys()).contains(v._key)) {
          return false;
        }
      }
      if (_local_frames != null) {
        for (Key kl : _local_frames) {
          if (DKV.get(kl) != null && Arrays.asList(((Frame)DKV.get(kl).get()).keys()).contains(v._key)) {
            return false;
          }
          // needed for cases where we need to re-use the local frame, keep it in a local_locked since not in DKV and above check will miss it...
          if (_local_locked != null) {
            if (_local_locked.contains(kl)) return false;
          }
        }
      }
      removeVec(v, null);
      extinguishCounts(v);
      return true;
    } else {
      if(cnt < 0) extinguishCounts(v);
      else _refcnt.put(v, new IcedInt(cnt));
    }
    return false;
  }

  private void subRefLocked(Vec v) {
    if (_refcnt.get(v) == null) return;
    int cnt = _refcnt.get(v)._val -1;
    if (cnt <= 0) extinguishCounts(v);
    else _refcnt.put(v, new IcedInt(cnt));
  }

  // MUST be called in conjunction w/ push(frame) or addRef
  void addKeys(Frame fr) {
    if (_local_locked != null) {
      for (Vec v : fr.vecs()) _local_locked.add(v._key);
    } else if (_locked != null || H2O.containsKey(fr._key)) {
      assert _locked != null;
      for (Vec v : fr.vecs()) _locked.add(v._key);
    }
  }

  void addVec(Vec v) { _locked.add(v._key);  addRef(v); }
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

  void cleanup(Frame ... frames) { for (Frame f : frames) unload(f,true); }

  private void extinguishCounts(Object o) {
    if (o instanceof Vec) { extinguishCounts((Vec) o); }
    assert o instanceof Frame;
    for(Vec v: ((Frame) o).vecs()) extinguishCounts(v);
  }

  private void extinguishCounts(Vec v) { _refcnt.remove(v); }

  boolean allAlive(Frame fr) {
    for( Vec vec : fr.vecs() )
      assert _refcnt.get(vec)._val > 0;
    return true;
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

    for (String k : _global._local_frames.keySet()) {
      Frame f = _local._local_frames.get(k);
      for (Vec v : f.vecs()) removeVec(v, fs);
      f.delete();
    }
    fs.blockForPending();
  }

  void subVec(Vec v) { IcedInt I = _refcnt.get(v); _refcnt.put(v,new IcedInt(I._val-1)); }
  Futures subVec(Vec v, Futures fs) {
    assert fs != null : "Future should not be null!";
//    if ( v.masterVec() != null ) subRef(v.masterVec(), fs);
    if (!_refcnt.containsKey(v)) return fs;
    int cnt = _refcnt.get(v)._val-1;
    if ( cnt > 0 ) {
      _refcnt.put(v,new IcedInt(cnt));
    } else {
      if (!_locked.contains(v._key)) {
        extinguishCounts(v);
        fs = removeVec(v, fs);
      } else { extinguishCounts(v); }
    }
    return fs;
  }

  void remove(Object o, boolean popped) {
    assert o instanceof ValFrame || o instanceof Frame || o == null;
    if (o == null) return;
    if (o instanceof ValFrame) remove_and_unlock(((ValFrame)o)._fr);
    else remove_and_unlock((Frame)o);
    if(!popped) pop();
  }

  void unload(Object o, boolean popped) {
    assert o instanceof ValFrame || o instanceof Frame || o == null;
    if (o == null) return;
    if (o instanceof ValFrame) subref_and_unlock(((ValFrame)o)._fr);
    else subref_and_unlock((Frame)o);
    if(!popped) pop();
  }

  private void subref_and_unlock(Frame fr) {
    if (fr._lockers != null && lockerKeysNotNull(fr)) fr.unlock_all();
    subRef(new ValFrame(fr));
  }

  // currently does not rely on reference counting -- does the hard job of scanning environments and checking if any references exist above this scope.
  // might as well do the hard work, since it will have to be done in an assert anyway
  void popScope() {
    _local.clear();  // clear the symbol table
    Futures fs = new Futures();
    // chop the local frames made in the scope
    for (String k : _local._local_frames.keySet()) {
      if (isAry()) {
        if(peekAry()._key != null && peekAry()._key == _local._local_frames.get(k)._key) continue;
      }
      Frame f = _local._local_frames.get(k);
      for (Vec v : f.vecs()) removeVec(v, fs);
      f.delete();
    }
    _local._local_frames.clear();
    // zoop over the _local_locked hashset and hose down the KV store
    if (_local_locked != null) {
      for (Key k : _local_locked) {
        if (isAry()) {
          if(peekAry()._key != null && peekAry()._key == k) continue;
          if(Arrays.asList(peekAry().keys()).contains(k)) continue;
        }
        Keyed.remove(k, fs);
      }
    }
    fs.blockForPending();
  }

  // NOTE: this extinguishCounts is slightly suspicious, but might be OK here... Will matter in UDFs
  private void remove_and_unlock(Frame fr) {
    extinguishCounts(fr);
    if (fr._lockers != null && lockerKeysNotNull(fr)) fr.unlock_all();
    if (_locked.contains(fr._key) || any_locked(fr)) return;
    fr.delete();
  }

  private boolean lockerKeysNotNull(Frame f) {
    for (Key k : f._lockers)
      if (k == null) return false;
    return true;
  }

  private boolean any_locked(Frame fr) {
    for (Vec v : fr.vecs()) if (_locked.contains(v._key)) return true;
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
      default: throw H2O.fail("Bad value on the stack");
    }
  }

  @Override public String toString() {
    int sp = sp();
    String s="{";
    for( int i=0; i<sp; i++ ) s += toString(i)+",";
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
      if (i < 0) {
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
      if (o instanceof ValNull   ) return NULL;
      if (o instanceof ValId     ) return ID;
      if (o instanceof ValFrame  ) return ARY;
      if (o instanceof ValStr    ) return STR;
      if (o instanceof ValNum    ) return NUM;
      if (o instanceof ValSpan   ) return SPAN;
      if (o instanceof ValSeries ) return SERIES;
//      if (o instanceof ASTFunc   ) return FUN;
      throw H2O.fail("Got a bad type on the ExecStack: Object class: "+ o.getClass()+". Not a Frame, String, Double, Fun, Span, or Series");
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
      _stack.remove(_head--);
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
   *  The Symbol Table Data Structure: A mapping between identifiers and their values.
   *
   *  The role of the symbol table is to track the various identifiers and their attributes that appear in the nodes of
   *  the AST passed from R. There are three cases that are of interest:
   *    1. The identifier is a variable that references some blob of data having a type, value, and scope.
   *    2. The identifier is part of an assignment and its type is whatever the type is on the right-hand side.
   *    3. There is no identifier: a non-case, but worth mentioning.
   *
   *  As already stated, each identifier has a name, type, value, and scope. The scoping is implied by the Env object,
   *  so it is not necessary to include this attribute.
   *
   *  Valid types:
   *
   *    ID    =0;  // For a !ID (will be set into)
   *    ARY   =1;
   *    STR   =2;
   *    NUM   =3;
   *    FUN   =4;
   *    SPAN  =5;  // something like 1:10
   *    SERIES=6;  // something like c(1:10, 90, 230:500) ...
   *
   *  Symbol Table Permissions:
   *  -------------------------
   *
   *  Only the top-level Env object may write to the global symbol table.
   *
   *  NB: The existence of a non-null symbol table implies that execution is occurring in a non-global scope.
   */
  class SymbolTable extends Iced {

    HashMap<String, Frame> _local_frames; // these are not in the DKV!
    HashMap<String, SymbolAttributes> _table;
    public SymbolTable() { _table = new HashMap<>(); _local_frames = new HashMap<>(); }
    void clear() { _table.clear(); }
    public void copyOver(SymbolTable s) {
      _table = s._table;
      _local_frames = s._local_frames;
    }

    public void put(String name, int type, String value) {
      if (_table.containsKey(name)) {
        writeType(name, type);
        writeValue(name, value);
      }
      SymbolAttributes attributes = new SymbolAttributes(type, value);
      _table.put(name, attributes);
    }

    public int typeOf(String name) {
      if (!_table.containsKey(name)) return NULL;
      return _table.get(name).typeOf();
    }

    public int typeOf2(String name) {
      if (_local_frames.containsKey(name)) return LARY;
      if (_local_frames.containsKey(getValue(name, false))) return LARY;
      return NULL;
    }

    public String valueOf(String name) {
      if (!_table.containsKey(name)) return null;
      return _table.get(name).valueOf();
    }

    public void writeType(String name, int type) {
      assert _table.containsKey(name) : "No such identifier in the symbol table: " + name;
      SymbolAttributes attrs = _table.get(name);
      attrs.writeType(type);
      _table.put(name, attrs);
    }

    public void writeValue(String name, String value) {
      assert _table.containsKey(name) : "No such identifier in the symbol table: " + name;
      SymbolAttributes attrs = _table.get(name);
      attrs.writeValue(value);
      _table.put(name, attrs);
    }

    private class SymbolAttributes {
      private int _type;
      private String _value;

      SymbolAttributes(int type, String value) { _type = type; _value = value; }

      public int typeOf ()  { return  _type;  }
      public String valueOf()  { return  _value; }

      public void writeType(int type)      { this._type  = type; }
      public void writeValue(String value) { this._value = value;}
    }
  }

  /**
   *  The symbol table interface.
   *
   *  Overwrite existing values in writable tables.
   */
  void put(String name, int type, String value) {
    if (isGlobal()) _global.put(name, type, value);
    else _local.put(name, type, value);
  }

  int getType(String name, boolean search_global) {
    int res = NULL;

    // Check the local_frames first
    // then back out and look around symbol table for keys that MUST exist in DKV;
    // frames in _local_frames do NOT exist in DKV.
    if (_local != null) res = _local.typeOf2(name);

    // Check the local scope first if not null
    if (res == NULL && _local != null) res = _local.typeOf(name);

    // Didn't find it? Try the global scope next, if we haven't already
    if (res == NULL && search_global) res = _global.typeOf(name);

    // Still didn't find it? Try the KV store next, if we haven't already
    if (res == NULL && search_global) res = kvLookup(name);

    // Still didn't find it? Start looking up the parent scopes.
    if (res == NULL && _parent != null) res = _parent.getType(name, false); // false -> don't keep looking in the global env.

    // Fail if the variable does not exist in any table!
    if (res == NULL) throw H2O.fail("Failed lookup of variable: "+name);
    return res;
  }

  private int kvLookup(String name) {
    if (DKV.get(Key.make(name)) != null) return ARY; else return NULL;
  }

  String getValue(String name, boolean search_global) {
    String res = null;

    // Check the local scope first if not null
    if (_local != null) res = _local.valueOf(name);

    // Didn't find it? Try the global scope next, if we haven't already
    if (res == null && search_global) res = _global.valueOf(name);

    // Still didn't find it? Start looking up the parent scopes.
    if (res == null) res = _parent.getValue(name, false); // false -> don't keep looking in the global env.

    // Fail if the variable does not exist in any table!
    if (res == null) throw H2O.fail("Failed lookup of variable: "+name);
    return res;
  }

  AST lookup(water.rapids.ASTId id) {
    switch(getType(id.value(), true)) {
      case NUM: return new ASTNum(Double.valueOf(getValue(id.value(), true)));
      case ARY: return new ASTFrame(id.value());
//      case LARY:return new ASTFrame(_local._local_frames.get(id.value())); // pull the local frame out
      case LARY:return new ASTFrame(get_local(id.value())); // pull the local frame out
      case STR: return id.value().equals("null") ? new ASTNull() : new ASTString('\"', id.value());
      // case for FUN
      default: throw H2O.fail("Could not find appropriate type for identifier "+id);
    }
  }

  // take the local id, check if it maps to a Frame in _local_frames
  // if not, check if the value of id maps to is a key in _local_frames
  Frame get_local(String id) {
    if (_local._local_frames.containsKey(id)) return _local._local_frames.get(id);
    String value = getValue(id, true);
    if (_local._local_frames.containsKey(value)) return _local._local_frames.get(value);
    throw new IllegalArgumentException("No Frame Found! Failed to lookup on variable: " + id);
  }
}

abstract class Val extends Iced {
  abstract String value();
  abstract int type();
}

class ValFrame extends Val {
  final String _key;
  final Frame _fr;
  ValFrame(Frame fr) { _key = null; _fr = fr; }
  ValFrame(String key) {
    Key k = Key.make(key);
    if (DKV.get(k) == null) throw H2O.fail("Key "+ key +" no longer exists in the KV store!");
    _key = key;
    _fr = k.get();
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
  final long _min;       final long _max;
  final ASTNum _ast_min; final ASTNum _ast_max;
  boolean _isCol; boolean _isRow;
  ValSpan(ASTNum min, ASTNum max) { _ast_min = min; _ast_max = max; _min = (long)min._d; _max = (long)max._d; }
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
  boolean _isCol;
  boolean _isRow;
  int[] _order;

  ValSeries(long[] idxs, ASTSpan[] spans) {
    _idxs = idxs;
    _spans = spans;
  }

  boolean contains(long a) {
    if (_spans != null)
      for (ASTSpan s : _spans) if (s.contains(a)) return true;
    if (_idxs != null)
      for (long l : _idxs) if (l == a) return true;
    return false;
  }

  boolean isColSelector() { return _isCol; }
  boolean isRowSelector() { return _isRow; }

  void setSlice(boolean row, boolean col) {
    _isRow = row;
    _isCol = col;
  }

  @Override String value() {
    return null;
  }

  @Override
  int type() {
    return Env.SERIES;
  }

  @Override
  public String toString() {
    String res = "c(";
    if (_spans != null) {
      for (ASTSpan s : _spans) {
        res += s.toString();
        res += ",";
      }
      if (_idxs == null) res = res.substring(0, res.length() - 1); // remove last comma?
    }
    if (_idxs != null) {
      for (long l : _idxs) {
        res += l;
        res += ",";
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
