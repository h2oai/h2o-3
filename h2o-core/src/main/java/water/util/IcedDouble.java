package water.util;

import water.Iced;

public class IcedDouble extends Iced<IcedDouble> {
  public double _val;
  public IcedDouble(double v){_val = v;}
  @Override public boolean equals( Object o ) {
    return o instanceof IcedDouble && ((IcedDouble) o)._val == _val;
  }
  @Override public int hashCode() {
    long h = Double.doubleToLongBits(_val);
    // Doubles are lousy hashes; mix up the bits some
    h ^= (h >>> 20) ^ (h >>> 12);
    h ^= (h >>> 7) ^ (h >>> 4);
    return (int) ((h ^ (h >> 32)) & 0x7FFFFFFF);
  }
  @Override public String toString() { return Double.toString(_val); }

  public IcedDouble setVal(double atd) {
    _val = atd;
    return this;
  }
}
