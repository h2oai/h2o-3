package water.util;

import hex.KeyValue;
import hex.Model;
import org.junit.Test;
import water.Key;
import water.api.schemas3.KeyV3;
import water.api.schemas3.KeyValueV3;
import water.fvec.Frame;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class JSONValueTest {

    @Test public void test_primitive_type() {
        JSONValue jInt = JSONValue.fromValue(123);
        assertEquals("123", jInt._json);
        assertEquals(123, jInt.value());
        assertEquals(jInt, new JSONValue<>("123", Integer.class));
    }

    @Test public void test_primitive_array() {
        JSONValue jInts = JSONValue.fromValue(new int[] {1, 2, 3});
        assertEquals("[1,2,3]", jInts._json);
        assertArrayEquals(new int[] {1, 2, 3}, (int[])jInts.value());
        assertEquals(jInts, new JSONValue<>("[1,2,3]", int[].class));
    }

    @Test public void test_string() {
        JSONValue jStr = JSONValue.fromValue("123");
        assertEquals("\"123\"", jStr._json);
        assertEquals("123", jStr.value());
        assertEquals(jStr, new JSONValue<>("\"123\"", String.class));
    }

    @Test public void test_string_array() {
        JSONValue jStrs = JSONValue.fromValue(new String[] {"1", "2", "3"});
        assertEquals("[\"1\",\"2\",\"3\"]", jStrs._json);
        assertArrayEquals(new String[] {"1", "2", "3"}, (String[])jStrs.value());
        assertEquals(jStrs, new JSONValue<>("[\"1\",\"2\",\"3\"]", String[].class));
    }

    @Test public void test_autoparseable() {
        Map<String, Object> mKV = new HashMap<>();
        mKV.put("key", "foo");
        mKV.put("value", 123.);
        JSONValue<Map> jKV = JSONValue.fromValue(mKV);
        assertTrue("{\"key\":\"foo\",\"value\":123.0}".equals(jKV._json)
                || "{\"value\":123.0,\"key\":\"foo\"}".equals(jKV._json));
        KeyValue expected = new KeyValue("foo", 123.);
        KeyValue vKV = jKV.valueAs(KeyValue.class, KeyValueV3.class);
        assertEquals(expected.getKey(), vKV.getKey());
        assertEquals(expected.getValue(), vKV.getValue(), 1e-10);
    }

    @Test public void test_autoparseable_array() {
        Map<String, Object> mKVs[] = new HashMap[]{new HashMap(), new HashMap()};
        mKVs[0].put("key", "foo");
        mKVs[0].put("value", 123.);
        mKVs[1].put("key", "bar");
        mKVs[1].put("value", 456.);
        JSONValue<Map[]> jKVs = JSONValue.fromValue(mKVs);
        KeyValue[] expected = new KeyValue[] { new KeyValue("foo", 123.), new KeyValue("bar", 456.) };
        KeyValue[] vKVs = jKVs.valueAsArray(KeyValue[].class, KeyValueV3[].class);
        assertEquals(expected[0].getKey(), vKVs[0].getKey());
        assertEquals(expected[0].getValue(), vKVs[0].getValue(), 1e-10);
        assertEquals(expected[1].getKey(), vKVs[1].getKey());
        assertEquals(expected[1].getValue(), vKVs[1].getValue(), 1e-10);
    }

    @Test public void test_iced() {
        Map<String, Object> mKV = new HashMap<>();
        mKV.put("name", "foo");
        mKV.put("type", "Frame");
        JSONValue<Map> jKV = JSONValue.fromValue(mKV);

        KeyV3.FrameKeyV3 keyV3 = jKV.valueAsSchema(KeyV3.FrameKeyV3.class);
        assertEquals("foo", keyV3.name);
        assertEquals("Frame", keyV3.type);

        Key<Frame> expected = Key.make("foo");
        Key<Frame> key = jKV.valueAs(Key.class, KeyV3.class);
        assertEquals(expected, key);
    }

    @Test public void test_iced_array() {
        Map<String, Object> mKVs[] = new HashMap[]{new HashMap(), new HashMap()};
        mKVs[0].put("name", "foo");
        mKVs[0].put("type", "Model");
        mKVs[1].put("name", "bar");
        mKVs[1].put("type", "Model");
        JSONValue<Map[]> jKVs = JSONValue.fromValue(mKVs);

        KeyV3.ModelKeyV3[] keysV3 = jKVs.valueAsSchemas(KeyV3.ModelKeyV3[].class);
        assertEquals("foo", keysV3[0].name);
        assertEquals("bar", keysV3[1].name);
        assertEquals("Model", keysV3[0].type);
        assertEquals("Model", keysV3[1].type);

        Key<Model>[] expected = new Key[] { Key.make("foo"), Key.make("bar") };
        Key<Model>[] keys = jKVs.valueAsArray(Key[].class, KeyV3[].class);
        assertEquals(expected[0], keys[0]);
        assertEquals(expected[1], keys[1]);
    }

}
