package water.util;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.Iced;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.apache.commons.lang.ArrayUtils.toObject;
import static org.apache.commons.lang.ArrayUtils.toPrimitive;

/**
 * Iced / Freezable NonBlockingHashMap abstract base class.
 */
public abstract class IcedHashMapBase<K, V> extends Iced implements Map<K, V>, Cloneable, Serializable {

  public enum KeyType {
    String(String.class),
    Freezable(Freezable.class),
    ;

    Class _clazz;
    KeyType(Class clazz) {
      _clazz = clazz;
    }
  }

  public enum ValueType {
    String(String.class),
    Freezable(Freezable.class),
    Boolean(Boolean.class, boolean.class),
    Integer(Integer.class, int.class),
    Long(Long.class, long.class),
    Float(Float.class, float.class),
    Double(Double.class, double.class),
    ;

    Class _clazz;
    Class _arrayClazz;
    Class _primitiveArrayClazz;
    ValueType(Class clazz) {
      this(clazz, Void.class);
    }
    ValueType(Class clazz, Class primitiveClazz) {
      _clazz = clazz;
      _arrayClazz = Array.newInstance(_clazz, 0).getClass();
      _primitiveArrayClazz = Array.newInstance(primitiveClazz, 0).getClass();
    }
  }

  public enum ArrayType {
    None, Array, PrimitiveArray
  }

  private transient volatile boolean _write_lock;
  abstract protected Map<K,V> map();
  public int size()                                     { return map().size(); }
  public boolean isEmpty()                              { return map().isEmpty(); }
  public boolean containsKey(Object key)                { return map().containsKey(key); }
  public boolean containsValue(Object value)            { return map().containsValue(value); }
  public V get(Object key)                              { return (V)map().get(key); }
  public V put(K key, V value)                          { assert writeable(); return (V)map().put(key, value);}
  public V remove(Object key)                           { assert writeable(); return map().remove(key); }
  public void putAll(Map<? extends K, ? extends V> m)   { assert writeable();        map().putAll(m); }
  public void clear()                                   { assert writeable();        map().clear(); }
  public Set<K> keySet()                                { return map().keySet(); }
  public Collection<V> values()                         { return map().values(); }
  public Set<Entry<K, V>> entrySet()                    { return map().entrySet(); }
  @Override public boolean equals(Object o)             { return map().equals(o); }
  @Override public int hashCode()                       { return map().hashCode(); }
  @Override public String toString()                    { return map().toString(); }

  private static final byte empty_map = -1;  // for future extensions, -1 becomes valid mode if for all enums there's an entry corresponding to their max allocated bits
  static KeyType keyType(byte mode) { return KeyType.values()[mode & 0x3]; } // first 2 bits encode key type
  static ValueType valueType(byte mode) { return ValueType.values()[mode>>>2 & 0xF];} // 3rd to 6th bit encodes value type
  static ArrayType arrayType(byte mode) { return ArrayType.values()[mode>>>6 & 0x3]; } // 7th to 8th bit encodes array type

  private static byte getMode(KeyType keyType, ValueType valueType, ArrayType arrayType) {
    return (byte) ((arrayType.ordinal() << 6) | (valueType.ordinal() << 2) | (keyType.ordinal()));
  }

