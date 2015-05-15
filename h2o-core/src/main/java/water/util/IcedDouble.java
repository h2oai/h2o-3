package water.util;

import water.Iced;

public class IcedDouble extends Iced {
  public double _val;
  public IcedDouble(double v){_val = v;}
  @Override public boolean equals( Object o ) {
    return o instanceof IcedDouble && ((IcedDouble) o)._val == _val;
  }
  @Override public int hashCode() { return new Double(_val).hashCode(); }
  @Override public String toString() { return Double.toString(_val); }
}