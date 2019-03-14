package water.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EnumValuesProvider<E extends Enum<E>> implements ValuesProvider {

  private String[] _values;

  public EnumValuesProvider(Class<E> clazz) {
    this(clazz, null);
  }

  public EnumValuesProvider(Class<E> clazz, E[] excluded) {
    _values = getValuesOf(clazz, excluded == null ? Collections.<E>emptyList() : Arrays.asList(excluded));
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

