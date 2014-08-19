package water.cascade;

import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.IcedHashMap;
import water.util.IcedInt;
import water.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

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
  final static int NULL=99999;

  final ExecStack _stack;                   // The stack
  final IcedHashMap<Vec,IcedInt> _refcnt;   // Ref Counts for each vector
  transient final public StringBuilder _sb; // Holder for print results
  transient final ArrayList<Key> _locked;     // The original set of locked frames, these shalt not be DKV.removed.
  final SymbolTable _global;
  final SymbolTable _local;
  final Env _parent;
  final private boolean _isGlobal;

  // Top-level Env object: This is the global Env object. To determine if we're in the global scope, _parent == null
  // and _local == null will always be true. The parent of a scope is the calling scope. All scopes inherit from the
  // global scope.
  Env(ArrayList<Key> locked) {
    _stack  = new ExecStack();
    _refcnt = new IcedHashMap<>();
    _sb     = new StringBuilder();
    _locked = locked;
    _global = new SymbolTable();
    _local  = null;
    _parent = null;
    _isGlobal = true;
  }

  // Capture the current environment & return it (for some closure's future execution).
  Env capture() { return new Env(this); }
  private Env(Env e) {
    _stack  = e._stack;
    _refcnt = new IcedHashMap<>();
    _refcnt.putAll(e._refcnt);
    _sb     = null;
    _locked = e._locked;
    _global = e._global;
    _local  = new SymbolTable();
    _parent = e;
    _isGlobal = false;
  }

  public boolean isGlobal() { return _isGlobal; }

  /**
   * The stack API
   */
  public int sp() { return _stack._head + 1; }

  public void push(Object o) {
    if (o instanceof ASTFrame) { addRef(o); }
    _stack.push(o);
  }

  public Object pop() {
    Object o = _stack.pop();
    if (o instanceof ASTFrame) { subRef(o); }
    return o;
  }

  public Object peek() { return _stack.peek(); }
  public Object peekAt(int i) { return _stack.peekAt(i); }
  public int peekType() {return _stack.peekType(); }
  public int peekTypeAt(int i) { return _stack.peekTypeAt(i); }
  public boolean isAry() { return peekType() == ARY; }
  public boolean isNum() { return peekType() == NUM; }
  public boolean isStr() { return peekType() == STR; }
  public boolean isId () { return peekType() == ID;  }
  public boolean isFun() { return peekType() == FUN; }
  public boolean isSpan(){ return peekType() == SPAN;}
  public boolean isSeries(){ return peekType() == SERIES;}

  public Frame popAry () { return ((ASTFrame)pop())._fr; }
  public double popDbl() { return ((ASTNum)pop())._d;    }
  public String popStr() { return ((ASTString)pop())._s; }
  public ASTSeries popSeries() {return (ASTSeries)pop(); }
  public ASTSpan popSpan() { return (ASTSpan)pop();      }
  public Frame peekAry() {return ((ASTFrame)peek())._fr; }
  //TODO: func

  /**
   *  Reference Counting API
   *
   *  All of these methods should be private and void.
   */
  private void addRef(Object o) {
    assert o instanceof ASTFrame;
    for (Vec v : ((ASTFrame) o)._fr.vecs()) addRef(v);
  }

  private void subRef(Object o) {
    assert o instanceof ASTFrame;
    for(Vec v: ((ASTFrame) o)._fr.vecs()) subRef(v);
  }

  private void addRef(Vec v) {
    IcedInt I = _refcnt.get(v);
    assert I==null || I._val>0;
    _refcnt.put(v,new IcedInt(I==null?1:I._val+1));
    //TODO: Does masterVec() need to become public?
//      if (v.masterVec()!=null) addRef(vec.masterVec());
  }

  private void subRef(Vec v) {
    int cnt = _refcnt.get(v)._val - 1;
    if (cnt <= 0) {
      removeVec(v);
      _refcnt.remove(v);
    } else { _refcnt.put(v, new IcedInt(cnt)); }
  }

  static void removeVec(Vec v) {
    Futures fs = new Futures();
    DKV.remove(v._key, fs);
    fs.blockForPending();
  }

  void cleanup(Frame ... frames) {
    for (Frame f : frames)
      if (f != null && f._key != null && !_locked.contains(f._key)) f.delete();
  }

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

  /**
   * Utility & Cleanup
   */
  public void remove_and_unlock() {
    while(!_stack.isEmpty()) {
      int type = peekType();
      switch(type) {
        case ARY: remove(pop()); break;
        default : pop(); break;
      }
    }
  }

  private void remove(Object o) {
    assert o instanceof ASTFrame;
    remove_and_unlock(((ASTFrame)o)._fr);
  }

  private void remove_and_unlock(Frame fr) {
    extinguishCounts(fr);
    if(!_locked.contains(fr._key)) fr.unlock_all(); fr.delete(); DKV.remove(fr._key);
  }

  public String toString(int i) {
    int type = peekTypeAt(i);
    Object o = peekAt(i);
    switch(type) {
      case ARY:  return ((ASTFrame)o)._fr.numRows()+"x"+((ASTFrame)o)._fr.numCols();
      case NUM:  return Double.toString(((ASTNum)o)._d);
      case STR:  return ((ASTString)o)._s;
      case ID :  return ((ASTId)o)._id;
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
    Object  peek();
    Object  peekAt(int i);
    Object  pop();
    void    push(Object t);
    boolean isEmpty();
    int     size();
    int     peekType();
    int     peekTypeAt(int i);
  }

  private class ExecStack implements Stack {
    private final ArrayList<Object> _stack;
    private int _head;

    private ExecStack() {
      _stack = new ArrayList<>();
      _head  = -1;
    }

    /**
     * Peek the top of the stack
     * @return the Object at the `_head` of the stack
     */
    @Override public Object peek() {
      if (isEmpty()) return null;
      return _stack.get(_head);
    }

    /**
     * Peek the stack at position passed in (does error checking on the position)
     * @param i The position at which to peek the stack
     * @return the Object at position `i`.
     */
    @Override public Object peekAt(int i) {

      // Another check just in case assertions aren't on.
      if (i < 0) {
        throw new IllegalArgumentException("Trying to peekAt a negative position in the stack: "+i);
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

      // Return the Object at position i
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

    private int getType(Object o) {
      if (o instanceof ASTNull   ) return NULL;
      if (o instanceof ASTId     ) return ID;
      if (o instanceof ASTFrame  ) return ARY;
      if (o instanceof ASTString ) return STR;
      if (o instanceof ASTNum    ) return NUM;
      if (o instanceof ASTSpan   ) return SPAN;
      if (o instanceof ASTSeries ) return SERIES;
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
    @Override public Object pop() {
      if (isEmpty()) return null;
      Object o = peek();
      _stack.remove(_head--);
      return o;
    }

    /**
     * Push an Object onto the stack
     * @param t is the Object to be pushed onto the stack.
     */
    @Override public void push(Object t) {
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
   *    ID  =0;  // For a !ID (will be set into)
   *    ARY =1;
   *    STR =2;
   *    NUM =3;
   *    FUN =4;
   *
   *  Symbol Table Permissions:
   *  -------------------------
   *
   *  Only the top-level Env object may write to the global symbol table.
   *
   *  NB: The existence of a non-null symbol table implies that execution is occurring in a non-global scope.
   */
  class SymbolTable extends Iced {

    HashMap<String, SymbolAttributes> _table;
    SymbolTable() { _table = new HashMap<>(); }

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

    // Check the local scope first if not null
    if (_local != null) res = _local.typeOf(name);

    // Didn't find it? Try the global scope next, if we haven't already
    if (res == NULL && search_global) res = _global.typeOf(name);

    // Still didn't find it? Try the KV store next, if we haven't already
    if (res == NULL && search_global) res = kvLookup(name);

    // Still didn't find it? Start looking up the parent scopes.
    if (res == NULL) res = _parent.getType(name, false); // false -> don't keep looking in the global env.

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

  AST lookup(ASTId id) {
    switch(getType(id.value(), true)) {
      case NUM: return new ASTNum(Double.valueOf(getValue(id.value(), true)));
      case ARY: return new ASTFrame(id.value());
      case STR: return new ASTString('\"', id.value());
      // case for FUN
      default: throw H2O.fail("Could not find appropriate node for identifier "+id);
    }
  }
}