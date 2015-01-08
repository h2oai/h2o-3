package water;

import water.exceptions.H2OIllegalArgumentException;

/**
 * Iced wrapper object for primitive types and arrays, to allow fields in other Iced
 * classes to have a generic type equivalent to Object, which can contain primitives,
 * arrays, and Iced objects.
 */
public class IcedWrapper extends Iced {
  /**
   * Class of the wrapped type (could be a class of a primitive type like Integer.TYPE).
   */
  Class t;

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


  public IcedWrapper(Object o) {
    if (null == o) {
      this.t = null;
    } else if (o instanceof Byte) {
      i = (byte)o;
      t = Byte.class;
    } else if (o instanceof Integer) {
      i = (int)o;
      t = Integer.class;
    } else if (o instanceof Long) {
      l = (long)o;
      t = Long.class;
    } else if (o instanceof Float) {
      f = (float)o;
      t = Float.class;
    } else if (o instanceof Double) {
      d = (double)o;
      t = Double.class;
    } else if (o instanceof Boolean) {
      b = (boolean)o;
      t = Boolean.class;
    } else if (o instanceof String) {
      s = (String)o;
      t = String.class;
    } else if (o instanceof Enum) {
      e = ((Enum)o).toString();
      t = Enum.class;
    } else {
        throw new H2OIllegalArgumentException("o", "IcedWrapper", o);
    }
  }

  @Override
  public String toString() {
    if (null == t) {
      return "(null)";
    } else if (Byte.class == t) {
      return "" + i;
    } else if (Integer.class == t) {
      return "" + i;
    } else if (Long.class == t) {
      return "" + l;
    } else if (Float.class == t) {
      return "" + f;
    } else if (Double.class == t) {
      return "" + d;
    } else if (Boolean.class == t) {
      return "" + b;
    } else if (String.class == t) {
      return s;
    } else if (Enum.class == t) {
      return "" + e;
    } else {
      return "unhandled type";
    }
  }

  @Override
  public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    ab.putJSONStr("value").put1(':');
    if (Byte.class == t)
      return ab.putJSON1((byte)i);
    else if (Integer.class == t)
        return ab.putJSON4(i);
    else if (Long.class == t)
      return ab.putJSON8(l);
    else if (Float.class == t)
      return ab.putJSON4f(f);
    else if (Double.class == t)
      return ab.putJSON8d(d);
    else if (Boolean.class == t)
      return ab.putJSONStrUnquoted(b ? "true" : "false");
    else if (String.class == t)
      return ab.putJSONName(s);
    else if (Enum.class == t)
      return ab.putJSONName(e);

    throw H2O.fail("Unhandled type: " + t);
    // TODO: arrays
  }
}
