package water;

import org.apache.commons.lang.ArrayUtils;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OIllegalArgumentException;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Iced wrapper object for primitive types and arrays, to allow fields in other Iced
 * classes to have a generic type equivalent to Object, which can contain primitives,
 * arrays, and Iced objects.
 */
public class IcedWrapper extends Iced {
  /**
   * Is the wrapped value an array?
   */
  boolean is_array;

  /**
   * Class of the wrapped type (could be a class of a primitive type like Integer.TYPE).
   */
  String t = null;

  /**
   * Fields containing the wrapped value:
   */
  int i; // also holds byte
  long l;
  float f;
  double d;
  boolean b;
  String s;
  String e; // TODO: JavaAssist is blowing up on enum fields
  KeyV3 k;

  int[] i_ar; // also holds byte
  long[] l_ar;
  float[] f_ar;
  double[] d_ar;
  // boolean[] b_ar;
  String[] s_ar;
  String[] e_ar; // TODO: JavaAssist is blowing up on enum fields
  KeyV3[] k_ar;
  Iced[] iced_ar;

  public IcedWrapper(Object o) {
    if (null == o) {
      this.t = null;
      return;
    }

    this.is_array = o.getClass().isArray();

    if (is_array) {
      // array (1D only, for now)
      Class clz = o.getClass().getComponentType();

      if (clz == Byte.class) {
        t = "Byte";
        // TODO: i_ar = ArrayUtils.toPrimitive((Byte[])o);
      } else if (clz == Byte.TYPE) {
        t = "B";
        // TODO: i_ar = ArrayUtils.toPrimitive((Byte[])o);
      } else if (clz == Integer.class) {
        t = "I";
        i_ar = ArrayUtils.toPrimitive((Integer[])o);
      } else if (clz == Integer.TYPE) {
        t = "I";
        i_ar = (int[])o;
      } else if (clz == Long.class) {
        t = "L";
        l_ar = ArrayUtils.toPrimitive((Long[])o);
      } else if (clz == Long.TYPE) {
        t = "L";
        l_ar = (long[]) o;
      } else if (clz == Float.class) {
        t = "F";
        f_ar = ArrayUtils.toPrimitive((Float[])o);
      } else if (clz == Float.TYPE) {
        t = "F";
        f_ar = (float[]) o;
      } else if (clz == Double.class) {
        t = "D";
        d_ar = ArrayUtils.toPrimitive((Double[])o);
      } else if (clz == Double.TYPE) {
        t = "D";
        d_ar = (double[]) o;
      } else if (clz == Boolean.class) {
        t = "Bo";
        // TODO: AutoBuffer can't serialize arrays of booleans: b_ar = (boolean[])o;
      } else if (clz == Boolean.TYPE) {
        t = "Bo";
        // TODO: AutoBuffer etc etc.
      } else if (clz == String.class) {
        t = "S";
        s_ar = (String[])o;
      } else if (clz.isEnum()) {
        t = "E";
        e_ar = new String[Array.getLength(o)];
        for (int i = 0; i < e_ar.length; i++)
          e_ar[i] = Array.get(o, i).toString();
      } else if (o instanceof KeyV3[]) {
        t = "K";
        k_ar = (KeyV3[])o;
      } else if (o instanceof Iced[]) {
        t = "Iced";
        iced_ar = (Iced[])o;
      }
    } else {
      // scalar
      if (o instanceof Byte) {
        i = (byte)o;
        t = "B";
      } else if (o instanceof Integer) {
        i = (int)o;
        t = "I";
      } else if (o instanceof Long) {
        l = (long)o;
        t = "L";
      } else if (o instanceof Float) {
        f = (float)o;
        t = "F";
      } else if (o instanceof Double) {
        d = (double)o;
        t = "D";
      } else if (o instanceof Boolean) {
        b = (boolean)o;
        t = "Bo";
      } else if (o instanceof String) {
        s = (String)o;
        t = "S";
      } else if (o instanceof Enum) {
        e = ((Enum)o).toString();
        t = "E";
      } else if (o instanceof KeyV3) {
        k = (KeyV3)o;
        t = "K";
      }
    }

    if (null == t)
      throw new H2OIllegalArgumentException("o", "IcedWrapper", o);
  }