  private KeyType getKeyType(K key) {
    assert key != null;
    return Stream.of(KeyType.values())
            .filter(t -> isValidKey(key, t))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("keys of type "+key.getClass().getTypeName()+" are not supported"));
  }

  private ValueType getValueType(V value) {
    ArrayType arrayType = getArrayType(value);
    return Stream.of(ValueType.values())
            .filter(t -> isValidValue(value, t, arrayType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("values of type "+value.getClass().getTypeName()+" are not supported"));
  }

  private ArrayType getArrayType(V value) {
    if (value != null && value.getClass().isArray()) {
      if (value.getClass().getComponentType().isPrimitive()) return ArrayType.PrimitiveArray;
      return ArrayType.Array;
    }
    return ArrayType.None;
  }

  boolean isValidKey(K key, KeyType keyType) {
    return keyType._clazz.isInstance(key);
  }

  boolean isValidValue(V value, ValueType valueType, ArrayType arrayType) {
    if (value == null) return false;
    switch (arrayType) {
      case None: return valueType._clazz.isInstance(value);
      case Array: return valueType._arrayClazz.isInstance(value);
      case PrimitiveArray: return valueType._primitiveArrayClazz.isInstance(value);
      default: return false;
    }
  }

  // This comment is stolen from water.parser.Categorical:
  //
  // Since this is a *concurrent* hashtable, writing it whilst its being
  // updated is tricky.  If the table is NOT being updated, then all is written
  // as expected.  If the table IS being updated we only promise to write the
  // Keys that existed at the time the table write began.  If elements are
  // being deleted, they may be written anyways.  If the Values are changing, a
  // random Value is written.
  public final AutoBuffer write_impl( AutoBuffer ab ) {
    _write_lock = true;
    try {
      if (map().size() == 0) return ab.put1(empty_map);
      Entry<K, V> entry = map().entrySet().iterator().next();
      K key = entry.getKey();
      V val = entry.getValue();
      assert key != null && val != null;
      byte mode = getMode(getKeyType(key), getValueType(val), getArrayType(val));
      ab.put1(mode);              // Type of hashmap being serialized
      writeMap(ab, mode);         // Do the hard work of writing the map
      switch (keyType(mode)) {
        case String:
          return ab.putStr(null);
        case Freezable:
        default:
          return ab.put(null);
      }
    } catch (Throwable t) {
      throw H2O.fail("Iced hash map serialization failed!" + t.toString() + ", msg = " + t.getMessage(), t);
    } finally {
      _write_lock = false;
    }
  }

  abstract protected Map<K,V> init();

  /**
   * Can the map be modified?
   * 
   * By default we don't make any assumptions about the implementation of the backing Map and we will write-lock
   * the map when we are trying to serialize it. However, if the specific implementation knows it is safe to modify
   * the map when it is being written, it can bypass the write-lock by overriding this method. 
   * 
   * @return true if map can be modified 
   */
  protected boolean writeable() {
    return !_write_lock;
  }

  protected void writeMap(AutoBuffer ab, byte mode) {
    KeyType keyType = keyType(mode);
    ValueType valueType = valueType(mode);
    ArrayType arrayType = arrayType(mode);
    for( Entry<K, V> e : map().entrySet() ) {
      K key = e.getKey();   assert key != null;
      V val = e.getValue(); assert val != null;

      writeKey(ab, keyType, key);
      writeValue(ab, valueType, arrayType, val);
    }
  }

  protected void writeKey(AutoBuffer ab, KeyType keyType, K key) {
    switch (keyType) {
      case String:      ab.putStr((String)key); break;
      case Freezable:   ab.put((Freezable)key); break;
    }
  }

  protected void writeValue(AutoBuffer ab, ValueType valueType, ArrayType arrayType, V value) {
    switch (arrayType) {
      case None:
        switch (valueType) {
          case String:    ab.putStr((String)value); break;
          case Freezable: ab.put((Freezable)value); break;
          case Boolean:   ab.put1((Boolean)value ? 1 : 0); break;
          case Integer:   ab.put4((Integer)value); break;
          case Long:      ab.put8((Long)value); break;
          case Float:     ab.put4f((Float)value); break;
          case Double:    ab.put8d((Double)value); break;
        }
        break;
      case Array:
        switch (valueType) {
          case String:    ab.putAStr((String[])value); break;
          case Freezable: ab.putA((Freezable[])value); break;
          case Boolean:   ab.putA1(bools2bytes(toPrimitive((Boolean[])value))); break;
          case Integer:   ab.putA4(toPrimitive((Integer[])value)); break;
          case Long:      ab.putA8(toPrimitive((Long[])value)); break;
          case Float:     ab.putA4f(toPrimitive((Float[])value)); break;
          case Double:    ab.putA8d(toPrimitive((Double[])value)); break;
        }
        break;
      case PrimitiveArray:
        switch (valueType) {
          case Boolean:   ab.putA1(bools2bytes((boolean[])value)); break;
          case Integer:   ab.putA4((int[])value); break;
          case Long:      ab.putA8((long[])value); break;
          case Float:     ab.putA4f((float[])value); break;
          case Double:    ab.putA8d((double[])value); break;
        }
        break;
    }
  }

  @SuppressWarnings("unchecked")
  protected K readKey(AutoBuffer ab, KeyType keyType) {
    switch (keyType) {
      case String: return (K) ab.getStr();
      case Freezable: return ab.get();
      default: return null;
    }
  }

  @SuppressWarnings("unchecked")
  protected V readValue(AutoBuffer ab, ValueType valueType, ArrayType arrayType) {
    switch (arrayType) {
      case None:
        switch (valueType) {
          case String:    return (V) ab.getStr();
          case Freezable: return (V) ab.get();
          case Boolean:   return (V) Boolean.valueOf(ab.get1() == 1);
          case Integer:   return (V) Integer.valueOf(ab.get4());
          case Long:      return (V) Long.valueOf(ab.get8());
          case Float:     return (V) Float.valueOf(ab.get4f());
          case Double:    return (V) Double.valueOf(ab.get8d());
          default:        return null;
        }
      case Array:
        switch (valueType) {
          case String:    return (V) ab.getAStr();
          case Freezable: return (V) ab.getA(Freezable.class);
          case Boolean:   return (V) toObject(bytes2bools(ab.getA1()));
          case Integer:   return (V) toObject(ab.getA4());
          case Long:      return (V) toObject(ab.getA8());
          case Float:     return (V) toObject(ab.getA4f());
          case Double:    return (V) toObject(ab.getA8d());
          default:        return null;
        }
      case PrimitiveArray:
        switch (valueType) {
          case Boolean:   return (V) bytes2bools(ab.getA1());
          case Integer:   return (V) ab.getA4();
          case Long:      return (V) ab.getA8();
          case Float:     return (V) ab.getA4f();
          case Double:    return (V) ab.getA8d();
          default:        return null;
        }
      default:
        return null;
    }
  }

  /**
   * Helper for serialization - fills the mymap() from K-V pairs in the AutoBuffer object
   * @param ab Contains the serialized K-V pairs
   */
  public final IcedHashMapBase read_impl(AutoBuffer ab) {
    try {
      assert map() == null || map().isEmpty(); // Fresh from serializer, no constructor has run
      Map<K, V> map = init();
      byte mode = ab.get1();
      if (mode == empty_map) return this;
      KeyType keyType = keyType(mode);
      ValueType valueType = valueType(mode);
      ArrayType arrayType = arrayType(mode);

      while (true) {
        K key = readKey(ab, keyType);
        if (key == null) break;
        V val = readValue(ab, valueType, arrayType);
        map.put(key, val);
      }
      return this;
    } catch(Throwable t) {
      if (null == t.getCause()) {
        throw H2O.fail("IcedHashMap deserialization failed! + " + t.toString() + ", msg = " + t.getMessage() + ", cause: null", t);
      } else {
        throw H2O.fail("IcedHashMap deserialization failed! + " + t.toString() + ", msg = " + t.getMessage() +
                ", cause: " + t.getCause().toString() +
                ", cause msg: " + t.getCause().getMessage() +
                ", cause stacktrace: " + java.util.Arrays.toString(t.getCause().getStackTrace()));
      }
    }
  }

  public final IcedHashMapBase readJSON_impl( AutoBuffer ab ) {throw H2O.unimpl();}

  public final AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    boolean first = true;
    for (Entry<K, V> entry : map().entrySet()) {
      K key = entry.getKey();
      V value = entry.getValue();

      KeyType keyType = getKeyType(key);
      assert keyType == KeyType.String: "JSON format supports only String keys";
      ValueType valueType = getValueType(value);
      ArrayType arrayType = getArrayType(value);

      if (first) { first = false; } else {ab.put1(',').put1(' '); }
      String name = (String) key;
      switch (arrayType) {
        case None:
          switch (valueType) {
            case String:    ab.putJSONStr(name, (String) value); break;
            case Freezable: ab.putJSON(name, (Freezable) value); break;
            case Boolean:   ab.putJSONStrUnquoted(name, Boolean.toString((Boolean)value)); break;
            case Integer:   ab.putJSON4(name, (Integer) value); break;
            case Long:      ab.putJSON8(name, (Long) value); break;
            case Float:     ab.putJSON4f(name, (Float) value); break;
            case Double:    ab.putJSON8d(name, (Double) value); break;
          }
          break;
        case Array:
          switch (valueType) {
            case String:    ab.putJSONAStr(name, (String[]) value); break;
            case Freezable: ab.putJSONA(name, (Freezable[]) value); break;
            case Boolean:   ab.putJSONStrUnquoted(name, Arrays.toString(toPrimitive((Boolean[]) value))); break;
            case Integer:   ab.putJSONA4(name, toPrimitive((Integer[]) value)); break;
            case Long:      ab.putJSONA8(name, toPrimitive((Long[]) value)); break;
            case Float:     ab.putJSONA4f(name, toPrimitive((Float[]) value)); break;
            case Double:    ab.putJSONA8d(name, toPrimitive((Double[]) value)); break;
          }
          break;
        case PrimitiveArray:
          switch (valueType) {
            case Boolean:   ab.putJSONStrUnquoted(name, Arrays.toString((boolean[]) value)); break;
            case Integer:   ab.putJSONA4(name, (int[]) value); break;
            case Long:      ab.putJSONA8(name, (long[]) value); break;
            case Float:     ab.putJSONA4f(name, (float[]) value); break;
            case Double:    ab.putJSONA8d(name, (double[]) value); break;
          }
          break;
      }
    }
    return ab;
  }

  private static byte[] bools2bytes(boolean[] bools) {
    byte[] bytes = new byte[bools.length];
    for (int i=0; i<bools.length; i++)
      bytes[i] = bools[i] ? (byte)1 : 0;
    return bytes;
  }

  private static boolean[] bytes2bools(byte[] bytes) {
    boolean[] bools = new boolean[bytes.length];
    for(int i=0; i<bytes.length; i++)
      bools[i] = bytes[i] == 1;
    return bools;
  }
}
