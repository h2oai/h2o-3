package water.util;

import hex.KeyValue;
import org.junit.Test;
import water.api.schemas3.KeyValueV3;

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

}
