package water;

import org.apache.commons.lang.ArrayUtils;
import water.exceptions.H2OIllegalArgumentException;

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
  Class t = null;

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

  int[] i_ar; // also holds byte
  long[] l_ar;
  float[] f_ar;
  double[] d_ar;
  // boolean[] b_ar;
  String[] s_ar;
  String[] e_ar; // TODO: JavaAssist is blowing up on enum fields

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
        t = Byte.TYPE;
        // TODO: i_ar = ArrayUtils.toPrimitive((Byte[])o);
      } else if (clz == Byte.TYPE) {
        t = Byte.TYPE;
        // TODO: i_ar = ArrayUtils.toPrimitive((Byte[])o);
      } else if (clz == Integer.class) {
        t = Integer.TYPE;
        i_ar = ArrayUtils.toPrimitive((Integer[])o);
      } else if (clz == Integer.TYPE) {
        t = Integer.TYPE;
        i_ar = (int[])o;
      } else if (clz == Long.class) {
        t = Long.TYPE;
        l_ar = ArrayUtils.toPrimitive((Long[])o);
      } else if (clz == Long.TYPE) {
        t = Long.TYPE;
        l_ar = (long[]) o;
      } else if (clz == Float.class) {
        t = Float.TYPE;
        f_ar = ArrayUtils.toPrimitive((Float[])o);
      } else if (clz == Float.TYPE) {
        t = Float.TYPE;
        f_ar = (float[]) o;
      } else if (clz == Double.class) {
        t = Double.TYPE;
        d_ar = ArrayUtils.toPrimitive((Double[])o);
      } else if (clz == Double.TYPE) {
        t = Double.TYPE;
        d_ar = (double[]) o;
      } else if (clz == Boolean.class) {
        t = Boolean.TYPE;
        // TODO: AutoBuffer can't serialize arrays of booleans: b_ar = (boolean[])o;
      } else if (clz == Boolean.TYPE) {
        t = Boolean.TYPE;
        // TODO: AutoBuffer etc etc.
      } else if (clz == String.class) {
        t = String.class;
        s_ar = (String[])o;
      } else if (clz == Enum.class) {
        t = Enum.class;
        e_ar = (String[])o;
      }
    } else {
      // scalar
      if (o instanceof Byte) {
        i = (byte)o;
        t = Byte.TYPE;
      } else if (o instanceof Integer) {
        i = (int)o;
        t = Integer.TYPE;
      } else if (o instanceof Long) {
        l = (long)o;
        t = Long.TYPE;
      } else if (o instanceof Float) {
        f = (float)o;
        t = Float.TYPE;
      } else if (o instanceof Double) {
        d = (double)o;
        t = Double.TYPE;
      } else if (o instanceof Boolean) {
        b = (boolean)o;
        t = Boolean.TYPE;
      } else if (o instanceof String) {
        s = (String)o;
        t = String.class;
      } else if (o instanceof Enum) {
        e = ((Enum)o).toString();
        t = Enum.class;
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
      if (t == Byte.TYPE) {
        throw H2O.unimpl();
        // TODO: i_ar = ArrayUtils.toPrimitive((Byte[])o);
      } else if (t == Integer.TYPE) {
        return i_ar;
      } else if (t == Long.TYPE) {
        return l_ar;
      } else if (t == Float.TYPE) {
        return f_ar;
      } else if (t == Double.TYPE) {
        return d_ar;
      } else if (t == Boolean.TYPE) {
        throw H2O.unimpl();
        // TODO: AutoBuffer can't serialize arrays of booleans: b_ar = (boolean[])o;
      } else if (t == String.class) {
        return s_ar;
      } else if (t == Enum.class) {
        return e_ar;
      }
    } else {
      if (t == Byte.TYPE) {
        return i;
      } else if (t == Integer.TYPE) {
        return i;
      } else if (t == Long.TYPE) {
        return l;
      } else if (t == Float.TYPE) {
        return f;
      } else if (t == Double.TYPE) {
        return d;
      } else if (t == Boolean.TYPE) {
        return b;
      } else if (t == String.class) {
        return s;
      } else if (t == Enum.class) {
        return e;
      }
    }
    throw new H2OIllegalArgumentException(this.toString());
  }

  @Override
  public String toString() {
    if (null == t) {
      return "(null)";
    } else if (is_array) {
      // TODO: return Arrays.toString(ar);
    } else if (Byte.TYPE == t) {
      return "" + i;
    } else if (Integer.TYPE == t) {
      return "" + i;
    } else if (Long.TYPE == t) {
      return "" + l;
    } else if (Float.TYPE == t) {
      return "" + f;
    } else if (Double.TYPE == t) {
      return "" + d;
    } else if (Boolean.TYPE == t) {
      return "" + b;
    } else if (String.class == t) {
      return s;
    } else if (Enum.class == t) {
      return "" + e;
    }

    return "unhandled type";
  }


  /** Write JSON for the wrapped value without putting it inside a JSON object. */
  public AutoBuffer writeUnwrappedJSON( AutoBuffer ab ) {
    if (is_array) {
      if (Byte.TYPE == t)
        return ab.putJSONA4(i_ar); // NOTE: upcast
      else if (Integer.TYPE == t)
        return ab.putJSONA4(i_ar);
      else if (Long.TYPE == t)
        return ab.putJSONA8(l_ar);
      else if (Float.TYPE == t)
        return ab.putJSONA4f(f_ar);
      else if (Double.TYPE == t)
        return ab.putJSONA8d(d_ar);
      else if (Boolean.TYPE == t)
        return ab.putJSONAStr(null); // TODO: BROKEN
      else if (String.class == t)
        return ab.putJSONAStr(s_ar);
      else if (Enum.class == t)
        return ab.putJSONAStr(e_ar);
    } else {
      if (Byte.TYPE == t)
        return ab.putJSON1((byte)i);
      else if (Integer.TYPE == t)
        return ab.putJSON4(i);
      else if (Long.TYPE == t)
        return ab.putJSON8(l);
      else if (Float.TYPE == t)
        return ab.putJSON4f(f);
      else if (Double.TYPE == t)
        return ab.putJSON8d(d);
      else if (Boolean.TYPE == t)
        return ab.putJSONStrUnquoted(b ? "true" : "false");
      else if (String.class == t)
        return ab.putJSONName(s);
      else if (Enum.class == t)
        return ab.putJSONName(e);
    }

    throw H2O.fail("Unhandled type: " + t);
    // TODO: arrays
  }
}
