package water.api;

import java.util.ArrayList;
import java.util.List;

public class EnumValuesProvider<E extends Enum<E>> implements ValuesProvider {

  private String[] _values;

  public EnumValuesProvider(Class<E> clazz) {
    _values = getValuesOf(clazz);
  }

  @Override
  public String[] values() {
    return _values;
  }

  private String[] getValuesOf(Class<E> clazz) {
    E[] values = clazz.getEnumConstants();
    List<String> names = new ArrayList<>(values.length);
    for (E val : values) {
      names.add(val.name());
    }
    return names.toArray(new String[0]);
  }
}

