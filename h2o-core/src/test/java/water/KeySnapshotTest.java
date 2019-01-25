package water;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import org.junit.*;
import water.util.IcedDouble;
import water.util.IcedInt;

import java.util.ArrayList;
import java.util.Map;

public class KeySnapshotTest extends TestUtil {
  public KeySnapshotTest() { super(5); }

  @Test
  public void testGlobalKeySet(){
    ArrayList<Key> madeKeys = new ArrayList<>();
    try {
      Futures fs = new Futures();

      for (int i = 0; i < 100; ++i) {
        Key k = Key.make("key" + i); madeKeys.add(k);
        DKV.put(k, new IcedInt(i), fs, true);
      }
      for (int i = 0; i < 100; ++i) {
        Key k = Key.make(H2O.SELF); madeKeys.add(k);
        DKV.put(k, new IcedInt(i), fs, true);
      }
      fs.blockForPending();
      Key[] keys = KeySnapshot.globalSnapshot().keys();
      assertEquals(100, keys.length);
    } finally {
      for(Key k:madeKeys) DKV.remove(k);
    }
  }
  @Test
  public void testLocalKeySet(){
    ArrayList<Key> madeKeys = new ArrayList<>();
    int homeKeys = 0;
    Futures fs = new Futures();
    try {
      for(int i = 0; i < 200; ++i){
        Key k = Key.make("key" + i); madeKeys.add(k);
        DKV.put(k, new IcedInt(i),fs,true);
        if(k.home())++homeKeys;
        k = Key.make(H2O.SELF); madeKeys.add(k);
        DKV.put(k, new IcedInt(i),fs,true);
      }
      fs.blockForPending();
      Key[] keys = KeySnapshot.localSnapshot().keys();
      assertEquals(homeKeys, keys.length);
      for (Key k:keys)
        assertTrue(k.home());
    } finally {
      for(Key k:madeKeys)
        DKV.remove(k);
    }
  }
  @Test
  public void testFetchAll(){
    Key [] userKeys = new Key[200];
    Key [] systemKeys = new Key[200];
    Futures fs = new Futures();
    try {
      for(int i = 0; i < (userKeys.length >> 1); ++i){
        DKV.put(userKeys[i] = Key.make("key" + i), new IcedInt(i),fs,false);
        systemKeys[i] = Key.make(H2O.SELF);
        DKV.put(systemKeys[i], new Value(systemKeys[i], new IcedInt(i)));
      }
      for(int i = (userKeys.length >> 1); i < userKeys.length; ++i){
        DKV.put(userKeys[i] = Key.make("key" + i), new IcedDouble(i),fs,false);
        systemKeys[i] = Key.make(H2O.SELF);
        DKV.put(systemKeys[i], new Value(systemKeys[i], new IcedDouble(i)));
      }
      fs.blockForPending();
      KeySnapshot s = KeySnapshot.globalSnapshot();
      Map<String,Iced> all =  s.fetchAll(Iced.class,true);
      assertTrue(all.isEmpty());
      all =  s.fetchAll(Iced.class);
      assertEquals(userKeys.length, all.size());
      Map<String,IcedInt> ints =  s.fetchAll(IcedInt.class);
      Map<String,IcedDouble> doubles =  s.fetchAll(IcedDouble.class);
      assertEquals(userKeys.length >> 1, ints.size());
      assertEquals(userKeys.length >> 1, doubles.size());
    } finally {
      for (int i = 0; i < userKeys.length; ++i) {
        if(userKeys[i]   != null)DKV.remove(userKeys[i]);
        if(systemKeys[i] != null)DKV.remove(systemKeys[i]);
      }
    }
  }

}
