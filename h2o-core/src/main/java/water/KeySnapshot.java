package water;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Convenience class for easy access to user-visible keys in the cloud with enabled caching.
 *
 * This class represents snapshot of user keys currently stored in the cloud and contains methods to retrieve it.
 * It contains all user keys stored in the cloud at one particular point in time (marked by timestamp member variable).
 * Snapshot does not contain the actual values and no values are fetched from remote by requesting new snapshot.
 *
 * KeySnapshot itself is a set of user keys with some additional info (e.g. type and size) and some convenience functions
 * supporting filtering and instantiating of classes pointed to by the keys
 *
 * @author tomas
 */
public class KeySnapshot {
  /** Class to filter keys from the snapshot.  */
  public abstract static class KVFilter {
    /** @param k KeyInfo to be filtered
     *  @return true if the key should be included in the new (filtered) set.  */
    public abstract boolean filter(KeyInfo k);
  }

  /** Class containing information about user keys.
   *  Contains the actual key and all interesting information except the data itself.  */
  public static final class KeyInfo extends Iced implements Comparable<KeyInfo>{
    public final Key _key;
    public final int _type;
    public final int _sz;
    public final byte _backEnd;

    public KeyInfo(Key k, Value v){
      _key = k;
      _type = v.type();
      _sz = v._max;
      _backEnd = v.backend();
    }
    @Override public int compareTo(KeyInfo ki) { return _key.compareTo(ki._key);}

    public boolean isFrame()   { return _type == TypeMap.FRAME; }
    public boolean isLockable(){ return TypeMap.theFreezable(_type) instanceof Lockable; }
  }
  private static final long _updateInterval = 1000;
  private static volatile KeySnapshot _cache;
  public final KeyInfo [] _keyInfos;
  /** (local) Time of creation. */
  public final long timestamp;


  /** @return cached version of KeySnapshot */
  public static KeySnapshot cache(){return _cache;}

  /** Filter the snapshot providing custom filter.
   *  Only the keys for which filter returns true will be present in the new snapshot.
   *  @param kvf The filter
   *  @return filtered snapshot
   */
  public KeySnapshot filter(KVFilter kvf){
    ArrayList<KeyInfo> res = new ArrayList<>();
    for(KeyInfo kinfo: _keyInfos)
      if(kvf.filter(kinfo))res.add(kinfo);
    return new KeySnapshot(res.toArray(new KeyInfo[res.size()]));
  }

  KeySnapshot(KeyInfo[] snapshot){
    _keyInfos = snapshot;
    timestamp = System.currentTimeMillis();
  }
  /**
   @return array of all keys in this snapshot.
   */
  public Key[] keys(){
    Key [] res = new Key[_keyInfos.length];
    for(int i = 0; i < _keyInfos.length; ++i)
      res[i] = _keyInfos[i]._key;
    return res;
  }

