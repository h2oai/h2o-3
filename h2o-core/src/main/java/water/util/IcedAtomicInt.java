package water.util;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.TypeMap;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by tomas on 3/13/17.
 */
public final class IcedAtomicInt extends AtomicInteger implements Freezable {
  private static volatile int _frozeType = 0;

  public IcedAtomicInt(){super(0);}
  public IcedAtomicInt(int val){super(val);}

  @Override
  public final AutoBuffer write(AutoBuffer ab) {
    ab.put4(get());
    return ab;
  }

  @Override
  public final IcedAtomicInt read(AutoBuffer ab) {
    set(ab.get4());
    return this;
  }

  @Override
  public AutoBuffer writeJSON(AutoBuffer ab) {
    return ab.putJSON4(get());
  }

  @Override
  public Freezable readJSON(AutoBuffer ab) {
    throw H2O.unimpl();
  }

  @Override
  public int frozenType() {
    if(_frozeType != 0) return _frozeType;
    return (_frozeType = TypeMap.getIcer(this).frozenType());
  }

  @Override
  public byte [] asBytes(){
    return write(new AutoBuffer()).buf();
  }

  @Override
  public IcedAtomicInt reloadFromBytes(byte [] ary){
    return read(new AutoBuffer(ary));
  }

  @Override
  public Freezable clone() {
    return new IcedAtomicInt(get());
  }
}
