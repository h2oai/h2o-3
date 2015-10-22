package water.util;

import water.Iced;

public class IcedInt extends Iced {
  public int _val;
  public IcedInt(int v){_val = v;}
  @Override public boolean equals( Object o ) {
    return o instanceof IcedInt && ((IcedInt) o)._val == _val;
  }
  @Override public int hashCode() { return _val; }
  @Override public String toString() { return Integer.toString(_val); }
}