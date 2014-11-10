package water.util;

import water.*;
import water.api.Schema;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

/**
 * POJO utilities which cover cases similar to but not the same as Aapche Commons PojoUtils.
 */
public class PojoUtils {
  public enum FieldNaming {
    CONSISTENT,
    DEST_HAS_UNDERSCORES,
    ORIGIN_HAS_UNDERSCORES
  }

  /**
   * Copy properties "of the same name" from one POJO to the other.  If the fields are
   * named consistently (both sides have fields named "_foo" and/or "bar") this acts like
   * Apache Commons PojoUtils.copyProperties(). If one side has leading underscores and
   * the other does not then the names are conformed according to the field_naming
   * parameter.
   *
   * It is also able to map fields between external types like Schema to their corresponding
   * internal types.
   *
   * @param dest Destination POJO
   * @param origin Origin POJO
   * @param field_naming Are the fields named consistently, or does one side have underscores?
   */
  public static void copyProperties(Object dest, Object origin, FieldNaming field_naming) {
    copyProperties(dest, origin, field_naming, null);
  }

  /**
   * Copy properties "of the same name" from one POJO to the other.  If the fields are
   * named consistently (both sides have fields named "_foo" and/or "bar") this acts like
   * Apache Commons PojoUtils.copyProperties(). If one side has leading underscores and
   * the other does not then the names are conformed according to the field_naming
   * parameter.
   *
   * @param dest Destination POJO
   * @param origin Origin POJO
   * @param field_naming Are the fields named consistently, or does one side have underscores?
   * @param skip_fields Array of origin or destination field names to skip
   */
  // TODO: support automagic key-name <-> Model / Frame / Vec translation.
  public static void copyProperties(Object dest, Object origin, FieldNaming field_naming, String[] skip_fields) {
    if (null == dest || null == origin) return;

    Field[] dest_fields = Weaver.getWovenFields(dest  .getClass());
    Field[] orig_fields = Weaver.getWovenFields(origin.getClass());

    for (Field f : orig_fields) {
      String origin_name = f.getName();

      if (skip_fields != null & ArrayUtils.contains(skip_fields, origin_name))
        continue;

      String dest_name = null;
      if (field_naming == FieldNaming.CONSISTENT) {
        dest_name = origin_name;
      } else if (field_naming == FieldNaming.DEST_HAS_UNDERSCORES) {
        dest_name = "_" + origin_name;
      } else if (field_naming == FieldNaming.ORIGIN_HAS_UNDERSCORES) {
        dest_name = origin_name.substring(1);
      }

      if ( skip_fields != null & ArrayUtils.contains(skip_fields, dest_name) )
        continue;

      try {
        Field dest_field = null;
        for( Field fd : dest_fields ) {
          if (fd.getName().equals(dest_name)) {
            dest_field = fd;
            break;
          }
        }

        if( dest_field != null ) {
          dest_field.setAccessible(true);
          f.setAccessible(true);
          if (null == f.get(origin)) {
            //
            // Assigning null to dest.
            //
            dest_field.set(dest, null);
          } else if (dest_field.getType().isArray() && f.getType().isArray() && (dest_field.getType().getComponentType() != f.getType().getComponentType())) {
            //
            // Assigning an array to another array.
            //
            // You can't use reflection to set an int[] with an Integer[].  Argh.
            // TODO: other types of arrays. . .
            if (dest_field.getType().getComponentType() == int.class && f.getType().getComponentType() == Integer.class) {
              //
              // Assigning an Integer[] to an int[]
              //
              int[] copy = (int[]) f.get(origin);
              dest_field.set(dest, copy);
            } else if (dest_field.getType().getComponentType() == Integer.class && f.getType().getComponentType() == int.class) {
              //
              // Assigning an int[] to an Integer[]
              //
              Integer[] copy = (Integer[]) f.get(origin);
              dest_field.set(dest, copy);
            } else if (Schema.class.isAssignableFrom(dest_field.getType().getComponentType()) && ((Schema)dest_field.get(dest)).getImplClass().isAssignableFrom(f.getType().getComponentType())) {
              //
              // Assigning an array of impl fields to an array of schema fields, e.g. a DeepLearningParameters[] into a DeepLearningParametersV2[]
              //
              Class dest_component_class = dest_field.getType().getComponentType();
              Schema[] translation = (Schema[]) Array.newInstance(dest_component_class, Array.getLength(f.get(origin)));
              int i = 0;
              for (Iced impl : ((Iced[])f.get(origin))) {
                translation[i] = ((Schema)dest_field.getType().newInstance()).fillFromImpl(impl);
              }
              dest_field.set(dest, translation);
            } else if (Schema.class.isAssignableFrom(f.getType().getComponentType()) && Iced.class.isAssignableFrom(dest_field.getType().getComponentType())) {
              //
              // Assigning an array of schema fields to an array of impl fields, e.g. a DeepLearningParametersV2[] into a DeepLearningParameters[]
              //
              // We can't check against the actual impl class I, because we can't instantiate the schema base classes to get the impl class from an instance:
              // dest_field.getType().getComponentType().isAssignableFrom(((Schema)f.getType().getComponentType().newInstance()).getImplClass())) {
              Class dest_component_class = dest_field.getType().getComponentType();
              Iced[] translation = (Iced[]) Array.newInstance(dest_component_class, Array.getLength(f.get(origin)));
              int i = 0;
              for (Schema s : ((Schema[])f.get(origin))) {
                translation[i] = s.createImpl();
              }
              dest_field.set(dest, translation);
            } else {
              throw H2O.fail("Don't know how to cast an array of: " + f.getType().getComponentType() + " to an array of: " + dest_field.getType().getComponentType());
            }
            // end of array handling
          } else if (dest_field.getType() == Key.class && Keyed.class.isAssignableFrom(f.getType())) {
            //
            // Assigning a Keyed (e.g., a Frame or Model) to a Key.
            //
            dest_field.set(dest, ((Keyed) f.get(origin))._key);
          } else if (f.getType() == Key.class && Keyed.class.isAssignableFrom(dest_field.getType())) {
            //
            // Assigning a Key (for e.g., a Frame or Model) to a Keyed (e.g., a Frame or Model).
            //
            Value v = DKV.get((Key) f.get(origin));
            dest_field.set(dest, (null == v ? null : v.get()));
          } else if (dest_field.getType() == Pattern.class && String.class.isAssignableFrom(f.getType())) {
            //
            // Assigning a String to a Pattern.
            //
            dest_field.set(dest, Pattern.compile((String)f.get(origin)));
          } else if (f.getType() == Pattern.class && String.class.isAssignableFrom(dest_field.getType())) {
            //
            // We are assigning a Pattern to a String.
            //
            dest_field.set(dest, f.get(origin).toString());
          } else if (Enum.class.isAssignableFrom(dest_field.getType()) && String.class.isAssignableFrom(f.getType())) {
            //
            // Assigning a String into an enum field.
            //
            Class<Enum> dest_class = (Class<Enum>)dest_field.getType();
            dest_field.set(dest, Enum.valueOf(dest_class, (String) f.get(origin)));
          } else if (Enum.class.isAssignableFrom(f.getType()) && String.class.isAssignableFrom(dest_field.getType())) {
            //
            // Assigning an enum field into a String.
            //
            dest_field.set(dest, f.get(origin).toString());
          } else if (Schema.class.isAssignableFrom(dest_field.getType()) && ((Schema)dest_field.get(dest)).getImplClass().isAssignableFrom(f.getType())) {
            //
            // Assigning an impl field into a schema field, e.g. a DeepLearningParameters into a DeepLearningParametersV2.
            //
            dest_field.set(dest, ((Schema) dest_field.getType().newInstance()).fillFromImpl((Iced) f.get(origin)));
          } else if (Schema.class.isAssignableFrom(f.getType()) && ((Schema)f.get(origin)).getImplClass().isAssignableFrom(dest_field.getType())) {
            //
            // Assigning a schema field into an impl field, e.g. a DeepLearningParametersV2 into a DeepLearningParameters.
            //
            dest_field.set(dest, ((Schema)f.get(origin)).createImpl());
          } else {
            //
            // Normal case: not doing any type conversion.
            //
            dest_field.set(dest, f.get(origin));
          }
        }
      }
      catch (IllegalAccessException e) {
        Log.err("Illegal access exception trying to copy field: " + origin_name + " of class: " + origin.getClass() + " to field: " + dest_name + " of class: " + dest.getClass());
      }
      catch (InstantiationException e) {
        Log.err("Instantiation exception trying to copy field: " + origin_name + " of class: " + origin.getClass() + " to field: " + dest_name + " of class: " + dest.getClass());
      }
    }
  }
}
