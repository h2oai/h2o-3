package water.util;

import water.Iced;

public class IcedLong extends Iced {
  public long _val;
  public IcedLong(long v){_val = v;}
  @Override public boolean equals( Object o ) {
    return o instanceof IcedLong && ((IcedLong) o)._val == _val;
  }
  @Override public int hashCode() { return new Long(_val).hashCode(); }
  @Override public String toString() { return Long.toString(_val); }

  public static IcedLong valueOf(long value) {
    return new IcedLong(value);
  }
}