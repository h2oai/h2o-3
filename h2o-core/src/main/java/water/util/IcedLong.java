package water.util;

import water.*;

public class IcedLong extends Iced {
  public long _val;
  public IcedLong(long v){_val = v;}
  @Override public boolean equals( Object o ) {
    return o instanceof IcedLong && ((IcedLong) o)._val == _val;
  }
  @Override public int hashCode() {
    return (int)(_val ^ (_val >>> 32));
  }
  @Override public String toString() { return Long.toString(_val); }

  public static IcedLong valueOf(long value) {
    return new IcedLong(value);
  }
  
  public static long get(Key key) {
    return ((IcedLong) DKV.getGet(key))._val;
  }

  public static long incrementAndGet(Key key) {
    return ((AtomicIncrementAndGet) new AtomicIncrementAndGet().invoke(key))._val;
  }
  
  public static long decrementAndGet(Key key) {
    return ((AtomicDecrementAndGet) new AtomicDecrementAndGet().invoke(key))._val;
  }

  public static class AtomicIncrementAndGet extends TAtomic<IcedLong> {
    public AtomicIncrementAndGet() {
      this(null);
    }
    public AtomicIncrementAndGet(H2O.H2OCountedCompleter cc) {
      super(cc);
    }

    // OUT
    public long _val;

    @Override
    protected IcedLong atomic(IcedLong old) {
      return new IcedLong(_val = old._val + 1);
    }
  }

  public static class AtomicDecrementAndGet extends TAtomic<IcedLong> {
    public AtomicDecrementAndGet() {
      this(null);
    }
    public AtomicDecrementAndGet(H2O.H2OCountedCompleter cc) {
      super(cc);
    }

    // OUT
    public long _val;

    @Override
    protected IcedLong atomic(IcedLong old) {
      return new IcedLong(_val = old._val - 1);
    }
  }


}
