package water.cascade;

import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import water.util.IcedHashMap;
import water.util.IcedInt;

/** Execute a set of instructions in the context of an H2O cloud.
*
*  An Env (environment) object is a classic stack of values used during the execution of a Program (see Program.java
*  for more details on what makes a Program a Program). The Program itself may be a single `main` program or an array
*  of programs. In the latter case, additional programs represent a user-defined function that is at some point called
*  by the main program.
*
*  Each Program will have a new instance of Env so as to preserve the functional aspects of the R language (lexical
*  scoping, functions are first class, etc.).
*
*  For efficiency, reference counting is employed to recycle objects already in use rather than creating copies upon
*  copies (à la R). When a Vec is `pushed` on to the stack, its reference count is incremented by 1. When a Vec is
*  `popped` off of the stack, its reference count is decremented by 1. When the reference count is 0, the Env instance
*  will dispose of the object. All objects live and die by the Env's that create them. That means that any object not
*  created by an Env instance shalt not be DKV.removed. Every Env has a set of these.
*
*  Therefore, the Env class is a stack of values + an API for reference counting.
*/
public class Env extends Iced {

  final static private int VEC =0;
  final static private int ARY =1;
  final static private int STR =2;
  final static private int DBL =3;
  final static private int FLT =4;
  final static private int INT =5;
  final static private int NULL=99999;

  final ExecStack _stack;                   // The stack
  final IcedHashMap<Vec,IcedInt> _refcnt;   // Ref Counts for each vector
  transient final public StringBuilder _sb; // Holder for print results
  transient final HashSet<Key> _locked;     // The original set of locked frames, these shalt not be DKV.removed.

  Env(HashSet<Key> locked) {
    _stack  = new ExecStack();
    _refcnt = new IcedHashMap<>();
    _sb     = new StringBuilder();
    _locked = locked;
  }

  // Capture the current environment & return it (for some closure's future execution).
  Env capture() { return new Env(this); }
  private Env(Env e) {
    _stack  = e._stack;
    _refcnt = new IcedHashMap<>();
    _refcnt.putAll(e._refcnt);
    _sb     = null;
    _locked = e._locked;
  }

  /**
   * The stack API
   */
  public int sp() { return _stack._head; }

  public void push(Object o) {
    if (o instanceof Frame || o instanceof Vec) { addRef(o); }
    _stack.push(o);
  }

  public Object pop() {
    Object o = _stack.pop();
    if (o instanceof Frame || o instanceof Vec) { subRef(o); }
    return o;
  }

  public Object peek() { return _stack.peek(); }
  public Object peekAt(int i) { return _stack.peekAt(i); }
  public int peekType() {return _stack.peekType(); }
  public int peekTypeAt(int i) { return _stack.peekTypeAt(i); }
  public boolean isAry() { return peekType() == ARY; }
  public boolean isDbl() { return peekType() == DBL; }
  public boolean isInt() { return peekType() == INT; }
  public boolean isStr() { return peekType() == STR; }
  public boolean isFlt() { return peekType() == FLT; }
  public boolean isVec() { return peekType() == VEC; }

  /**
   *  Reference Counting API
   *
   *  All of these methods should be private and void.
   */
  private void addRef(Object o) {
    if (o instanceof Vec) { addRef((Vec) o); }
    assert o instanceof Frame;
    for (Vec v : ((Frame) o).vecs()) addRef(v);
  }

  private void subRef(Object o) {
    if (o instanceof Vec) { subRef((Vec) o); }
    assert o instanceof Frame;
    for(Vec v: ((Frame) o).vecs()) subRef(v);
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
      Futures fs = new Futures();
      DKV.remove(v._key, fs);
      fs.blockForPending();
      _refcnt.remove(v);
    } else { _refcnt.put(v, new IcedInt(cnt)); }
  }

  private void extinguishCounts(Object o) {
    if (o instanceof Vec) { extinguishCounts((Vec) o); }
    assert o instanceof Frame;
    for(Vec v: ((Frame) o).vecs()) extinguishCounts(v);
  }

  private void extinguishCounts(Vec v) {
    _refcnt.remove(v);
  }

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
        case VEC: remove(pop()); break;
        case ARY: remove(pop()); break;
        default : pop(); break;
      }
    }
  }

  private void remove(Object o) {
    if (o instanceof Vec) remove((Vec)o);
    assert o instanceof Frame;
    remove_and_unlock((Frame)o);
  }

  private void remove(Vec v) {
    extinguishCounts(v);
    if (!_locked.contains(v._key)) DKV.remove(v._key);
  }

  private void remove_and_unlock(Frame fr) {
    extinguishCounts(fr);
    if(!_locked.contains(fr._key)) fr.unlock_all(); fr.delete(); DKV.remove(fr._key);
  }

  public String toString(int i) {
    int type = peekTypeAt(i);
    Object o = peekAt(i);
    switch(type) {
      case VEC:  return ((Vec)o).length() + "x1";
      case ARY:  return ((Frame)o).numRows()+"x"+((Frame)o).numCols();
      case DBL:  return Double.toString((double)o);
      case INT:  return Integer.toString((int)o);
      case STR:  return (String)o;
      case FLT:  return Float.toString((float)o);
      case NULL: return "null";
      default: throw H2O.fail("Bad value on the stack");
    }
  }

  @Override public String toString() {
    int sp = sp();
    String s="{";
    for( int i=0; i<sp; i++ ) s += toString()+",";
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

  public class ExecStack implements Stack {
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
    @Override public int peekType() {
      return getType(peek());
    }

    /**
     * Peek the tpe of the object at position `i` in the stack. Does not pop!
     * @param i The position at which to peek the stack
     * @return an int representing the type of the object at position `i` in the stack.
     */
    @Override public int peekTypeAt(int i) {
      return getType(peekAt(i));
    }

    private int getType(Object o) {
      if (o == NULL) return NULL;
      if (o instanceof Vec    ) return VEC;
      if (o instanceof Frame  ) return ARY;
      if (o instanceof String ) return STR;
      if (o instanceof Double ) return DBL;
      if (o instanceof Float  ) return FLT;
      if (o instanceof Integer) return INT;
      throw H2O.fail("Got a bad type on the ExecStack: Object class: "+ o.getClass()+". Not a Vec, Frame, String, Double, Float, or Int.");
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
}