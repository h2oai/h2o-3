package hex.knn;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

public class TopNTreeMapTest extends TestUtil {
    
    @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testPut(){
        int k = 3;
        TopNTreeMap<KNNKey<String, Double>, Double> map = new TopNTreeMap<>(k);
        
        // test all items was added and correctly sorted (1.0, 2.0, 3.0)
        map.put(new KNNKey("a", 3.0), 1.0);
        map.put(new KNNKey("b", 2.0), 0.0);
        map.put(new KNNKey("c", 1.0), 1.0);
        Assert.assertEquals(k, map.size());
        Assert.assertEquals(map.lastKey().value, 3.0, 0);
        
        // test the new item 4. 0 is not added
        map.put(new KNNKey("d", 4.0), 0.0);
        Assert.assertEquals(k, map.size());
        Assert.assertEquals(map.lastKey().value, 3.0, 0);
        
        // test the new item 0.0 should be added and be the first item of the map and the last item 3.0 is removed
        map.put(new KNNKey("e", 0.0), 0.0);
        Assert.assertEquals(k, map.size());
        Assert.assertEquals(map.lastKey().value, 2.0, 0);
        
        // test the new item with the key("e", 0.0) and value "0.0" is not added
        // the item is not added, put returns value which is associated with this key
        Double value = map.put(new KNNKey("e", 0.0), 0.0);
        Assert.assertEquals(value, 0.0, 0);
        Assert.assertEquals(map.lastKey().value, 2.0, 0);
        
        // test the new item with the key("ee", 0.0) and value "0.0" is added
        // the item is added, put returns null
        value = map.put(new KNNKey("ee", 0.0), 0.0);
        Assert.assertNull(value);
        Assert.assertEquals(map.firstKey().value, 0.0, 0);
        //Assert.assertEquals("", "e", map.firstKey().key);
        
        // test put new item with the key ("ee", "1.0") and value "0.0" is added
        // the item is added, put return null
        value = map.put(new KNNKey("ee", 0.5), 0.0);
        Assert.assertEquals(map.lastKey().value, 0.5, 0);
        
    }

    @Test
    public void testKNNKeyCompareTo(){
        KNNKey<Integer, Double> k1 = new KNNKey<>(1, 1.0);
        KNNKey<Integer, Double> k2 = new KNNKey<>(2, 1.0);
        KNNKey<Integer, Double> k3 = new KNNKey<>(2, 1.0);
        KNNKey<Integer, Double> k4 = new KNNKey<>(2, 2.0);

        // different key same value -> the first is less
        Assert.assertEquals(k1.compareTo(k2), -1);
        
        // same key same value -> both object are the same 
        Assert.assertEquals(k2.compareTo(k3), 0);
        
        // different key same value -> depends on key comparator
        Assert.assertEquals(k2.compareTo(k1), 1);
        
        // same key different value -> the item with less value is less
        Assert.assertEquals(k3.compareTo(k4), -1);
        
        // different key different value -> the item with less value is less
        Assert.assertEquals(k1.compareTo(k4), -1);

        // different key different value -> the item with less value is less
        Assert.assertEquals(k4.compareTo(k1), 1);
    }
    
}
