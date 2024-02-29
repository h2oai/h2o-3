package water.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import water.AutoBuffer;
import water.H2O;
import water.Key;
import water.TestUtil;

import java.io.*;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class IcedHashMapTest extends TestUtil {

    private static final String suppress_shutdown_on_failure_property = H2O.OptArgs.SYSTEM_PROP_PREFIX+"suppress.shutdown.on.failure";

    @BeforeClass
    public static void setUp() {
        stall_till_cloudsize(1);  // necessary for Freezable key/values
    }

    @Rule
    public final ProvideSystemProperty provideSystemProperty =
            new ProvideSystemProperty(suppress_shutdown_on_failure_property, "true");

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    private Gson gson = new Gson();

    private void assertMapEquals(Map expected, Map actual) {
//        System.out.println(gson.toJson(expected));
//        System.out.println(gson.toJson(actual));
        assertEquals(gson.toJson(new TreeMap<>(expected)), gson.toJson(new TreeMap<>(actual)));  // this ensures deep comparison of values (esp. if arrays)
    }

    private <K, V> void testWriteRead(Map<K, V> map) {
        IcedHashMap<K, V> write = new IcedHashMap<>();
        write.putAll(map);
        assertEquals(map, write);

        IcedHashMap<K, V> read = new AutoBuffer().put(write).flipForReading().get();
        System.out.println(read);
        assertMapEquals(map, read);
    }

    private <K, V> void testWriteJSON(Map<K, V> map) {
        testWriteJSON(map, map.getClass());
    }

    private <K, V> void testWriteJSON(Map<K, V> map, Type type) {
        IcedHashMap<K, V> write = new IcedHashMap<>();
        write.putAll(map);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        AutoBuffer ab = new AutoBuffer(os, false);
        write.writeJSON_impl(ab);
        ab.close();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(os.toByteArray())))) {
            String pseudo_json = br.lines().collect(Collectors.joining());
//            System.out.println(pseudo_json);
            assertEquals(0, pseudo_json.charAt(0));
            String json = "{"+pseudo_json.substring(1)+"}"; // removing first char
            Map<K, V> jsonToMap = gson.fromJson(json, type);
//            System.out.println(jsonToMap);
            assertMapEquals(map, jsonToMap);
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Test public void testStringString() {
        final Map<String, String> map = Collections.unmodifiableMap(new HashMap<String, String>() {{
            put("one", "first");
            put("two", "second");
            put("three", "third");
        }});
        testWriteRead(map);
        testWriteJSON(map);
    }

    @Test public void testStringStringArray() {
        final Map<String, String[]> map = Collections.unmodifiableMap(new HashMap<String, String[]>() {{
            put("one", new String[] {"un", "uno", "eins", "jeden"});
            put("two", new String[] {"deux", "duo", "zwei", "dva"});
            put("three", new String[] {"trois", "tre", "drei", "tři"});
        }});
        testWriteRead(map);
        testWriteJSON(map);
    }

    @Test public void testStringInteger() {
        final Map<String, Integer> map = Collections.unmodifiableMap(new HashMap<String, Integer>() {{
            put("one", 1);
            put("two", 2);
            put("three", 3);
        }});
        testWriteRead(map);
        testWriteJSON(map, new TypeToken<Map<String, Integer>>(){}.getType());
    }

    @Test public void testStringIntegerArray() {
        final Map<String, Integer[]> map = Collections.unmodifiableMap(new HashMap<String, Integer[]>() {{
            put("one", new Integer[] {1});
            put("two", new Integer[] {2, 2});
            put("three", new Integer[] {3, 3, 3});
        }});
        testWriteRead(map);
        testWriteJSON(map, new TypeToken<Map<String, Integer[]>>(){}.getType());
    }

    @Test public void testStringIntArray() {
        final Map<String, int[]> map = Collections.unmodifiableMap(new HashMap<String, int[]>() {{
            put("one", new int[] {1});
            put("two", new int[] {2, 2});
            put("three", new int[] {3, 3, 3});
        }});
        testWriteRead(map);
        testWriteJSON(map, new TypeToken<Map<String, int[]>>(){}.getType());
    }

    @Test public void testStringDouble() {
        final Map<String, Double> map = Collections.unmodifiableMap(new HashMap<String, Double>() {{
            put("one", 1.1);
            put("two", 2.2);
            put("three", 3.3);
        }});
        testWriteRead(map);
        testWriteJSON(map);
    }

    @Test public void testStringDoubleArray() {
        final Map<String, Double[]> map = Collections.unmodifiableMap(new HashMap<String, Double[]>() {{
            put("one", new Double[] {1.1});
            put("two", new Double[] {2.1, 2.2});
            put("three", new Double[] {3.1, 3.2, 3.3});
        }});
        testWriteRead(map);
        testWriteJSON(map);
    }

    @Test public void testStringDoublePrimitiveArray() {
        final Map<String, double[]> map = Collections.unmodifiableMap(new HashMap<String, double[]>() {{
            put("one", new double[] {1.1});
            put("two", new double[] {2.1, 2.2});
            put("three", new double[] {3.1, 3.2, 3.3});
        }});
        testWriteRead(map);
        testWriteJSON(map);
    }

    @Test public void testStringBoolean() {
        final Map<String, Boolean> map = Collections.unmodifiableMap(new HashMap<String, Boolean>() {{
            put("one", true);
            put("two", false);
            put("three", true);
            put("four", false);
            put("five", true);
        }});
        testWriteRead(map);
        testWriteJSON(map);
    }

    @Test public void testStringBooleanArray() {
        final Map<String, Boolean[]> map = Collections.unmodifiableMap(new HashMap<String, Boolean[]>() {{
            put("one", new Boolean[] {true, false});
            put("two", new Boolean[] {false, true});
            put("three", new Boolean[] {true, true});
            put("four", new Boolean[] {false, false});
            put("five", new Boolean[] {true, true});
            // game: find the logical suite
        }});
        testWriteRead(map);
        testWriteJSON(map);
    }

    @Test public void testLongFreezable() {
      final Map<Long, Key> map = Collections.unmodifiableMap(new HashMap<Long, Key>() {{
        put(1L, Key.make("one"));
        put(2L, Key.make("two"));
        put(3L, Key.make("three"));
      }});
      testWriteRead(map);
    }
    @Test public void testStringFreezable() {
        final Map<String, Key> map = Collections.unmodifiableMap(new HashMap<String, Key>() {{
            put("one", Key.make("one"));
            put("two", Key.make("two"));
            put("three", Key.make("three"));
        }});
        testWriteRead(map);
    }

    @Test public void testStringFreezableArray() {
        final Map<String, Key[]> map = Collections.unmodifiableMap(new HashMap<String, Key[]>() {{
            put("one", new Key[]{ Key.make("un"), Key.make("eins"), Key.make("jeden")});
            put("two", new Key[]{ Key.make("deux"), Key.make("zwei"), Key.make("dva")});
            put("three", new Key[]{ Key.make("trois"), Key.make("drei"), Key.make("tři")});
        }});
        testWriteRead(map);
    }

    @Test public void testFreezableString() {
        final Map<Key, String> map = Collections.unmodifiableMap(new HashMap<Key, String>() {{
            put(Key.make("one"), "one");
            put(Key.make("two"), "two");
            put(Key.make("three"), "three");
        }});
        testWriteRead(map);
    }

    @Test(expected = IllegalStateException.class)
    public void arrayKeysNotSupported() {
        //because it's very wrong to use arrays or any other mutable object as keys!
        final Map<String[], String> map = Collections.unmodifiableMap(new HashMap<String[], String>() {{
            put(new String[]{"one"}, "first");
            put(new String[]{"two"}, "second");
            put(new String[]{"three"}, "third");
        }});
        testWriteRead(map);
    }

    @Test(expected = NullPointerException.class)
    public void nullValuesAreNotSupported() {
        // because backed by NonBlockingHashMap which doesn't support null values
        final Map<String, String> map = Collections.unmodifiableMap(new HashMap<String, String>() {{
            put("three", "third");
            put("two", "two");
            put("one", "first");
            put("zero", null);
        }});
        testWriteRead(map);
    }

    public void testEmptyMap() {
        testWriteRead(Collections.emptyMap());
        testWriteJSON(Collections.emptyMap());
    }
}
