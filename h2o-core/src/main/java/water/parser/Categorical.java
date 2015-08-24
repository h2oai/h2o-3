package water.parser;

import water.AutoBuffer;
import water.H2O;
import water.Iced;
import water.nbhm.NonBlockingHashMap;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Class for tracking categorical (enum) columns.
 *
 *  Basically a wrapper around non blocking hash map.
 *  In the first pass, we just collect set of unique strings per column
 *  (if there are less than MAX_ENUM_SIZE unique elements).
 *  
 *  After pass1, the keys are sorted and indexed alphabetically.
 *  In the second pass, map is used only for lookup and never updated.
 *  
 *  Categorical objects are shared among threads on the local nodes!
 *
 * @author tomasnykodym
 *
 */
public final class Categorical extends Iced {

  public static final int MAX_ENUM_SIZE = 10000000;
  AtomicInteger _id = new AtomicInteger();
  int _maxId = -1;
  volatile NonBlockingHashMap<ValueString, Integer> _map;
  boolean maxEnumExceeded = false;

  Categorical() { _map = new NonBlockingHashMap<>(); }

  private Categorical(int id, NonBlockingHashMap<ValueString, Integer> map) {
    _id = new AtomicInteger(id);
    _map = map;
  }
  Categorical deepCopy() {
    return new Categorical(_id.get(), _map==null ? null : (NonBlockingHashMap<ValueString,Integer>)_map.clone());
  }
  /** Add key to this map (treated as hash set in this case). */
  int addKey(ValueString str) {
    // _map is shared and be cast to null (if enum is killed) -> grab local copy
    NonBlockingHashMap<ValueString, Integer> m = _map;
    if( m == null ) return Integer.MAX_VALUE;     // Nuked already
    Integer res = m.get(str);
    if( res != null ) return res; // Recorded already
    assert str.length() < 65535; // Length limit so 65535 can be used as a sentinel
    int newVal = _id.incrementAndGet();
    res = m.putIfAbsent(new ValueString(str), newVal);
    if( res != null ) return res;
    if( m.size() > MAX_ENUM_SIZE ) maxEnumExceeded = true;
    return newVal;
  }
  final boolean containsKey(ValueString key){ return _map.containsKey(key); }
  @Override public String toString() {
    return "{"+_map+" }";
  }

  int getTokenId( ValueString str ) { return _map.get(str); }
  
  void merge(Categorical other){
    if( this == other ) return;
    if( isMapFull() ) return;
    if( !other.isMapFull() ) {   // do the merge
      Map<ValueString, Integer> myMap = _map;
      Map<ValueString, Integer> otMap = other._map;
      if( myMap == otMap ) return;
      for( ValueString str : otMap.keySet() )
        myMap.put(str, 1);
      if( myMap.size() <= MAX_ENUM_SIZE ) return;
    }
    maxEnumExceeded = true; // too many values, enum should be killed!
  }
  int maxId() { return _maxId == -1 ? _id.get() : _maxId; }
  int size() { return _map.size(); }
  boolean isMapFull() { return maxEnumExceeded; }

  // assuming single threaded
  ValueString [] computeColumnDomain() {
    ValueString vs[] = _map.keySet().toArray(new ValueString[_map.size()]);
    Arrays.sort(vs);            // Alpha sort to be nice
    for( int j = 0; j < vs.length; ++j )
      _map.put(vs[j], j);       // Renumber in the map
    return vs;
  }

  // Since this is a *concurrent* hashtable, writing it whilst its being
  // updated is tricky.  If the table is NOT being updated, then all is written
  // as expected.  If the table IS being updated we only promise to write the
  // Keys that existed at the time the table write began.  If elements are
  // being deleted, they may be written anyways.  If the Values are changing, a
  // random Value is written.
  @Override public AutoBuffer write_impl( AutoBuffer ab ) {
    if( _map == null ) return ab.put1(1); // Killed map marker
    ab.put1(0);                           // Not killed
    ab.put4(maxId());
    for( ValueString key : _map.keySet() )
      ab.put2((char)key.length()).putA1(key.getBuffer(),key.length()).put4(_map.get(key));
    return ab.put2((char)65535); // End of map marker
  }
  
  @Override public Categorical read_impl( AutoBuffer ab ) {
    assert _map == null || _map.size()==0;
    _map = null;
    if( ab.get1() == 1 ) return this; // Killed?
    _maxId = ab.get4();
    _map = new NonBlockingHashMap<>();
    int len;
    while( (len = ab.get2()) != 65535 ) // Read until end-of-map marker
      _map.put(new ValueString(ab.getA1(len)),ab.get4());
    return this;
  }
  @Override public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    throw H2O.unimpl();
  }
  @Override public Categorical readJSON_impl( AutoBuffer ab ) { throw H2O.unimpl(); }
}
