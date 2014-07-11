package water.util;

import water.H2O;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Bean utilities which cover cases similar to but not the same as Aapche Commons BeanUtils.
 */
public class BeanUtils {
  public enum FieldNaming {
    CONSISTENT,
    DEST_HAS_UNDERSCORES,
    ORIGIN_HAS_UNDERSCORES
  }

  /**
   * Copy properties "of the same name" from one POJO to the other.  If the fields are
   * named consistently (both sides have fields named "_foo" and/or "bar") this acts like
   * Apache Commons BeanUtils.copyProperties(). If one side has leading underscores and
   * the other does not then the names are conformed according to the field_naming
   * parameter.
   *
   * @param dest Destination POJO
   * @param origin Origin POJO
   * @param field_naming Are the fields named consistently, or does one side have underscores?
   */
  public static void copyProperties(Object dest, Object origin, FieldNaming field_naming) {
    if (null == dest || null == origin) return;

    Map<String, Field> dest_fields = new HashMap<>();
    Map<String, Field> origin_fields = new HashMap<>();

    for (Field f : dest.getClass().getFields())
      dest_fields.put(f.getName(), f);

    for (Field f : origin.getClass().getFields())
      origin_fields.put(f.getName(), f);

    for (Map.Entry<String, Field> entry : origin_fields.entrySet()) {
      String origin_name = entry.getKey();
      Field f = entry.getValue();
      String dest_name = null;

      if (field_naming == FieldNaming.CONSISTENT) {
        dest_name = origin_name;
      } else if (field_naming == FieldNaming.DEST_HAS_UNDERSCORES) {
        dest_name = "_" + origin_name;
      } else if (field_naming == FieldNaming.ORIGIN_HAS_UNDERSCORES) {
        dest_name = origin_name.substring(1);
      }

      try {
        if (dest_fields.containsKey(dest_name)) {
          dest_fields.get(dest_name).set(dest, f.get(origin));
        }
      }
      catch (IllegalAccessException e) {
        Log.err("Illegal access exception trying to copy field: " + origin_name + " of class: " + origin.getClass() + " to field: " + dest_name + " of class: " + dest.getClass());
      }
    }
  }
}
