package water.util;

import water.H2O.H2OCountedCompleter;
import water.Iced;
import water.TAtomic;

public class IcedInt extends Iced {
  public int _val;
  public IcedInt(int v){_val = v;}
  @Override public boolean equals( Object o ) {
    return o instanceof IcedInt && ((IcedInt) o)._val == _val;
  }
  @Override public int hashCode() { return _val; }
  @Override public String toString() { return Integer.toString(_val); }

  public static class AtomicIncrementAndGet extends TAtomic<IcedInt> {
    public AtomicIncrementAndGet(H2OCountedCompleter cc) {super(cc);}
    public int _val;
    @Override
    protected IcedInt atomic(IcedInt old) {
      return new IcedInt(_val = old._val + 1);
    }
  }
}