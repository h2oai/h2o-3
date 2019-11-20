package water.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class EnumValuesProvider<E extends Enum<E>> implements ValuesProvider {

  private String[] _values;

  public EnumValuesProvider(Class<E> clazz) {
    this(clazz, e -> true);
  }

  public EnumValuesProvider(Class<E> clazz, Predicate<E> filter) {
    _values = getValuesOf(clazz, filter);
  }

  public EnumValuesProvider(Class<E> clazz, E[] excluded) {
    final List<E> exclusions = Arrays.asList(excluded);
    _values = getValuesOf(clazz, e -> !exclusions.contains(e));
  }

  @Override
  public String[] values() {
    return _values;
  }

  private String[] getValuesOf(Class<E> clazz, Predicate<E> filter) {
    E[] values = clazz.getEnumConstants();
    List<String> names = new ArrayList<>(values.length);
    for (E val : values) {
      if (filter.test(val)) {
        names.add(val.name());
      }
    }
    return names.toArray(new String[0]);
  }
}

