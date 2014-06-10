package water;

import junit.framework.Assert;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Created by tomasnykodym on 5/30/14.
 */
public class KeySnapshotTest extends TestUtil {
  private static class IcedInt extends Iced {
    public final int value;
    public IcedInt(int val){value = val;}
  }
  public static class IcedDouble extends Iced {
    public final double value;
    public IcedDouble (double v) {value = v;}
  }
  public void testGlobalKeySet(){
    try {
      Futures fs = new Futures();
      for (int i = 0; i < 100; ++i)
        DKV.put(Key.make("key" + i), new IcedInt(i),fs,true);
      for (int i = 0; i < 100; ++i)
        DKV.put(Key.makeUserHidden(Key.make()), new IcedInt(i),fs,true);
      fs.blockForPending();
      Key[] keys = KeySnapshot.globalSnapshot().keys();
      assertEquals(100, keys.length);
    } finally {
      for (int i = 0; i < 100; ++i) {
        DKV.remove(Key.make("key" + i));
        DKV.remove(Key.makeUserHidden(Key.make()));
      }
    }
  }

  public void testLocalKeySet(){
    Key [] userKeys = new Key[100];
    Key [] systemKeys = new Key[100];
    int homeKeys = 0;
    Futures fs = new Futures();
    try {
      for(int i = 0; i < userKeys.length; ++i){
        DKV.put(userKeys[i] = Key.make("key" + i), new IcedInt(i),fs,true);
        if(userKeys[i].home())++homeKeys;
        DKV.put(systemKeys[i] = Key.makeUserHidden(Key.make()), new IcedInt(i),fs,true);
      }
      fs.blockForPending();
      Key[] keys = KeySnapshot.localSnapshot().keys();
      Assert.assertEquals(homeKeys, keys.length);
      for (Key k:keys)
        Assert.assertTrue(k.home());
    } finally {
      for (int i = 0; i < userKeys.length; ++i) {
        DKV.remove(userKeys[i]);
        DKV.remove(systemKeys[i]);
      }
    }
  }

  public void testFetchAll(){
    Key [] userKeys = new Key[200];
    Key [] systemKeys = new Key[200];
    int homeKeys = 0;
    Futures fs = new Futures();
    try {
      for(int i = 0; i < (userKeys.length >> 1); ++i){
        DKV.put(userKeys[i] = Key.make("key" + i), new IcedInt(i),fs,false);
        if(userKeys[i].home())++homeKeys;
        systemKeys[i] = Key.makeUserHidden(Key.make());
        DKV.put(systemKeys[i], new Value(systemKeys[i], new IcedInt(i)));
      }
      for(int i = (userKeys.length >> 1); i < userKeys.length; ++i){
        DKV.put(userKeys[i] = Key.make("key" + i), new IcedDouble(i),fs,false);
        if(userKeys[i].home())++homeKeys;
        systemKeys[i] = Key.makeUserHidden(Key.make());
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
