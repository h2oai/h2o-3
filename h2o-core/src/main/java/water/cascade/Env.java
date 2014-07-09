//package water.cascade;
//
//import water.Futures;
//import water.Iced;
//import water.Key;
//import water.UKV;
//import water.exec.ASTFunc;
//import water.exec.ASTOp;
//import water.fvec.Frame;
//import water.fvec.Vec;
//import water.util.Log;
//import water.util.Utils;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.HashSet;
//
///** Execute a set of instructions in the context of an H2O cloud.
// *  @author spencer@0xdata.com
// *
// *
// *  An Env (environment) object is a classic stack of values used during the execution of a Program (see Program.java
// *  for more details on what makes a Program a Program). The Program itself may be a single `main` program or an array
// *  of programs. In the latter case, additional programs represent a user-defined function that is at some point called
// *  by the main program.
// *
// *  Each Program will have a new instance of Env so as to perserve the functional aspects of the R language (lexical
// *  scoping, functions are first class, etc.).
// *
// *  For efficiency, reference counting is employed to recycle objects already in use rather than creating copies upon
// *  copies (à la R). When a Vec is `pushed` on to the stack, its referene count is incremented by 1. When a Vec is
// *  `popped` off of the stack, its reference count is decremented by 1. When the reference count is 0, the Env instance
// *  will dispose of the object. All objects live and die by the Env's that create them. That means that any object not
// *  created by an Env instance shalt not be UKV.removed.
// *
// *  Therefore, the Env class is a stack of values + an API for reference counting.
// */
//public class Env extends Iced {
//  ExecStack _stack;
//  int _sp; // Stack pointer
//
//  // Ref Counts for each vector
//  final Utils.IcedHashMap<Vec,Utils.IcedInt> _refcnt;
//
//  transient final public StringBuilder _sb; // Holder for print results
//
//  transient boolean _allow_tmp;           // Deep-copy allowed to tmp
//  transient boolean _busy_tmp;            // Assert temp is available for use
//  transient Frame _tmp;                  // The One Big Active Tmp
//  transient final ArrayList<Key> _locked; // The original set of locked frames
//
//  Env(ArrayList<Key> locked) {
//    _stack  = new ExecStack(20);
//    _refcnt = new Utils.IcedHashMap<Vec,Utils.IcedInt>();
//    _sb     = new StringBuilder();
//    _locked = locked;
//  }
//
//  public int sp() { return _sp; }
//
//  // Push k empty slots
//  void push( int slots ) {
//    assert 0 <= slots && slots < 1000;
//    int len = _d.length;
//    _sp += slots;
//    while( _sp > len ) {
//      _key= Arrays.copyOf(_key, len << 1);
//      _ary= Arrays.copyOf(_ary, len << 1);
//      _d  = Arrays.copyOf(_d, len << 1);
//      _fcn= Arrays.copyOf(_fcn, len <<= 1);
//    }
//  }
//  void push( Frame fr ) { push(1); _ary[_sp-1] = addRef(fr); assert _ary[0]==null||check_refcnt(_ary[0].anyVec());}
//  void push( double d ) { push(1); _d  [_sp-1] = d  ; }
//  void push( ASTOp fcn) { push(1); _fcn[_sp-1] = addRef(fcn); }
//  void push( Frame fr, String key ) { push(fr); _key[_sp-1]=key; }
//
//  // Copy from display offset d, nth slot
//  void push_slot( int d, int n ) {
//    assert d==0;                // Should use a fcn's closure for d>1
//    int idx = _display[_tod-d]+n;
//    push(1);
//    _ary[_sp-1] = addRef(_ary[idx]);
//    _d  [_sp-1] =        _d  [idx];
//    _fcn[_sp-1] = addRef(_fcn[idx]);
//    assert _ary[0]==null || check_refcnt(_ary[0].anyVec());
//  }
//  void push_slot( int d, int n, Env global ) {
//    assert _refcnt==null;       // Should use a fcn's closure for d>1
//    int idx = _display[_tod-d]+n;
//    int gidx = global._sp;
//    global.push(1);
//    global._ary[gidx] = global.addRef(_ary[idx]);
//    global._d  [gidx] =               _d  [idx] ;
//    global._fcn[gidx] = global.addRef(_fcn[idx]);
//    assert _ary[0]==null || global.check_refcnt(_ary[0].anyVec());
//  }
//  // Copy from TOS into a slot.  Does NOT pop results.
//  void tos_into_slot( int d, int n, String id ) {
//    // In a copy-on-modify language, only update the local scope, or return val
//    assert d==0 || (d==1 && _display[_tod]==n+1);
//    int idx = _display[_tod-d]+n;
//    // Temporary solution to kill a UDF from global name space. Needs to fix in the future.
//    if (_tod == 0) ASTOp.removeUDF(id);
//    subRef(_ary[idx], _key[idx]);
//    subRef(_fcn[idx]);
//    Frame fr =                   _ary[_sp-1];
//    _ary[idx] = fr==null ? null : addRef(new Frame(fr));
//    _d  [idx] =                  _d  [_sp-1] ;
//    _fcn[idx] =           addRef(_fcn[_sp-1]);
//    _key[idx] = d==0 && fr!=null ? id : null;
//    // Temporary solution to add a UDF to global name space. Needs to fix in the future.
//    if (_tod == 0 && _fcn[_sp-1] != null) ASTOp.putUDF(_fcn[_sp-1], id);
//    assert _ary[0]== null || check_refcnt(_ary[0].anyVec());
//  }
//  // Copy from TOS into a slot, using absolute index.
//  void tos_into_slot( int idx, String id ) {
//    subRef(_ary[idx], _key[idx]);
//    subRef(_fcn[idx]);
//    Frame fr =                   _ary[_sp-1];
//    _ary[idx] = fr==null ? null : addRef(new Frame(fr));
//    _d  [idx] =                  _d  [_sp-1] ;
//    _fcn[idx] =           addRef(_fcn[_sp-1]);
//    _key[idx] = fr!=null ? id : null;
//    assert _ary[0]== null || check_refcnt(_ary[0].anyVec());
//  }
//
//  // Copy from TOS into stack.  Pop's all intermediate.
//  // Example: pop_into_stk(-4)  BEFORE: A,B,C,D,TOS  AFTER: A,TOS
//  void pop_into_stk( int x ) {
//    assert x < 0;
//    subRef(_ary[_sp+x], _key[_sp+x]);  // Nuke out old stuff
//    subRef(_fcn[_sp+x]);
//    _ary[_sp+x] = _ary[_sp-1];  // Copy without changing ref cnt
//    _fcn[_sp+x] = _fcn[_sp-1];
//    _d  [_sp+x] = _d  [_sp-1];
//    _sp--;  x++;                // Pop without changing ref cnt
//    while( x++ < -1 ) pop();
//  }
//
//  // Push a scope, leaving room for passed args
//  int pushScope(int args) {
//    assert fcn(-args-1) instanceof ASTFunc; // Expect a function under the args
//    return _display[++_tod] = _sp-args;
//  }
//  // Grab the function for nested scope d
//  ASTFunc fcnScope( int d ) { return (ASTFunc)_fcn[_display[_tod]-1]; }
//
//  // Pop a slot.  Lowers refcnts on vectors.  Always leaves stack null behind
//  // (to avoid dangling pointers stretching lifetimes).
//  void pop( Env global ) {
//    assert _sp > _display[_tod]; // Do not over-pop current scope
//    _sp--;
//    _fcn[_sp]=global.subRef(_fcn[_sp]);
//    _ary[_sp]=global.subRef(_ary[_sp],_key[_sp]);
//    assert _sp==0 || _ary[0]==null || check_refcnt(_ary[0].anyVec());
//  }
//  public void popUncheck( ) {
//    _sp--;
//    _fcn[_sp]=subRef(_fcn[_sp]);
//    _ary[_sp]=subRef(_ary[_sp],_key[_sp]);
//  }
//  public void pop( ) { pop(this); }
//  public void pop( int n ) {
//    for( int i=0; i<n; i++ )
//      pop();
//  }
//
//  void popScope() {
//    assert _tod > 0;            // Something to pop?
//    assert _sp >= _display[_tod]; // Did not over-pop already?
//    while( _sp > _display[_tod] ) pop();
//    _tod--;
//  }
//
//  // Pop & return a Frame or Fcn; ref-cnt of all things remains unchanged.
//  // Caller is responsible for tracking lifetime.
//  public double popDbl()  { assert isDbl(); return _d  [--_sp]; }
//  public ASTOp  popFcn()  { assert isFcn(); ASTOp op = _fcn[--_sp]; _fcn[_sp]=null; return op; }
//  public Frame popAry()  { assert isAry(); Frame fr = _ary[--_sp]; _ary[_sp]=null; assert allAlive(fr); return fr; }
//  public Frame peekAry() { assert isAry(); Frame fr = _ary[_sp-1]; assert allAlive(fr); return fr; }
//  public ASTOp  peekFcn() { assert isFcn(); ASTOp op = _fcn[_sp-1]; return op; }
//  public String peekKey() { return _key[_sp-1]; }
//  public String key()     { return _key[_sp]; }
//  // Pop frame from stack; lower refcnts... allowing to fall to zero without deletion.
//  // Assumption is that this Frame will get pushed again shortly.
//  public Frame popXAry()  {
//    Frame fr = popAry();
//    for( Vec vec : fr.vecs() ) {
//      popVec(vec);
//      if ( vec.masterVec() != null ) popVec(vec.masterVec());
//    }
//    return fr;
//  }
//  public void popVec(Vec vec)  {
//    int cnt = _refcnt.get(vec)._val-1;
//    if( cnt > 0 ) _refcnt.put(vec,new Utils.IcedInt(cnt));
//    else _refcnt.remove(vec);
//  }
//
//  // Replace a function invocation with it's result
//  public void poppush( int n, Frame ary, String key) {
//    addRef(ary);
//    for( int i=0; i<n; i++ ) {
//      assert _sp > 0;
//      _sp--;
//      _fcn[_sp] = subRef(_fcn[_sp]);
//      _ary[_sp] = subRef(_ary[_sp], _key[_sp]);
//    }
//    push(1); _ary[_sp-1] = ary; _key[_sp-1] = key;
//    assert check_all_refcnts();
//  }
//  // Replace a function invocation with it's result
//  public void poppush(double d) { pop(); push(d); }
//
//  // Capture the current environment & return it (for some closure's future execution).
//  Env capture( boolean cntrefs ) { return new Env(this,cntrefs); }
//  private Env( Env e, boolean cntrefs ) {
//    _sp = e._sp;
//    _key= Arrays.copyOf(e._key, _sp);
//    _ary= Arrays.copyOf(e._ary, _sp);
//    _d  = Arrays.copyOf(e._d, _sp);
//    _fcn= Arrays.copyOf(e._fcn, _sp);
//    _tod= e._tod;
//    _display = e._display.clone();
//    if( cntrefs ) {             // If counting refs
//      _refcnt = new Utils.IcedHashMap<Vec,Utils.IcedInt>();
//      _refcnt.putAll(e._refcnt); // Deep copy the existing refs
//    } else _refcnt = null;
//    // All other fields are ignored/zero
//    _sb = null;
//    _locked = null;
//  }
//
//
//  // Nice assert
//  boolean allAlive(Frame fr) {
//    for( Vec vec : fr.vecs() )
//      assert _refcnt.get(vec)._val > 0;
//    return true;
//  }
//
//  public Futures subRef( Vec vec, Futures fs ) {
//    if ( vec.masterVec() != null ) subRef(vec.masterVec(), fs);
//    int cnt = _refcnt.get(vec)._val-1;
//    //Log.info(" --- " + vec._key.toString()+ " RC=" + cnt);
//    if( cnt > 0 ) _refcnt.put(vec,new Utils.IcedInt(cnt));
//    else {
//      if( fs == null ) fs = new Futures();
//      UKV.remove(vec._key, fs);
//      _refcnt.remove(vec);
//    }
//    return fs;
//  }
//
//  // Lower the refcnt on all vecs in this frame.
//  // Immediately free all vecs with zero count.
//  // Always return a null.
//  public Frame subRef( Frame fr, String key ) {
//    if( fr == null ) return null;
//    Futures fs = null;
//    for( Vec vec : fr.vecs() ) fs = subRef(vec,fs);
//    if( fs != null ) fs.blockForPending();
//    return null;
//  }
//  // Lower refcounts on all vecs captured in the inner environment
//  public ASTOp subRef( ASTOp op ) {
//    if( op == null ) return null;
//    if( !(op instanceof ASTFunc) ) return null;
//    ASTFunc fcn = (ASTFunc)op;
//    if( fcn._env != null ) fcn._env.subRef(this);
//    else Log.info("Popping fcn object, never executed no environ capture");
//    return null;
//  }
//
//  Vec addRef( Vec vec ) {
//    Utils.IcedInt I = _refcnt.get(vec);
//    assert I==null || I._val>0;
//    assert vec.length() == 0 || (vec.at(0) > 0 || vec.at(0) <= 0 || Double.isNaN(vec.at(0)));
//    _refcnt.put(vec,new Utils.IcedInt(I==null?1:I._val+1));
//    if (vec.masterVec()!=null) addRef(vec.masterVec());
//    return vec;
//  }
//  // Add a refcnt to all vecs in this frame
//  Frame addRef( Frame fr ) {
//    if( fr == null ) return null;
//    for( Vec vec : fr.vecs() ) addRef(vec);
//    return fr;
//  }
//  ASTOp addRef( ASTOp op ) {
//    if( op == null ) return null;
//    if( !(op instanceof ASTFunc) ) return op;
//    ASTFunc fcn = (ASTFunc)op;
//    if( fcn._env != null ) fcn._env.addRef(this);
//    else Log.info("Pushing fcn object, never executed no environ capture");
//    return op;
//  }
//  private void addRef(Env global) {
//    for( int i=0; i<_sp; i++ ) {
//      if( _ary[i] != null ) global.addRef(_ary[i]);
//      if( _fcn[i] != null ) global.addRef(_fcn[i]);
//    }
//  }
//  private void subRef(Env global) {
//    for( int i=0; i<_sp; i++ ) {
//      if( _ary[i] != null ) global.subRef(_ary[i],_key[i]);
//      if( _fcn[i] != null ) global.subRef(_fcn[i]);
//    }
//  }
//
//
//  // Remove everything
//  public void remove_and_unlock() {
//    // Remove all shallow scopes
//    while( _tod > 0 ) popScope();
//    // Push changes at the outer scope into the K/V store
//    while( _sp > 0 ) {
//      if( isAry() && _key[_sp-1] != null ) { // Has a K/V mapping?
//        Frame fr = popAry();    // Pop w/o lowering refcnt
//        String skey = key();
//        Frame fr2=new Frame(Key.make(skey),fr._names.clone(),fr.vecs().clone());
//        for( int i=0; i<fr.numCols(); i++ ) {
//          Vec v = fr.vecs()[i];
//          int refcnt = _refcnt.get(v)._val;
//          assert refcnt > 0;
//          if( refcnt > 1 ) {    // Need a deep-copy now
//            Vec v2 = new Frame(v).deepSlice(null,null).vecs()[0];
//            fr2.replace(i,v2);  // Replace with private deep-copy
//            subRef(v,null);     // Now lower refcnt for good assertions
//            addRef(v2);
//          } // But not down to zero (do not delete items in global scope)
//        }
//        if( _locked.contains(fr2._key) ) fr2.write_lock(null);     // Upgrade to write-lock
//        else { fr2.delete_and_lock(null); _locked.add(fr2._key); } // Clear prior & set new data
//        fr2.unlock(null);
//        _locked.remove(fr2._key); // Unlocked already
//      } else
//        popUncheck();
//    }
//    // Unlock all things that do not survive, plus also delete them
//    for( Key k : _locked ) {
//      Frame fr = UKV.get(k);
//      fr.unlock(null);  fr.delete(); // Should be atomic really
//    }
//  }
//
//  // Done writing into all things.  Allow rollups.
//  public void postWrite() {
//    for( Vec vec : _refcnt.keySet() )
//      vec.postWrite();
//  }
//
//  // Count references the "hard way" - used to check refcnting math.
//  int compute_refcnt( Vec vec ) {
//    int cnt=0;
//    HashSet<Vec> refs = new HashSet<Vec>();
//    for( int i=0; i<_sp; i++ )
//      if( _ary[i] != null) {
//        for (Vec v : _ary[i].vecs()) {
//          Vec vm;
//          if (v.equals(vec)) cnt++;
//          else if ((vm = v.masterVec()) !=null && vm.equals(vec)) cnt++;
//        }
//      }
//      else if( _fcn[i] != null && (_fcn[i] instanceof ASTFunc) )
//        cnt += ((ASTFunc)_fcn[i])._env.compute_refcnt(vec);
//    return cnt + refs.size();
//  }
//  boolean check_refcnt( Vec vec ) {
//    Utils.IcedInt I = _refcnt.get(vec);
//    int cnt0 = I==null ? 0 : I._val;
//    int cnt1 = compute_refcnt(vec);
//    if( cnt0==cnt1 ) return true;
//    Log.err("Refcnt is " + cnt0 + " but computed as " + cnt1);
//    return false;
//  }
//
//  boolean check_all_refcnts() {
//    for (Vec v : _refcnt.keySet())
//      if (check_refcnt(v) == false)
//        return false;
//    return true;
//  }
//
//  // Pop and return the result as a string
//  public String resultString( ) {
//    assert _tod==0 : "Still have lexical scopes past the global";
//    String s = toString(_sp-1,true);
//    pop();
//    return s;
//  }
//
//  public String toString(int i, boolean verbose_fcn) {
//    if( _ary[i] != null ) return _ary[i].numRows()+"x"+_ary[i].numCols();
//    else if( _fcn[i] != null ) return _fcn[i].toString(verbose_fcn);
//    return Double.toString(_d[i]);
//  }
//  @Override
//  public String toString() {
//    String s="{";
//    for( int i=0; i<_sp; i++ )   s += toString(i,false)+",";
//    return s+"}";
//  }
//}
//
//interface Stack {
//  Object getTop();
//  Object pop();
//  void push(Object t);
//  boolean isEmpty();
//  int size();
//}
//
//class ExecStack implements Stack {
//  private ArrayList<Object> _stack;
//  private int _head;
//  private final static int SIZE = 15;
//
//  public ExecStack(int size) {
//    _stack = new ArrayList<Object>();
//    _head  = -1;
//  }
//
//  @Override
//  public Object getTop() {
//    if (_head == -1) {
//      return null;
//    }
//    return _stack[_head];
//  }
//
//  @Override
//  public boolean isEmpty() { return _head == -1; }
//
//  @Override
//  public int size() {
//    return 0;
//  }
//
//  @Override
//  public Object pop() {
//    if (isEmpty()) return null;
//    return _stack[--];
//  }
//
//  @Override
//  public void push(Object t) {
//
//  }
//
//}