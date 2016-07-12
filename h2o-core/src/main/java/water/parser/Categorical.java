package water.parser;

import java.util.concurrent.atomic.AtomicInteger;

import water.Iced;
import water.util.IcedHashMap;
import water.util.Log;
import water.util.PrettyPrint;

/** Class for tracking categorical (factor) columns.
 *
 *  Basically a wrapper around non blocking hash map.
 *  In the first pass, we just collect set of unique strings per column
 *  (if there are less than MAX_CATEGORICAL_COUNT unique elements).
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

  public static final int MAX_CATEGORICAL_COUNT = 10000000;
  transient AtomicInteger _id = new AtomicInteger();
  int _maxId = -1;
  volatile IcedHashMap<BufferedString, Integer> _map;
  boolean maxDomainExceeded = false;

  Categorical() { _map = new IcedHashMap<>(); }

  /** Add key to this map (treated as hash set in this case). */
  int addKey(BufferedString str) {
    // _map is shared and be cast to null (if categorical is killed) -> grab local copy
    IcedHashMap<BufferedString, Integer> m = _map;
    if( m == null ) return Integer.MAX_VALUE;     // Nuked already
    Integer res = m.get(str);
    if( res != null ) return res; // Recorded already
    assert str.length() < 65535; // Length limit so 65535 can be used as a sentinel
    int newVal = _id.incrementAndGet();
    res = m.putIfAbsent(new BufferedString(str), newVal);
    if( res != null ) return res;
    if( m.size() > MAX_CATEGORICAL_COUNT) maxDomainExceeded = true;
    return newVal;
  }
  final boolean containsKey(BufferedString key){ return _map.containsKey(key); }
  @Override public String toString() {
    return "{"+_map+" }";
  }

  int getTokenId( BufferedString str ) { return _map.get(str); }
  
  int maxId() { return _maxId == -1 ? _id.get() : _maxId; }
  int size() { return _map.size(); }
  boolean isMapFull() { return maxDomainExceeded; }

  BufferedString[] getColumnDomain() {
    return  _map.keySet().toArray(new BufferedString[_map.size()]);
  }

  public static final int MAX_EXAMPLES = 10;
  public void convertToUTF8(int col){
    int hexConvCnt = 0;
    BufferedString[] bStrs = _map.keySet().toArray(new BufferedString[_map.size()]);
    StringBuilder hexSB = new StringBuilder();
    for (int i =0; i < bStrs.length; i++) {
      String s = bStrs[i].toString();
      if (!bStrs[i].equals(s)) {
        if (s.contains("\uFFFD")) { // make weird chars into hex
          s = bStrs[i].bytesToString();
          if (hexConvCnt++ < MAX_EXAMPLES) hexSB.append(s +", ");
          if (hexConvCnt == MAX_EXAMPLES) hexSB.append("...");
        }
        int val = _map.get(bStrs[i]);
        _map.remove(bStrs[i]);
        bStrs[i] = new BufferedString(s);
        _map.put(bStrs[i], val);
      }
    }
    if (hexConvCnt > 0) Log.info("Found categoricals with non-UTF-8 characters in the "
        + PrettyPrint.withOrdinalIndicator(col)
        + " column. Converting unrecognized characters into hex:  "
        + hexSB.toString());
  }

  // Since this is a *concurrent* hashtable, writing it whilst its being
  // updated is tricky.  If the table is NOT being updated, then all is written
  // as expected.  If the table IS being updated we only promise to write the
  // Keys that existed at the time the table write began.  If elements are
  // being deleted, they may be written anyways.  If the Values are changing, a
  // random Value is written.
//  public AutoBuffer write_impl( AutoBuffer ab ) {
//    if( _map == null ) return ab.put1(1); // Killed map marker
//    ab.put1(0);                           // Not killed
//    ab.put4(maxId());
//    for( BufferedString key : _map.keySet() )
//      ab.put2((char)key.length()).putA1(key.getBuffer(),key.length()).put4(_map.get(key));
//    return ab.put2((char)65535); // End of map marker
//  }
//
//  public Categorical read_impl( AutoBuffer ab ) {
//    assert _map == null || _map.size()==0;
//    _map = null;
//    if( ab.get1() == 1 ) return this; // Killed?
//    _maxId = ab.get4();
//    _map = new NonBlockingHashMap<>();
//    int len;
//    while( (len = ab.get2()) != 65535 ) // Read until end-of-map marker
//      _map.put(new BufferedString(ab.getA1(len)),ab.get4());
//    return this;
//  }
}
