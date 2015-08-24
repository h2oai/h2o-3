package water.util;

import water.*;

import java.util.ArrayList;

/**
 * Simple wrapper around ArrayList with support for H2O serialization
 * @param <T>
 */
public class IcedArrayList<T extends Iced> extends ArrayList<T> implements Freezable {
  private final ArrayList<T> _aList;
  public IcedArrayList() {_aList = new ArrayList<T>();}

  @Override public AutoBuffer write(AutoBuffer ab) {
    ab.put4(size());
    for(T t:this)
      ab.put(t);
    return ab;
  }
  @Override public IcedArrayList<T> read(AutoBuffer ab) {
    int n = ab.get4();
    for(int i = 0; i < n; ++i)
      add(ab.<T>get());
    return this;
  }

  @Override public AutoBuffer write_impl( AutoBuffer ab ) { throw H2O.fail(); }
  @Override public IcedArrayList<T> read_impl( AutoBuffer ab ) { throw H2O.fail(); }
  @Override public AutoBuffer writeJSON_impl(AutoBuffer ab) { return ab; }
  @Override public IcedArrayList<T> readJSON_impl(AutoBuffer ab) {throw H2O.fail(); }

  @Override public AutoBuffer writeJSON(AutoBuffer ab) { throw H2O.fail(); }
  @Override public T readJSON(AutoBuffer ab) { throw H2O.fail(); }

  private static int _frozen$type;
  @Override public int frozenType() {
    return _frozen$type == 0 ? (_frozen$type=water.TypeMap.onIce(IcedArrayList.class.getName())) : _frozen$type;
  }
}
