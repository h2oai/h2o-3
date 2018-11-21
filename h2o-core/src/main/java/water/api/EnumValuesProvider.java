package water.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EnumValuesProvider<E extends Enum<E>> implements ValuesProvider {

  private static final Enum<?>[] EMPTY = {};
  private String[] _values;

  public EnumValuesProvider(Class<E> clazz) {
    this(clazz, (E[])EMPTY);
  }

  public EnumValuesProvider(Class<E> clazz, E[] excluded) {
    _values = getValuesOf(clazz, Arrays.asList(excluded));
  }

  @Override
  public String[] values() {
    return _values;
  }

  private String[] getValuesOf(Class<E> clazz, List<E> excluded) {
    E[] values = clazz.getEnumConstants();
    List<String> names = new ArrayList<>(values.length);
    for (E val : values) {
      if (!excluded.contains(val)) {
        names.add(val.name());
      }
    }
    return names.toArray(new String[0]);
  }
}