  /** Return all the keys of the given class.
   *  @param clz Class
   *  @return array of keys in this snapshot with the given class */
  public static Key[] globalKeysOfClass(final Class clz) {
    return KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
      @Override public boolean filter(KeySnapshot.KeyInfo k) { return Value.isSubclassOf(k._type, clz); }
    }).keys();
  }

  /** @param c Class objects of which should be instantiated
   *  @param <T> Generic class being fetched
   *  @return all objects (of the proper class) pointed to by this key snapshot (and still present in the K/V at the time of invocation). */
  public <T extends Iced> Map<String, T> fetchAll(Class<T> c)                { return fetchAll(c,false,0,Integer.MAX_VALUE);}
  /** @param c Class objects of which should be instantiated
   *  @param <T> Generic class being fetched
   *  @param exact - subclasses will not be included if set.
   *  @return all objects (of the proper class) pointed to by this key snapshot (and still present in the K/V at the time of invocation).  */
  public <T extends Iced> Map<String, T> fetchAll(Class<T> c, boolean exact) { return fetchAll(c,exact,0,Integer.MAX_VALUE);}
  /** @param c Class objects of which should be instantiated
   *  @param <T> Generic class being fetched
   *  @param exact - subclasses will not be included if set.
   *  @param offset - skip first offset values matching the given type
   *  @param limit - produce only up to the limit objects.
   *  @return all objects (of the proper class) pointed to by this key snapshot (and still present in the K/V at the time of invocation).  */
  public <T extends Iced> Map<String, T> fetchAll(Class<T> c, boolean exact, int offset, int limit) {
    TreeMap<String, T> res = new TreeMap<>();
    final int typeId = TypeMap.onIce(c.getName());
    for (KeyInfo kinfo : _keyInfos) {
      if (kinfo._type == typeId || (!exact && Value.isSubclassOf(kinfo._type, c))) {
        if (offset > 0) {
          --offset;
          continue;
        }
        Value v = DKV.get(kinfo._key);
        if (v != null) {
          T t = v.get();
          res.put(kinfo._key.toString(), t);
          if (res.size() == limit)
            break;
        }
      }
    }
    return res;
  }

  /**
   * Get the user keys from this node only.
   * Includes non-local keys which are cached locally.
   * @return KeySnapshot containing keys from the local K/V.
   */
  public static KeySnapshot localSnapshot(){return localSnapshot(false);}

  /**
   * Get the user keys from this node only.
   * @param homeOnly - exclude the non-local (cached) keys if set
   * @return KeySnapshot containing keys from the local K/V.
   */
  public static KeySnapshot localSnapshot(boolean homeOnly){
    Object [] kvs = H2O.STORE.raw_array();
    ArrayList<KeyInfo> res = new ArrayList<>();
    for(int i = 2; i < kvs.length; i+= 2){
      Object ok = kvs[i];
      if( !(ok instanceof Key  ) ) continue; // Ignore tombstones and Primes and null's
      Key key = (Key )ok;
      if(!key.user_allowed())continue;
      if(homeOnly && !key.home())continue;
      // Raw array can contain regular and also wrapped values into Prime marker class:
      //  - if we see Value object, create instance of KeyInfo
      //  - if we do not see Value object directly (it can be wrapped in Prime marker class),
      //    try to unwrap it via calling STORE.get (~H2O.get) and then
      //    look at wrapped value again.
      Value val = Value.STORE_get(key);
      if( val == null ) continue;
      res.add(new KeyInfo(key,val));
    }
    final KeyInfo [] arr = res.toArray(new KeyInfo[res.size()]);
    Arrays.sort(arr);
    return new KeySnapshot(arr);
  }
  /**
   * @return KeySnapshot containing user keys from all the nodes.
   */
  public static KeySnapshot globalSnapshot(){ return globalSnapshot(-1);}
  /**
   * Cache-enabled call to get global key snapshot.
   * User can provide time tolerance to indicate a how old the snapshot can be.
   * @param timeTolerance - tolerated age of the cache in millis.
   *                      If the last snapshot is bellow this value, cached version will be returned immediately.
   *                      Otherwise new snapshot must be obtained by from all nodes.
   * @return KeySnapshot containing user keys from all the nodes.
   */
  public static KeySnapshot globalSnapshot(long timeTolerance){
    KeySnapshot res = _cache;
    final long t = System.currentTimeMillis();
    if(res == null || (t - res.timestamp) > timeTolerance)
      res = new KeySnapshot((new GlobalUKeySetTask().doAllNodes()._res));
    else if(t - res.timestamp > _updateInterval)
      H2O.submitTask(new H2O.H2OCountedCompleter() {
        @Override
        public void compute2() {
          new GlobalUKeySetTask().doAllNodes();
        }
      });
    return res;
  }
  // task to grab all user keys (+ info) form all around the cloud
  // updates the cache when done
  private static class GlobalUKeySetTask extends MRTask<GlobalUKeySetTask> {
    KeyInfo [] _res;
    GlobalUKeySetTask() { super(H2O.MIN_HI_PRIORITY); }
    @Override public void setupLocal(){ _res = localSnapshot(true)._keyInfos;}
    @Override public void reduce(GlobalUKeySetTask gbt){
      if(_res == null)_res = gbt._res;
      else if(gbt._res != null){ // merge sort keys together
        KeyInfo [] res = new KeyInfo[_res.length + gbt._res.length];
        int j = 0, k = 0;
        for(int i = 0; i < res.length; ++i)
          res[i] = j < gbt._res.length && (k == _res.length || gbt._res[j].compareTo(_res[k]) < 0)?gbt._res[j++]:_res[k++];
        _res = res;
      }
    }
    @Override public void postGlobal(){
      _cache = new KeySnapshot(_res);
    }
  }
}