  public Object get() {
    if (t == null) {
      return null;
    }
    if (is_array) {
      if (t == "byte") {
        throw H2O.fail();
        // TODO: i_ar = ArrayUtils.toPrimitive((Byte[])o);
      } else if (t.equals("I")) {
        return i_ar;
      } else if (t.equals("L")) {
        return l_ar;
      } else if (t.equals("F")) {
        return f_ar;
      } else if (t.equals("D")) {
        return d_ar;
      } else if (t.equals("Bo")) {
        throw H2O.fail();
        // TODO: AutoBuffer can't serialize arrays of booleans: b_ar = (boolean[])o;
      } else if (t.equals("S")) {
        return s_ar;
      } else if (t.equals("E")) {
        return e_ar;
      } else if (t.equals("K")) {
        return k_ar;
      } else if (t.equals("Iced")) {
        return iced_ar;
      }
    } else {
      if (t.equals("B")) {
        return i;
      } else if (t.equals("I")) {
        return i;
      } else if (t.equals("L")) {
        return l;
      } else if (t.equals("F")) {
        return f;
      } else if (t.equals("D")) {
        return d;
      } else if (t.equals("Bo")) {
        return b;
      } else if (t.equals("S")) {
        return s;
      } else if (t.equals("E")) {
        return e;
      } else if (t.equals("K")) {
        return k;
      }
    }
    throw new H2OIllegalArgumentException(this.toString());
  }

  @Override
  public String toString() {
    if (null == t) {
      return "(null)";
    } else if (is_array) {
      if (t.equals("I"))
        return Arrays.toString(i_ar);
      else if (t.equals("L"))
        return Arrays.toString(l_ar);
      else if (t.equals("F"))
        return Arrays.toString(f_ar);
      else if (t.equals("D"))
        return Arrays.toString(d_ar);
      else if (t.equals("S"))
        return Arrays.toString(s_ar);
      else if (t.equals("E"))
        return Arrays.toString(e_ar);
      else if (t.equals("K"))
        return Arrays.toString(k_ar);
    } else if (t.equals("B")) {
      return "" + i;
    } else if (t.equals("I")) {
      return "" + i;
    } else if (t.equals("L")) {
      return "" + l;
    } else if (t.equals("F")) {
      return "" + f;
    } else if (t.equals("D")) {
      return "" + d;
    } else if (t.equals("Bo")) {
      return "" + b;
    } else if (t.equals("S")) {
      return s;
    } else if (t.equals("E")) {
      return "" + e;
    } else if (t.equals("K")) {
      return "" + k;
    }

    return "unhandled type";
  }


  /** Write JSON for the wrapped value without putting it inside a JSON object. */
  public AutoBuffer writeUnwrappedJSON( AutoBuffer ab ) {
    if (is_array) {
      if (t.equals("B"))
        return ab.putJSONA4(i_ar); // NOTE: upcast
      else if (t.equals("I"))
        return ab.putJSONA4(i_ar);
      else if (t.equals("L"))
        return ab.putJSONA8(l_ar);
      else if (t.equals("F"))
        return ab.putJSONA4f(f_ar);
      else if (t.equals("D"))
        return ab.putJSONA8d(d_ar);
      else if (t.equals("Bo"))
        return ab.putJSONAStr(null); // TODO: BROKEN
      else if (t.equals("S"))
        return ab.putJSONAStr(s_ar);
      else if (t.equals("E"))
        return ab.putJSONAStr(e_ar);
      else if (t.equals("K"))
        return ab.putJSONA(k_ar);
      else if (t.equals("Iced"))
        return ab.putJSONA(iced_ar);
    } else {
      if (t.equals("B"))
        return ab.putJSON1((byte)i);
      else if (t.equals("I"))
        return ab.putJSON4(i);
      else if (t.equals("L"))
        return ab.putJSON8(l);
      else if (t.equals("F"))
        return ab.putJSON4f(f);
      else if (t.equals("D"))
        return ab.putJSON8d(d);
      else if (t.equals("Bo"))
        return ab.putJSONStrUnquoted(b ? "true" : "false");
      else if (t.equals("S"))
        return ab.putJSONName(s);
      else if (t.equals("E"))
        return ab.putJSONName(e);
      else if (t.equals("K"))
        return ab.putJSON(k);
    }

    throw H2O.fail("Unhandled type: " + t);
    // TODO: arrays
  }
}
