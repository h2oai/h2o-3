package water.util;

import water.Iced;

public class IcedBoolean extends Iced {
  public boolean _val;
  public IcedBoolean(boolean v){_val = v;}
  @Override public boolean equals( Object o ) {
    return o instanceof IcedBoolean && ((IcedBoolean) o)._val == _val;
  }
  @Override public int hashCode() { return _val ? 1231 : 1237; }
  @Override public String toString() { return Boolean.toString(_val); }
}
