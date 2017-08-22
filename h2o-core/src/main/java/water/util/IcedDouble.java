package water.util;

import water.Iced;

public class IcedDouble extends Iced {
  public double _val;
  public IcedDouble(double v){_val = v;}
  @Override public boolean equals( Object o ) {
    return o instanceof IcedDouble && ((IcedDouble) o)._val == _val;
  }
  @Override public int hashCode() {
    long bits = Double.doubleToLongBits(_val);
    return (int)(bits ^ (bits >>> 32));
  }
  @Override public String toString() { return Double.toString(_val); }

  public IcedDouble setVal(double atd) {
    _val = atd;
    return this;
  }
}